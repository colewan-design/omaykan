<?php

namespace App\Http\Controllers\Api;

use App\Http\Controllers\Controller;
use App\Models\Category;
use App\Models\InventoryLevel;
use App\Models\Product;
use App\Models\ProductStoreOverride;
use App\Models\Store;
use App\Services\DeliveryQuoter;
use Illuminate\Database\Eloquent\Collection as EloquentCollection;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;

/**
 * The public catalog a storefront renders.
 *
 * There was no api/*.ts handler for this: the storefront read Firestore
 * directly (apps/web/src/storefront/catalog.ts), which is exactly why the
 * client needed Firestore credentials at all. Serving it here is what lets the
 * storefront drop the Firebase SDK.
 *
 * Two modes:
 *
 *   - no coordinates: exactly what it always did — the one store named by
 *     orgSlug + storeCode;
 *   - with lat/lng: the shopper has set a delivery address, so the answer is
 *     "what can actually reach you" — the union of the catalogs of every
 *     branch of that organization within delivery range, nearest branch first.
 *
 * Deliberately org-scoped in both modes. Widening it across organizations
 * would turn the storefront into a merchant directory, which documentation/
 * positioning.md rules out on purpose.
 */
class StorefrontCatalogController extends Controller
{
    public function __construct(private readonly DeliveryQuoter $quoter)
    {
    }

    public function show(Request $request): JsonResponse
    {
        $validated = $request->validate([
            'orgSlug' => ['required', 'string'],
            'storeCode' => ['required', 'string'],
            // Both or neither: half a pin is not a location, and silently
            // ignoring the half that arrived would quote the wrong area.
            'lat' => ['nullable', 'numeric', 'between:-90,90', 'required_with:lng'],
            'lng' => ['nullable', 'numeric', 'between:-180,180', 'required_with:lat'],
        ]);

        $home = Store::query()
            ->with('organization')
            ->where('code', $validated['storeCode'])
            ->whereHas('organization', fn ($query) => $query->where('slug', $validated['orgSlug']))
            ->first();

        abort_if($home === null || $home->status !== 'active', 404, 'Store not found.');

        $businessMode = $home->business_mode;

        // A store with no mode set cannot decide which products belong in a
        // cart. Empty beats guessing.
        if ($businessMode === null) {
            return response()->json([
                'categories' => [],
                'products' => [],
                'delivery' => $this->deliveryBlock(false, true, [], null),
            ]);
        }

        $dropLat = isset($validated['lat']) ? (float) $validated['lat'] : null;
        $dropLng = isset($validated['lng']) ? (float) $validated['lng'] : null;
        $hasDrop = $dropLat !== null && $dropLng !== null;

        if (! $hasDrop) {
            $serving = [['store' => $home, 'distanceKm' => null, 'feeCents' => null]];

            return response()->json(
                $this->catalogFor($home, $serving)
                    + ['delivery' => $this->deliveryBlock(false, true, $serving, null)],
            );
        }

        [$serving, $nearestOutOfRange] = $this->branchesReaching($home, $dropLat, $dropLng);

        if ($serving === []) {
            // Nothing can be delivered here. An empty list is the honest
            // answer — see the client, which must not mistake it for a failed
            // request and fall back to the demo catalog.
            return response()->json([
                'categories' => [],
                'products' => [],
                'delivery' => $this->deliveryBlock(true, false, [], $nearestOutOfRange),
            ]);
        }

        return response()->json(
            $this->catalogFor($home, $serving)
                + ['delivery' => $this->deliveryBlock(true, true, $serving, null)],
        );
    }

    /**
     * Every branch of the organization that can deliver to the drop pin,
     * nearest first.
     *
     * A branch with no pin of its own is kept rather than dropped: the store
     * never told us where it is, so it cannot be proved out of range, and
     * DeliveryQuoter already charges those the flat base fee rather than
     * refusing the order. Hiding their shelves would be a stricter rule than
     * the one checkout enforces.
     *
     * @return array{0: list<array{store: Store, distanceKm: float|null, feeCents: int|null}>, 1: array{store: Store, distanceKm: float}|null}
     */
    private function branchesReaching(Store $home, float $dropLat, float $dropLng): array
    {
        $branches = Store::query()
            ->where('organization_id', $home->organization_id)
            ->where('status', 'active')
            ->where('business_mode', $home->business_mode)
            ->orderBy('name')
            ->get();

        $serving = [];
        $nearestOutOfRange = null;

        foreach ($branches as $branch) {
            if (! $branch->hasPin()) {
                $serving[] = ['store' => $branch, 'distanceKm' => null, 'feeCents' => null];

                continue;
            }

            $distanceKm = round(
                $this->quoter->haversineKm($branch->lat, $branch->lng, $dropLat, $dropLng),
                2,
            );

            if ($distanceKm > DeliveryQuoter::MAX_KM) {
                if ($nearestOutOfRange === null || $distanceKm < $nearestOutOfRange['distanceKm']) {
                    $nearestOutOfRange = ['store' => $branch, 'distanceKm' => $distanceKm];
                }

                continue;
            }

            $serving[] = [
                'store' => $branch,
                'distanceKm' => $distanceKm,
                'feeCents' => $this->quoter->feeForKm($distanceKm),
            ];
        }

        // Nearest first, and the pinned branches ahead of the ones that never
        // said where they are: whichever branch is listed first is the one
        // whose price and stock a product is shown with.
        usort($serving, fn ($a, $b) => ($a['distanceKm'] ?? PHP_FLOAT_MAX) <=> ($b['distanceKm'] ?? PHP_FLOAT_MAX));

        return [$serving, $nearestOutOfRange];
    }

    /**
     * The union of what the serving branches can sell, with the first branch
     * that stocks a product deciding its price and stock figure.
     *
     * @param  list<array{store: Store, distanceKm: float|null, feeCents: int|null}>  $serving
     * @return array{categories: \Illuminate\Support\Collection<int, array<string, mixed>>, products: list<array<string, mixed>>}
     */
    private function catalogFor(Store $home, array $serving): array
    {
        $products = Product::query()
            ->where('organization_id', $home->organization_id)
            ->where('is_active', true)
            ->whereJsonContains('business_modes', $home->business_mode)
            ->orderBy('name')
            ->get();

        $storeIds = array_map(fn ($entry) => $entry['store']->id, $serving);
        $productIds = $products->pluck('id');

        // One query each rather than one per branch: keyed by store, then by
        // product, so the per-branch pass below is pure array lookups.
        $overrides = ProductStoreOverride::query()
            ->whereIn('store_id', $storeIds)
            ->whereIn('product_id', $productIds)
            ->get()
            ->groupBy('store_id')
            ->map(fn (EloquentCollection $rows) => $rows->keyBy('product_id'));

        $stock = InventoryLevel::query()
            ->whereIn('store_id', $storeIds)
            ->whereIn('product_id', $productIds)
            ->get()
            ->groupBy('store_id')
            ->map(fn (EloquentCollection $rows) => $rows->pluck('qty_on_hand', 'product_id'));

        $visible = [];

        foreach ($products as $product) {
            foreach ($serving as $entry) {
                $store = $entry['store'];
                $override = $overrides->get($store->id)?->get($product->id);

                // is_available is nullable — null means "no opinion, inherit".
                if ($override?->is_available === false) {
                    continue;
                }

                $stockQty = $product->track_inventory
                    ? (float) ($stock->get($store->id)?->get($product->id) ?? 0)
                    : null;

                // Mirrors mapProduct() in catalog.ts: there is no stored
                // "outOfStock" flag, it is derived.
                if ($product->track_inventory && $stockQty <= 0) {
                    continue;
                }

                $visible[] = [
                    'id' => $product->id,
                    'categoryId' => $product->category_id ?? 'uncategorized',
                    'sku' => $product->sku ?? '',
                    'barcode' => $product->barcode ?? '',
                    'name' => $product->name,
                    'priceCents' => $override?->price_cents ?? $product->price_cents,
                    'compareAtPriceCents' => $product->compare_at_price_cents,
                    'taxRate' => (float) $product->tax_rate,
                    'kind' => $product->product_type === 'weighted' ? 'weighted' : 'standard',
                    'imageUrl' => $product->image_url,
                    'unitLabel' => $product->unit_label,
                    'businessModes' => $product->business_modes ?? [],
                    'outOfStock' => false,
                    'stockQty' => $stockQty,
                    'lowStockThreshold' => $product->low_stock_threshold,
                    // Which counter this particular item would come off, so the
                    // storefront can say "from Main Branch, 1.2 km away".
                    'storeCode' => $store->code,
                    'storeName' => $store->name,
                    // Empty string when the branch never filled its address in
                    // — the product detail says so rather than showing nothing.
                    'storeAddress' => $store->address ?? '',
                    'distanceKm' => $entry['distanceKm'],
                ];

                // The nearest branch that has it wins; the rest of the list is
                // only there to cover what this one doesn't stock.
                break;
            }
        }

        // Only categories that still have something to show — an empty category
        // renders as a dead tab. Matches what the demo catalog does.
        $usedCategoryIds = array_unique(array_column($visible, 'categoryId'));

        $categories = Category::query()
            ->where('organization_id', $home->organization_id)
            ->whereIn('id', $usedCategoryIds)
            ->orderBy('sort_order')
            ->orderBy('name')
            ->get()
            ->map(fn (Category $category) => [
                'id' => $category->id,
                'name' => $category->name,
            ])
            ->values();

        return ['categories' => $categories, 'products' => $visible];
    }

    /**
     * What the storefront needs to explain the filtering it just did.
     *
     * `requested` separates "no address set, showing everything" from "an
     * address is set and everything shown reaches it" — the two look identical
     * in the product list and read very differently in the header.
     *
     * @param  list<array{store: Store, distanceKm: float|null, feeCents: int|null}>  $serving
     * @param  array{store: Store, distanceKm: float}|null  $nearestOutOfRange
     */
    private function deliveryBlock(
        bool $requested,
        bool $serviceable,
        array $serving,
        ?array $nearestOutOfRange,
    ): array {
        return [
            'requested' => $requested,
            'serviceable' => $serviceable,
            'maxKm' => DeliveryQuoter::MAX_KM,
            // The flat fee charged when no distance can be worked out — the
            // storefront quotes it rather than keeping its own copy of the
            // number, which would be one more thing to keep in step.
            'baseFeeCents' => DeliveryQuoter::BASE_FEE_CENTS,
            'stores' => array_map(fn ($entry) => [
                'code' => $entry['store']->code,
                'name' => $entry['store']->name,
                'address' => $entry['store']->address ?? '',
                'distanceKm' => $entry['distanceKm'],
                'feeCents' => $entry['feeCents'],
            ], $serving),
            'nearest' => $nearestOutOfRange === null ? null : [
                'code' => $nearestOutOfRange['store']->code,
                'name' => $nearestOutOfRange['store']->name,
                'address' => $nearestOutOfRange['store']->address ?? '',
                'distanceKm' => $nearestOutOfRange['distanceKm'],
                'feeCents' => null,
            ],
        ];
    }
}
