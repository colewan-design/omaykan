<?php

namespace Tests\Feature;

use App\Models\Category;
use App\Models\Organization;
use App\Models\Product;
use App\Models\Store;
use App\Services\Discovery\Discovery;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Tests\TestCase;

/**
 * The town and category landing pages, /baguio and /baguio/restaurants.
 *
 * What is being pinned down is placement — which shop lands on which page —
 * since nothing is stored and every placement is read off a shop's own
 * details. The seeded demo shop has no address and no pin, so it is in no town
 * and never on these pages.
 */
class DiscoveryPagesTest extends TestCase
{
    use RefreshDatabase;

    protected function setUp(): void
    {
        parent::setUp();

        config([
            'discovery.site_url' => 'https://omaykan.test',
            'shops.root_domain' => '',
        ]);
    }

    /**
     * @param  array<int, string>  $products  one sellable product per name
     */
    private function makeShop(
        string $slug,
        string $name,
        string $mode = 'grocery',
        ?string $label = null,
        string $address = '',
        ?float $lat = null,
        ?float $lng = null,
        string $aisle = 'Shelf',
        array $products = ['Item'],
        string $orgStatus = 'active',
    ): Store {
        $organization = Organization::query()->create(['name' => $name, 'slug' => $slug, 'status' => $orgStatus]);

        $store = new Store([
            'organization_id' => $organization->id,
            'name' => $name,
            'code' => 'main',
            'timezone' => 'Asia/Manila',
            'currency_code' => 'PHP',
            'status' => 'active',
            'business_mode' => $mode,
            'business_type_label' => $label ?? ucfirst($mode),
            'address' => $address,
            'lat' => $lat,
            'lng' => $lng,
        ]);
        $store->save();

        $category = Category::query()->create(['organization_id' => $organization->id, 'name' => $aisle, 'sort_order' => 1]);

        foreach ($products as $i => $product) {
            Product::query()->create([
                'organization_id' => $organization->id,
                'category_id' => $category->id,
                'sku' => "SKU-{$slug}-{$i}",
                'barcode' => "bar-{$slug}-{$i}",
                'name' => $product,
                'product_type' => 'standard',
                'tax_rate' => 12,
                'price_cents' => 1000,
                'track_inventory' => false,
                'is_active' => true,
                'business_modes' => [$mode],
            ]);
        }

        return $store;
    }

    public function test_a_category_page_lists_the_shops_of_that_kind_in_that_town(): void
    {
        $this->makeShop('good-taste', 'Good Taste', 'restaurant', address: 'Carino St, Baguio City');
        $this->makeShop('la-tri-eatery', 'Trinidad Eatery', 'restaurant', address: 'Km 5, La Trinidad, Benguet');
        $this->makeShop('session-grocer', 'Session Grocer', 'grocery', 'Grocery store', address: 'Session Rd, Baguio City');

        $response = $this->get('/baguio/restaurants');

        $response->assertOk()
            ->assertSee('Restaurants in Baguio', false)
            ->assertSee('Good Taste')
            ->assertDontSee('Trinidad Eatery')
            ->assertDontSee('Session Grocer')
            ->assertSee('<link rel="canonical" href="https://omaykan.test/baguio/restaurants">', false)
            ->assertSee('"@type":"ItemList"', false)
            ->assertSee('href="/shop/good-taste"', false)
            ->assertSee('"url":"https://omaykan.test/shop/good-taste"', false)
            ->assertDontSee('noindex');

        $this->get('/la-trinidad/restaurants')->assertOk()->assertSee('Trinidad Eatery')->assertDontSee('Good Taste');
        $this->get('/baguio/groceries')->assertOk()->assertSee('Session Grocer')->assertDontSee('Good Taste');
    }

    public function test_the_last_town_an_address_names_is_the_one_it_is_in(): void
    {
        $this->makeShop('km5', 'Km Five Diner', 'restaurant', address: 'Baguio-La Trinidad Road, Km 5, La Trinidad');

        $this->get('/la-trinidad/restaurants')->assertSee('Km Five Diner');
        $this->get('/baguio/restaurants')->assertDontSee('Km Five Diner');
    }

    public function test_a_shop_with_no_town_in_its_address_is_placed_by_its_pin(): void
    {
        // A few hundred metres from the La Trinidad center, well inside its
        // radius and outside Baguio's nearer claim.
        $this->makeShop('pinned', 'Pinned Kitchen', 'restaurant', address: 'Purok 3, Betag', lat: 16.4630, lng: 120.5880);
        // Far from both.
        $this->makeShop('faraway', 'Faraway Kitchen', 'restaurant', address: '', lat: 14.5995, lng: 120.9842);

        $this->get('/la-trinidad/restaurants')->assertSee('Pinned Kitchen')->assertDontSee('Faraway Kitchen');
        $this->get('/baguio/restaurants')->assertDontSee('Pinned Kitchen')->assertDontSee('Faraway Kitchen');
    }

    public function test_a_shelf_puts_a_shop_in_a_category_only_past_its_threshold(): void
    {
        // A grocery with a real bread aisle is a place to buy baked goods…
        $this->makeShop('big-bread', 'Big Bread Grocery', 'grocery', 'Grocery store', 'Baguio City',
            aisle: 'Bakery', products: ['Pandesal', 'Ube loaf', 'Cheese roll']);
        // …one with two strawberry drinks is not a strawberry seller, and a
        // breaded chop is not bread.
        $this->makeShop('frappe', 'Frappe Corner', 'coffee-shop', 'Coffee shop', 'Baguio City',
            aisle: 'Drinks', products: ['Strawberry frappe', 'Breaded chicken', 'Breaded pork', 'Breaded fish']);
        $this->makeShop('berry-farm', 'Berry Farm Stall', 'grocery', 'Farm stall', 'La Trinidad',
            aisle: 'Fruit', products: ['Strawberries 500g', 'Strawberry jam']);

        $this->get('/baguio/baked-goods')->assertSee('Big Bread Grocery')->assertDontSee('Frappe Corner');
        $this->get('/baguio/strawberries')->assertDontSee('Frappe Corner');
        $this->get('/la-trinidad/strawberries')->assertSee('Berry Farm Stall');
    }

    public function test_business_types_match_whole_words_only(): void
    {
        $this->makeShop('steak', 'Prime Cuts', 'grocery', 'Steakhouse supplies', 'Baguio City');
        $this->makeShop('tea', 'Leaf and Cup', 'grocery', 'Tea house', 'Baguio City');

        $this->get('/baguio/cafes')->assertSee('Leaf and Cup')->assertDontSee('Prime Cuts');
    }

    public function test_hidden_shops_are_on_no_page(): void
    {
        $this->makeShop('closed-org', 'Suspended Bistro', 'restaurant', address: 'Baguio City', orgStatus: 'suspended');
        $this->makeShop('empty', 'Empty Shelf Bistro', 'restaurant', address: 'Baguio City', products: []);

        $this->get('/baguio/restaurants')->assertDontSee('Suspended Bistro')->assertDontSee('Empty Shelf Bistro');
        $this->get('/baguio')->assertDontSee('Suspended Bistro')->assertDontSee('Empty Shelf Bistro');
    }

    public function test_an_empty_page_answers_but_asks_not_to_be_indexed(): void
    {
        $this->get('/la-trinidad/baked-goods')
            ->assertOk()
            ->assertSee('<meta name="robots" content="noindex, follow">', false)
            ->assertDontSee('rel="canonical"', false)
            ->assertSee('No bakeries and baked goods in La Trinidad on Omaykan yet.');
    }

    public function test_unknown_towns_and_categories_are_not_found(): void
    {
        $this->get('/manila/restaurants')->assertNotFound();
        $this->get('/baguio/hardware')->assertNotFound();
    }

    public function test_the_town_page_links_only_categories_that_have_shops(): void
    {
        $this->makeShop('good-taste', 'Good Taste', 'restaurant', address: 'Baguio City');
        $this->makeShop('tri-grocer', 'Trinidad Grocer', 'grocery', 'Grocery store', address: 'La Trinidad');

        $this->get('/baguio')
            ->assertOk()
            ->assertSee('Shop local in Baguio')
            ->assertSee('href="/baguio/restaurants"', false)
            ->assertDontSee('href="/baguio/groceries"', false)
            ->assertSee('href="/la-trinidad"', false)
            ->assertSee('Good Taste');
    }

    public function test_the_sitemap_lists_towns_and_pages_that_have_shops(): void
    {
        $this->makeShop('good-taste', 'Good Taste', 'restaurant', address: 'Baguio City');

        $this->get('/sitemap-towns.xml')
            ->assertOk()
            ->assertHeader('Content-Type', 'application/xml; charset=UTF-8')
            ->assertSee('<loc>https://omaykan.test/baguio</loc>', false)
            ->assertSee('<loc>https://omaykan.test/baguio/restaurants</loc>', false)
            ->assertDontSee('<loc>https://omaykan.test/baguio/groceries</loc>', false)
            ->assertDontSee('<loc>https://omaykan.test/la-trinidad</loc>', false);
    }

    public function test_pages_are_cacheable_and_set_no_cookies(): void
    {
        $response = $this->get('/baguio');

        $this->assertStringContainsString('public', (string) $response->headers->get('Cache-Control'));
        $this->assertSame([], $response->headers->getCookies());
    }

    public function test_shops_link_to_their_subdomain_when_the_site_has_one(): void
    {
        config(['shops.root_domain' => 'omaykan.test']);
        $this->makeShop('good-taste', 'Good Taste', 'restaurant', address: 'Baguio City');

        $this->get('/baguio/restaurants')->assertSee('href="https://good-taste.omaykan.test"', false);
    }

    public function test_a_slug_that_cannot_be_a_host_links_to_its_path(): void
    {
        config(['shops.root_domain' => 'omaykan.test']);
        $this->makeShop('app', 'App Diner', 'restaurant', address: 'Baguio City');

        $this->get('/baguio/restaurants')->assertSee('href="/shop/app"', false);
    }

    public function test_word_matching(): void
    {
        $this->assertTrue(Discovery::mentions('Bakeshop', ['bake*']));
        $this->assertTrue(Discovery::mentions('Fresh Cakes', ['cake']));
        $this->assertTrue(Discovery::mentions('Sari-sari store', ['sari-sari']));
        $this->assertFalse(Discovery::mentions('Breaded pork chop', ['bread']));
        $this->assertFalse(Discovery::mentions('Steakhouse', ['tea']));
        $this->assertFalse(Discovery::mentions('', ['tea']));
    }
}
