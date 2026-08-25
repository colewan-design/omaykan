<?php

namespace App\Http\Controllers\Api;

use App\Http\Controllers\Controller;
use App\Models\Category;
use App\Models\InventoryLevel;
use App\Models\Product;
use App\Models\ProductStoreOverride;
use App\Models\Store;
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
    public function show(Request $request): JsonResponse
    {
        $validated = $request->validate([
            'orgSlug' => ['required', 'string'],
            'storeCode' => ['required', 'string'],
        ]);

        $store = Store::query()
            ->with('organization')
            ->where('code', $validated['storeCode'])
            ->whereHas('organization', fn ($query) => $query->where('slug', $validated['orgSlug']))
            ->first();

        abort_if($store === null || $store->status !== 'active', 404, 'Store not found.');

        $businessMode = $store->business_mode;

        // A store with no mode set cannot decide which products belong in a
        // cart. Empty beats guessing.
        if ($businessMode === null) {
            return response()->json(['categories' => [], 'products' => []]);
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
            ];
        }

        // Only categories that still have something to show — an empty category
        // renders as a dead tab. Matches what the demo catalog does.
        $usedCategoryIds = array_unique(array_column($visible, 'categoryId'));

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
            'categories' => $categories,
            'products' => $visible,
        ]);
    }
}
