<?php

namespace App\Http\Controllers\Api;

use App\Http\Controllers\Controller;
use App\Models\Category;
use App\Models\InventoryLevel;
use App\Models\OrganizationMembership;
use App\Models\Product;
use App\Models\ProductStoreOverride;
use App\Models\Store;
use App\Services\Billing\TenantAccessDenied;
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
 * Returns the same shape catalog.ts already builds, so the client port is a
 * transport swap rather than a rewrite.
 */
class StorefrontCatalogController extends Controller
{
    /** Stands in for a product the merchant never filed under a category. */
    private const UNCATEGORISED = 'uncategorized';

    public function show(Request $request): JsonResponse
    {
        $validated = $request->validate([
            'orgSlug' => ['required', 'string'],
            'storeCode' => ['required', 'string'],
        ]);

        $store = Store::query()
            ->with('organization.subscription')
            ->where('code', $validated['storeCode'])
            ->whereHas('organization', fn ($query) => $query->where('slug', $validated['orgSlug']))
            ->first();

        abort_if($store === null || $store->status !== 'active', 404, 'Store not found.');

        // The directory has always hidden a suspended shop. Since every shop
        // got its own `<slug>.omaykan.com`, hiding it from the list is not the
        // same as closing it — the address still works, and this is what it
        // loads. A shop that may not trade serves its closed state instead.
        if (! $store->organization->accessVerdict()->allowsStorefront()) {
            throw TenantAccessDenied::forStorefront();
        }

        $businessMode = $store->business_mode;
        $shop = $this->shopOf($store);

        // A store with no mode set cannot decide which products belong in a
        // cart. Empty beats guessing.
        if ($businessMode === null) {
            return response()->json(['store' => $shop, 'categories' => [], 'products' => []]);
        }

        $products = Product::query()
            ->where('organization_id', $store->organization_id)
            ->where('is_active', true)
            ->whereJsonContains('business_modes', $businessMode)
            ->orderBy('name')
            ->get();

        // Per-store price and availability overrides, keyed by product.
        $overrides = ProductStoreOverride::query()
            ->where('store_id', $store->id)
            ->whereIn('product_id', $products->pluck('id'))
            ->get()
            ->keyBy('product_id');

        $stock = InventoryLevel::query()
            ->where('store_id', $store->id)
            ->whereIn('product_id', $products->pluck('id'))
            ->pluck('qty_on_hand', 'product_id');

        $visible = [];

        foreach ($products as $product) {
            $override = $overrides->get($product->id);

            // is_available is nullable — null means "no opinion, inherit".
            if ($override?->is_available === false) {
                continue;
            }

            $stockQty = $product->track_inventory
                ? (float) ($stock[$product->id] ?? 0)
                : null;

            // Mirrors mapProduct() in catalog.ts: there is no stored
            // "outOfStock" flag, it is derived.
            if ($product->track_inventory && $stockQty <= 0) {
                continue;
            }

            $visible[] = [
                'id' => $product->id,
                'categoryId' => $product->category_id ?? self::UNCATEGORISED,
                'sku' => $product->sku ?? '',
                'barcode' => $product->barcode ?? '',
                'name' => $product->name,
                'priceCents' => $override?->price_cents ?? $product->price_cents,
                'compareAtPriceCents' => $product->compare_at_price_cents,
                // A fraction (0.12), which is what the storefront's cart and
                // product page compute with. The column is a percentage (12.00);
                // served as-is, a seeded product showed 1,200% VAT.
                'taxRate' => round(((float) $product->tax_rate) / 100, 4),
                'kind' => $product->product_type === 'weighted' ? 'weighted' : 'standard',
                'imageUrl' => $product->image_url,
                // The extra shots, after the primary one. The detail page
                // draws image_url first and these after it, in this order.
                'photoUrls' => array_values(array_filter(
                    $product->photo_urls ?? [],
                    fn ($url) => is_string($url) && trim($url) !== '',
                )),
                'brand' => $product->brand,
                'packagingType' => $product->packaging_type,
                'unitLabel' => $product->unit_label,
                'description' => $product->description,
                'businessModes' => $product->business_modes ?? [],
                'outOfStock' => false,
                'stockQty' => $stockQty,
                'lowStockThreshold' => $product->low_stock_threshold,
            ];
        }

        // Only categories that still have something to show — an empty category
        // renders as a dead tab. Matches what the demo catalog does.
        //
        // The sentinel has to come back out before this reaches the query.
        // `categories.id` is a uuid column, and PostgreSQL rejects the
        // comparison outright with 22P02 rather than simply not matching, so a
        // single uncategorised product would 500 the whole storefront — every
        // product, for every shopper. SQLite tolerated it, which is why this
        // survived local testing.
        $usedCategoryIds = array_values(array_filter(
            array_unique(array_column($visible, 'categoryId')),
            fn ($id) => $id !== self::UNCATEGORISED,
        ));

        $categories = Category::query()
            ->where('organization_id', $store->organization_id)
            ->whereIn('id', $usedCategoryIds)
            ->orderBy('sort_order')
            ->orderBy('name')
            ->get()
            ->map(fn (Category $category) => [
                'id' => $category->id,
                'name' => $category->name,
            ])
            ->values();

        return response()->json([
            'store' => $shop,
            'categories' => $categories,
            'products' => $visible,
        ]);
    }

    /**
     * Who the shopper is buying from, and where that shop is.
     *
     * Omaykan takes no commission and charges nothing online — the shopper
     * hands cash to a rider on behalf of a shop they have never seen. So the
     * shop has to be named on the product itself, not only in the footer:
     * "who am I buying from, and are they near me" is the question a price
     * on its own cannot answer.
     *
     * The owner is the organization's founding admin — the account signup
     * created (SignupController), which is the person whose name is over the
     * door. There is no separate 'owner' membership role, so oldest-admin is
     * the closest thing the schema has; staff promoted later sort after them.
     *
     * @return array<string, mixed>
     */
    private function shopOf(Store $store): array
    {
        $owner = OrganizationMembership::query()
            ->where('organization_id', $store->organization_id)
            ->where('membership_role', 'admin')
            ->with('user')
            ->oldest()
            ->first()?->user;

        $label = trim((string) $store->business_type_label);

        return [
            'name' => $store->name,
            'businessTypeLabel' => $label === '' ? null : $label,
            'ownerName' => $owner?->name,
            'address' => $store->address,
            // The photo the owner uploaded in Settings > Business image, if
            // they have. The storefront shows it behind the mark on products
            // that have no photo of their own, so a shelf of placeholders
            // still looks like this shop's shelf. Null is the ordinary case
            // and the caller falls back to the plain branded panel.
            'imageUrl' => StoreImageController::urlFor($store),
            // The shop's own pause. The menu is still served — a shopper can
            // browse and plan — and checkout reads this to say "back at 4:00
            // PM" instead of letting them fill a cart it will refuse.
            'ordering' => StoreOrderingController::stateOf($store),
        ];
    }
}
