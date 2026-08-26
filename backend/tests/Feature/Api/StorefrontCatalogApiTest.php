<?php

namespace Tests\Feature\Api;

use App\Models\Category;
use App\Models\InventoryLevel;
use App\Models\Organization;
use App\Models\Product;
use App\Models\ProductStoreOverride;
use App\Models\Store;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Tests\TestCase;

/**
 * The catalog endpoint that lets the storefront stop reading Firestore.
 */
class StorefrontCatalogApiTest extends TestCase
{
    use RefreshDatabase;

    private function get_catalog()
    {
        return $this->getJson('/api/storefront/catalog?orgSlug=demo-coffee&storeCode=main');
    }

    public function test_returns_the_stores_visible_products_and_their_categories(): void
    {
        $this->seed();

        $this->get_catalog()
            ->assertOk()
            ->assertJsonCount(1, 'products')
            ->assertJsonPath('products.0.name', 'Espresso')
            ->assertJsonPath('products.0.priceCents', 12000)
            ->assertJsonPath('products.0.outOfStock', false)
            ->assertJsonPath('products.0.stockQty', 100)
            ->assertJsonCount(1, 'categories')
            ->assertJsonPath('categories.0.name', 'Beverages');
    }

    public function test_product_outside_the_stores_business_mode_is_hidden(): void
    {
        $this->seed();

        Product::query()->where('sku', 'ESP-0001')->update(['business_modes' => json_encode(['grocery'])]);

        $this->get_catalog()
            ->assertOk()
            ->assertJsonCount(0, 'products')
            ->assertJsonCount(0, 'categories');
    }

    public function test_inactive_product_is_hidden(): void
    {
        $this->seed();

        Product::query()->where('sku', 'ESP-0001')->update(['is_active' => false]);

        $this->get_catalog()->assertOk()->assertJsonCount(0, 'products');
    }

    public function test_tracked_product_with_no_stock_is_hidden(): void
    {
        $this->seed();

        InventoryLevel::query()->update(['qty_on_hand' => 0]);

        $this->get_catalog()->assertOk()->assertJsonCount(0, 'products');
    }

    public function test_untracked_product_shows_regardless_of_stock(): void
    {
        $this->seed();

        Product::query()->where('sku', 'ESP-0001')->update(['track_inventory' => false]);
        InventoryLevel::query()->update(['qty_on_hand' => 0]);

        $this->get_catalog()
            ->assertOk()
            ->assertJsonCount(1, 'products')
            ->assertJsonPath('products.0.stockQty', null);
    }

    public function test_per_store_price_override_wins(): void
    {
        $this->seed();

        $store = Store::query()->firstOrFail();
        $product = Product::query()->where('sku', 'ESP-0001')->firstOrFail();

        ProductStoreOverride::query()->create([
            'id' => (string) str()->uuid(),
            'organization_id' => $store->organization_id,
            'store_id' => $store->id,
            'product_id' => $product->id,
            'price_cents' => 9900,
        ]);

        $this->get_catalog()
            ->assertOk()
            ->assertJsonPath('products.0.priceCents', 9900);
    }

    public function test_per_store_unavailability_hides_the_product(): void
    {
        $this->seed();

        $store = Store::query()->firstOrFail();
        $product = Product::query()->where('sku', 'ESP-0001')->firstOrFail();

        ProductStoreOverride::query()->create([
            'id' => (string) str()->uuid(),
            'organization_id' => $store->organization_id,
            'store_id' => $store->id,
            'product_id' => $product->id,
            'is_available' => false,
        ]);

        $this->get_catalog()->assertOk()->assertJsonCount(0, 'products');
    }

    public function test_empty_categories_are_not_returned(): void
    {
        $this->seed();

        $store = Store::query()->firstOrFail();
        Category::query()->create([
            'id' => (string) str()->uuid(),
            'organization_id' => $store->organization_id,
            'name' => 'Pastries',
            'sort_order' => 2,
        ]);

        // Pastries has no products, so it must not render as a dead tab.
        $this->get_catalog()
            ->assertOk()
            ->assertJsonCount(1, 'categories')
            ->assertJsonPath('categories.0.name', 'Beverages');
    }

    public function test_store_with_no_business_mode_returns_an_empty_catalog(): void
    {
        $this->seed();

        Store::query()->firstOrFail()->forceFill(['business_mode' => null])->save();

        $this->get_catalog()
            ->assertOk()
            ->assertJsonCount(0, 'products')
            ->assertJsonCount(0, 'categories');
    }

    public function test_unknown_store_is_a_404(): void
    {
        $this->seed();

        $this->getJson('/api/storefront/catalog?orgSlug=demo-coffee&storeCode=nope')
            ->assertNotFound();
    }

    public function test_org_and_store_are_required(): void
    {
        $this->seed();

        $this->getJson('/api/storefront/catalog')
            ->assertStatus(422)
            ->assertJsonValidationErrors(['orgSlug', 'storeCode']);
    }

    // -- Delivery-address filtering -----------------------------------------
    //
    // Measured against the seeded store's pin on Session Road, Baguio: a few
    // hundred metres away is in range, and 0.3 degrees of latitude is roughly
    // 33 km, comfortably past the 15 km limit.

    private const NEARBY_LAT = 16.42;

    private const NEARBY_LNG = 120.6;

    private const FAR_LAT = 16.71;

    private const FAR_LNG = 120.596;

    private function get_catalog_at(float $lat, float $lng)
    {
        return $this->getJson("/api/storefront/catalog?orgSlug=demo-coffee&storeCode=main&lat={$lat}&lng={$lng}");
    }

    /** A second branch of the same organization, stocking the same espresso. */
    private function branch(string $code, string $name, ?float $lat, ?float $lng, int $stock = 50): Store
    {
        $organization = Organization::query()->where('slug', 'demo-coffee')->firstOrFail();

        $branch = Store::query()->create([
            'organization_id' => $organization->id,
            'name' => $name,
            'code' => $code,
            'timezone' => 'Asia/Manila',
            'currency_code' => 'PHP',
            'status' => 'active',
            'business_mode' => 'coffee-shop',
            'lat' => $lat,
            'lng' => $lng,
        ]);

        InventoryLevel::query()->create([
            'id' => (string) str()->uuid(),
            'organization_id' => $organization->id,
            'store_id' => $branch->id,
            'product_id' => Product::query()->where('sku', 'ESP-0001')->value('id'),
            'qty_on_hand' => $stock,
        ]);

        return $branch;
    }

    public function test_a_catalog_asked_for_without_an_address_says_so(): void
    {
        $this->seed();

        $this->get_catalog()
            ->assertOk()
            ->assertJsonPath('delivery.requested', false)
            ->assertJsonPath('delivery.serviceable', true)
            // Quoted whether or not an address is in play: checkout shows it
            // as the "from" price before anyone has said where they are.
            ->assertJsonPath('delivery.baseFeeCents', 4900)
            ->assertJsonCount(1, 'products');
    }

    public function test_an_address_in_range_keeps_the_catalog_and_quotes_the_branch(): void
    {
        $this->seed();

        $response = $this->get_catalog_at(self::NEARBY_LAT, self::NEARBY_LNG)
            ->assertOk()
            ->assertJsonCount(1, 'products')
            ->assertJsonPath('products.0.storeCode', 'main')
            ->assertJsonPath('products.0.storeName', 'Main Branch')
            // Where to go and get it — the product detail shows this rather
            // than making the shopper guess which counter it comes off.
            ->assertJsonPath('products.0.storeAddress', '12 Session Road, Baguio City')
            ->assertJsonPath('delivery.requested', true)
            ->assertJsonPath('delivery.serviceable', true)
            ->assertJsonPath('delivery.stores.0.code', 'main')
            // Under the 2 km flag-down, so the base fee and nothing on top.
            ->assertJsonPath('delivery.stores.0.feeCents', 4900);

        $this->assertLessThan(2, $response->json('delivery.stores.0.distanceKm'));
    }

    public function test_an_address_outside_the_radius_empties_the_catalog(): void
    {
        $this->seed();

        $response = $this->get_catalog_at(self::FAR_LAT, self::FAR_LNG)
            ->assertOk()
            ->assertJsonCount(0, 'products')
            ->assertJsonCount(0, 'categories')
            ->assertJsonPath('delivery.requested', true)
            ->assertJsonPath('delivery.serviceable', false)
            ->assertJsonCount(0, 'delivery.stores')
            // Named so the storefront can say how far the nearest counter is
            // rather than just "nothing here".
            ->assertJsonPath('delivery.nearest.name', 'Main Branch');

        $this->assertGreaterThan(15, $response->json('delivery.nearest.distanceKm'));
    }

    public function test_stock_at_an_out_of_range_branch_does_not_count(): void
    {
        $this->seed();

        // The near branch is out of espresso; only the far one has any.
        InventoryLevel::query()->update(['qty_on_hand' => 0]);
        $this->branch('far', 'Far Branch', self::FAR_LAT, self::FAR_LNG);

        $this->get_catalog_at(self::NEARBY_LAT, self::NEARBY_LNG)
            ->assertOk()
            ->assertJsonCount(0, 'products')
            // The near branch still serves the address — it just has nothing.
            ->assertJsonPath('delivery.serviceable', true);
    }

    public function test_a_second_branch_in_range_fills_a_gap_in_the_first(): void
    {
        $this->seed();

        InventoryLevel::query()->update(['qty_on_hand' => 0]);
        $this->branch('hill', 'Hill Branch', 16.43, 120.61);

        $this->get_catalog_at(self::NEARBY_LAT, self::NEARBY_LNG)
            ->assertOk()
            ->assertJsonCount(1, 'products')
            ->assertJsonPath('products.0.storeCode', 'hill')
            ->assertJsonPath('products.0.stockQty', 50);
    }

    public function test_the_nearest_branch_that_stocks_it_sets_the_price(): void
    {
        $this->seed();

        $hill = $this->branch('hill', 'Hill Branch', 16.43, 120.61);
        $product = Product::query()->where('sku', 'ESP-0001')->firstOrFail();

        ProductStoreOverride::query()->create([
            'id' => (string) str()->uuid(),
            'organization_id' => $hill->organization_id,
            'store_id' => $hill->id,
            'product_id' => $product->id,
            'price_cents' => 20000,
        ]);

        // The seeded Main Branch is the closer of the two, so its price wins
        // and the hill branch's markup is never seen.
        $this->get_catalog_at(self::NEARBY_LAT, self::NEARBY_LNG)
            ->assertOk()
            ->assertJsonCount(1, 'products')
            ->assertJsonPath('products.0.storeCode', 'main')
            ->assertJsonPath('products.0.priceCents', 12000);
    }

    public function test_a_branch_with_no_pin_is_not_filtered_out(): void
    {
        $this->seed();

        // Cannot be proved out of range, and checkout would still take the
        // order at the flat base fee — so its shelves stay visible.
        Store::query()->firstOrFail()->forceFill(['lat' => null, 'lng' => null])->save();

        $this->get_catalog_at(self::FAR_LAT, self::FAR_LNG)
            ->assertOk()
            ->assertJsonCount(1, 'products')
            ->assertJsonPath('products.0.distanceKm', null)
            ->assertJsonPath('delivery.serviceable', true)
            ->assertJsonPath('delivery.stores.0.distanceKm', null)
            ->assertJsonPath('delivery.stores.0.feeCents', null);
    }

    public function test_half_a_pin_is_rejected(): void
    {
        $this->seed();

        $this->getJson('/api/storefront/catalog?orgSlug=demo-coffee&storeCode=main&lat=16.41')
            ->assertStatus(422)
            ->assertJsonValidationErrors(['lng']);
    }
}
