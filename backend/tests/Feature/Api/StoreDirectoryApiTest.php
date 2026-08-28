<?php

namespace Tests\Feature\Api;

use App\Models\Category;
use App\Models\Organization;
use App\Models\Product;
use App\Models\Store;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Tests\TestCase;

/**
 * The public shop list the landing page browses.
 *
 * The seeded fixture supplies one shop (demo-coffee, one Espresso); each test
 * adds only the shops it is about, so what a case asserts is what it built.
 */
class StoreDirectoryApiTest extends TestCase
{
    use RefreshDatabase;

    /** public_store_code is unique across every store, so codes cannot repeat. */
    private int $nextCode = 900001;

    /**
     * A shop with one sellable product, which is the minimum to be listed.
     */
    private function makeShop(
        string $slug,
        string $name,
        string $businessMode = 'grocery',
        int $products = 1,
        ?float $lat = null,
        ?float $lng = null,
        string $address = '',
        string $storeStatus = 'active',
        string $orgStatus = 'active',
        ?string $image = null,
    ): Store {
        $organization = Organization::query()->create([
            'name' => $name,
            'slug' => $slug,
            'status' => $orgStatus,
        ]);

        $store = new Store([
            'organization_id' => $organization->id,
            'name' => $name,
            'code' => 'main',
            'timezone' => 'Asia/Manila',
            'currency_code' => 'PHP',
            'status' => $storeStatus,
            'business_mode' => $businessMode,
            'business_type_label' => ucfirst($businessMode),
            'address' => $address,
            'lat' => $lat,
            'lng' => $lng,
        ]);
        $store->setPairingCode((string) $this->nextCode++);
        $store->save();

        $category = Category::query()->create([
            'organization_id' => $organization->id,
            'name' => 'Shelf',
            'sort_order' => 1,
        ]);

        for ($i = 0; $i < $products; $i++) {
            Product::query()->create([
                'organization_id' => $organization->id,
                'category_id' => $category->id,
                'sku' => "SKU-{$slug}-{$i}",
                'barcode' => "bar-{$slug}-{$i}",
                'name' => "Item {$i}",
                'product_type' => 'standard',
                'tax_rate' => 12,
                'price_cents' => 1000,
                'track_inventory' => false,
                'is_active' => true,
                'business_modes' => [$businessMode],
                // Numbered so a shop's photos differ, which is what makes
                // "always the same one back" an assertion worth making.
                'image_url' => $image === null ? null : "{$image}-{$i}.jpg",
            ]);
        }

        return $store;
    }

    public function test_lists_shops_that_can_take_an_order(): void
    {
        $this->seed();
        $this->makeShop('fresh-market', 'Fresh Market', 'grocery', 3);

        $this->getJson('/api/stores')
            ->assertOk()
            ->assertJsonCount(2, 'stores')
            ->assertJsonPath('stores.0.name', 'Fresh Market')
            ->assertJsonPath('stores.0.orgSlug', 'fresh-market')
            ->assertJsonPath('stores.0.storeCode', 'main')
            ->assertJsonPath('stores.0.productCount', 3)
            ->assertJsonPath('stores.1.name', 'Main Branch');
    }

    public function test_lists_a_photo_off_the_shops_own_shelf(): void
    {
        $this->seed();
        $this->makeShop('fresh-market', 'Fresh Market', 'grocery', 3, image: '/products/rice');

        $this->getJson('/api/stores')
            ->assertOk()
            ->assertJsonPath('stores.0.name', 'Fresh Market')
            ->assertJsonPath('stores.0.imageUrl', '/products/rice-0.jpg');
    }

    public function test_a_shop_whose_products_have_no_photos_has_a_null_image(): void
    {
        $this->seed();
        $this->makeShop('bare-shelf', 'Bare Shelf', 'grocery', 2);

        $this->getJson('/api/stores')
            ->assertOk()
            ->assertJsonPath('stores.0.name', 'Bare Shelf')
            ->assertJsonPath('stores.0.imageUrl', null);
    }

    /**
     * An empty string in the column is a photo nobody can see. min() would
     * pick it over every real URL, so the shop would come back with a broken
     * image rather than the monogram the carousel draws for "no photo".
     */
    public function test_a_blank_photo_column_does_not_win_over_a_real_one(): void
    {
        $this->seed();
        $store = $this->makeShop('corner-store', 'Corner Store', 'grocery', 1, image: '/products/tin');

        Product::query()->create([
            'organization_id' => $store->organization_id,
            'sku' => 'SKU-corner-blank',
            'barcode' => 'bar-corner-blank',
            'name' => 'Aaa unphotographed',
            'product_type' => 'standard',
            'tax_rate' => 12,
            'price_cents' => 1000,
            'track_inventory' => false,
            'is_active' => true,
            'business_modes' => ['grocery'],
            'image_url' => '',
        ]);

        $this->getJson('/api/stores')
            ->assertOk()
            ->assertJsonPath('stores.0.name', 'Corner Store')
            ->assertJsonPath('stores.0.imageUrl', '/products/tin-0.jpg');
    }

    /**
     * The same gate StoreCodeController applies when resolving a code: a salon
     * takes appointments, not carts, so listing it as somewhere to order from
     * would be an invitation to a 409.
     */
    public function test_a_mode_that_cannot_order_online_is_not_listed(): void
    {
        $this->seed();
        $this->makeShop('nail-lounge', 'Nail Lounge', 'nail-salon', 5);

        $this->getJson('/api/stores')
            ->assertOk()
            ->assertJsonCount(1, 'stores')
            ->assertJsonPath('stores.0.name', 'Main Branch');
    }

    public function test_a_shop_with_an_empty_shelf_is_not_listed(): void
    {
        $this->seed();
        $this->makeShop('empty-store', 'Empty Store', 'grocery', 0);

        $this->getJson('/api/stores')
            ->assertOk()
            ->assertJsonCount(1, 'stores')
            ->assertJsonPath('stores.0.name', 'Main Branch');
    }

    public function test_products_filed_under_another_business_mode_do_not_count(): void
    {
        $this->seed();
        // A grocery storefront whose only product is flagged restaurant-only
        // has nothing it can actually sell.
        $store = $this->makeShop('mismatched', 'Mismatched', 'grocery', 1);
        Product::query()
            ->where('organization_id', $store->organization_id)
            ->update(['business_modes' => json_encode(['restaurant'])]);

        $this->getJson('/api/stores')
            ->assertOk()
            ->assertJsonCount(1, 'stores')
            ->assertJsonPath('stores.0.name', 'Main Branch');
    }

    public function test_archived_stores_and_suspended_organizations_are_hidden(): void
    {
        $this->seed();
        $this->makeShop('closed-shop', 'Closed Shop', 'grocery', 2, storeStatus: 'archived');
        $this->makeShop('gone-org', 'Gone Org', 'grocery', 2, orgStatus: 'suspended');

        $this->getJson('/api/stores')
            ->assertOk()
            ->assertJsonCount(1, 'stores')
            ->assertJsonPath('stores.0.name', 'Main Branch');
    }

    public function test_search_matches_name_and_address(): void
    {
        $this->seed();
        $this->makeShop('magsaysay-mart', 'Magsaysay Mart', 'grocery', 1, address: 'Magsaysay Avenue');

        $this->getJson('/api/stores?q=magsaysay')
            ->assertOk()
            ->assertJsonCount(1, 'stores')
            ->assertJsonPath('stores.0.name', 'Magsaysay Mart');

        // Matches the seeded shop's address, not its name.
        $this->getJson('/api/stores?q=session')
            ->assertOk()
            ->assertJsonCount(1, 'stores')
            ->assertJsonPath('stores.0.name', 'Main Branch');

        $this->getJson('/api/stores?q=nothing-like-this')
            ->assertOk()
            ->assertJsonCount(0, 'stores');
    }

    /** A wildcard typed into the box is a character to find, not a pattern. */
    public function test_search_treats_wildcards_literally(): void
    {
        $this->seed();
        $this->makeShop('percent-store', '100% Fresh', 'grocery', 1);

        $this->getJson('/api/stores?q=%25')
            ->assertOk()
            ->assertJsonCount(1, 'stores')
            ->assertJsonPath('stores.0.name', '100% Fresh');
    }

    public function test_orders_by_distance_when_the_customer_is_placed(): void
    {
        $this->seed();
        // Seeded shop sits at 16.4123/120.5960. "Alpha" sorts first by name
        // but is the furthest away, so a distance sort has to reorder it.
        $this->makeShop('alpha-far', 'Alpha Far', 'grocery', 1, lat: 16.6000, lng: 120.8000);
        $this->makeShop('zulu-near', 'Zulu Near', 'grocery', 1, lat: 16.4124, lng: 120.5961);

        // Queried from Zulu Near's own pin, so it is the zero — the seeded
        // shop is metres away and Alpha Far is tens of kilometres.
        $response = $this->getJson('/api/stores?lat=16.4124&lng=120.5961')->assertOk();

        $names = array_column($response->json('stores'), 'name');
        $this->assertSame(['Zulu Near', 'Main Branch', 'Alpha Far'], $names);

        $this->assertEqualsWithDelta(0.0, $response->json('stores.0.distanceKm'), 0.05);
    }

    public function test_shops_without_a_pin_are_listed_after_the_ones_with_one(): void
    {
        $this->seed();
        $this->makeShop('no-pin', 'Aaa No Pin', 'grocery', 1);

        $response = $this->getJson('/api/stores?lat=16.4123&lng=120.5960')->assertOk();

        $names = array_column($response->json('stores'), 'name');
        $this->assertSame(['Main Branch', 'Aaa No Pin'], $names);
        $this->assertNull($response->json('stores.1.distanceKm'));
    }

    public function test_distance_is_null_without_coordinates(): void
    {
        $this->seed();

        $this->getJson('/api/stores')
            ->assertOk()
            ->assertJsonPath('stores.0.distanceKm', null);
    }

    /** Half a coordinate cannot place anyone, and must not look like it did. */
    public function test_half_a_coordinate_pair_is_rejected(): void
    {
        $this->seed();

        $this->getJson('/api/stores?lat=16.4')->assertStatus(422)->assertJsonValidationErrors('lng');
        $this->getJson('/api/stores?lng=120.5')->assertStatus(422)->assertJsonValidationErrors('lat');
        $this->getJson('/api/stores?lat=999&lng=1')->assertStatus(422)->assertJsonValidationErrors('lat');
    }

    /**
     * The directory is public. The pairing code is the secret a till proves to
     * pair itself, and nothing about listing a shop should hand it out.
     */
    public function test_never_exposes_the_pairing_secret(): void
    {
        $this->seed();

        $response = $this->getJson('/api/stores')->assertOk();

        $response->assertJsonMissing(['pairingCode' => '123456']);
        $this->assertStringNotContainsString('pairing_code_hash', $response->getContent());
        $this->assertStringNotContainsString('123456', $response->getContent());
    }
}
