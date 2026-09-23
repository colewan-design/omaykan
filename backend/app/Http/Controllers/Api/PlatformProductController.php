<?php

namespace App\Http\Controllers\Api;

use App\Http\Controllers\Controller;
use App\Models\Category;
use App\Models\Product;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\DB;

/**
 * Everything for sale on the platform, across every shop.
 *
 * The catalog endpoint the storefront uses answers "what can I buy from this
 * one shop"; this answers "what is on the platform at all", which is the
 * question behind moderation, duplicate listings, and a shop that has uploaded
 * 400 products with no photographs.
 *
 * Read-only, for the reason PlatformOrderController gives: a product belongs to
 * the shop that listed it, and an operator editing one from here would change
 * a merchant's shelf without the merchant knowing. The row names its shop so
 * the conversation can start in the right place.
 *
 * Behind `auth:platform` with the rest of the operator tools; see routes/api.php.
 */
class PlatformProductController extends Controller
{
    /** One screen of products. Larger than the order list — rows are thinner. */
    private const PER_PAGE = 30;

    public function index(Request $request): JsonResponse
    {
        $validated = $request->validate([
            'q' => ['nullable', 'string', 'max:120'],
            'categoryId' => ['nullable', 'string', 'max:64'],
            'page' => ['nullable', 'integer', 'min:1'],
        ]);

        $search = trim((string) ($validated['q'] ?? ''));

        $query = Product::query()
            ->with(['category:id,name', 'organization:id,name'])
            // Stock is spread across a row per store, and the operator wants
            // one number. Summed in SQL rather than by loading the levels,
            // so a 500-product page stays a fixed number of queries.
            ->addSelect([
                'stock_on_hand' => DB::table('inventory_levels')
                    ->selectRaw('coalesce(sum(qty_on_hand), 0)')
                    ->whereColumn('inventory_levels.product_id', 'products.id')
                    ->whereNull('inventory_levels.deleted_at'),
            ]);

        if (($validated['categoryId'] ?? '') !== '') {
            $query->where('category_id', $validated['categoryId']);
        }

        if ($search !== '') {
            $query->where(function ($builder) use ($search) {
                $like = '%'.addcslashes($search, '%_\\').'%';

                $builder->where('name', 'like', $like)
                    ->orWhere('sku', 'like', $like)
                    ->orWhere('barcode', 'like', $like);
            });
        }

        $page = $query->orderBy('name')->paginate(self::PER_PAGE, ['*'], 'page', $validated['page'] ?? 1);

        return response()->json([
            'products' => collect($page->items())->map(fn (Product $product) => [
                'id' => $product->id,
                'name' => $product->name,
                'sku' => $product->sku,
                'categoryName' => $product->category?->name,
                'sellerName' => $product->organization?->name,
                'priceCents' => (int) $product->price_cents,
                'compareAtPriceCents' => $product->compare_at_price_cents !== null
                    ? (int) $product->compare_at_price_cents
                    : null,
                'imageUrl' => $product->image_url,
                'unitLabel' => $product->unit_label,
                // Null, not 0, when the shop does not count this product —
                // "untracked" and "sold out" are different facts, and showing
                // an untracked product as 0 in stock starts a wrong
                // conversation with a merchant.
                'stockOnHand' => $product->track_inventory ? (float) $product->stock_on_hand : null,
                'trackInventory' => (bool) $product->track_inventory,
                'lowStockThreshold' => $product->low_stock_threshold !== null
                    ? (float) $product->low_stock_threshold
                    : null,
                'isActive' => (bool) $product->is_active,
            ])->values(),
            // The filter bar's tabs. Counted here rather than in the client so
            // a tab's number always matches what opening it will show.
            'categories' => Category::query()
                ->withCount('products')
                ->orderBy('sort_order')
                ->orderBy('name')
                ->get()
                ->map(fn (Category $category) => [
                    'id' => $category->id,
                    'name' => $category->name,
                    'products' => (int) $category->products_count,
                ])
                ->values(),
            'pagination' => [
                'page' => $page->currentPage(),
                'perPage' => $page->perPage(),
                'total' => $page->total(),
                'lastPage' => $page->lastPage(),
            ],
        ]);
    }
}
