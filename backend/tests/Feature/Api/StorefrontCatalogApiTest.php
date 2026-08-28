<?php

namespace Tests\Feature\Api;

use App\Models\Category;
use App\Models\InventoryLevel;
use App\Models\OrganizationMembership;
use App\Models\Product;
use App\Models\ProductStoreOverride;
use App\Models\Store;
use App\Models\User;
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

    public function test_returns_the_shop_behind_the_shelf(): void
    {
        $this->seed();

        $this->get_catalog()
            ->assertOk()
            ->assertJsonPath('store.name', 'Main Branch')
            ->assertJsonPath('store.ownerName', 'Admin User')
            ->assertJsonPath('store.address', '12 Session Road, Baguio City');
    }

    public function test_shop_owner_is_the_founding_admin_not_one_promoted_later(): void
    {
        $this->seed();

        $store = Store::query()->firstOrFail();

        $later = User::query()->create([
            'name' => 'Newer Admin',
            'username' => 'newer',
            'email' => 'newer@example.com',
            'password' => bcrypt('password'),
            'status' => 'active',
        ]);

        // created_at is not fillable, so it is forced after the fact — without
        // it the two admin rows share a timestamp and "oldest" is a coin toss.
        OrganizationMembership::query()->create([
            'organization_id' => $store->organization_id,
            'user_id' => $later->id,
            'membership_role' => 'admin',
        ])->forceFill(['created_at' => now()->addDay()])->save();

        $this->get_catalog()->assertOk()->assertJsonPath('store.ownerName', 'Admin User');
    }

    public function test_shop_is_still_named_when_the_store_has_no_business_mode(): void
    {
        $this->seed();

        Store::query()->firstOrFail()->forceFill(['business_mode' => null])->save();

        $this->get_catalog()
            ->assertOk()
            ->assertJsonPath('store.name', 'Main Branch')
            ->assertJsonPath('store.address', '12 Session Road, Baguio City');
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
}
