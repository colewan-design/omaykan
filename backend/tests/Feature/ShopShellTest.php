<?php

namespace Tests\Feature;

use App\Http\Controllers\Api\StoreImageController;
use App\Models\Organization;
use App\Models\Product;
use App\Models\Store;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Illuminate\Support\Facades\Storage;
use Tests\TestCase;

/**
 * Link previews for a shop's own address, `<slug>.omaykan.com`.
 *
 * Every assertion here is about bytes, because a crawler is all this is for:
 * facebookexternalhit and the rest read the file and run nothing, so a tag that
 * only exists after Vue mounts does not exist. What matters is that the shop's
 * name is *in the response*, and that the shell still goes out whole when there
 * is no shop to name.
 */
class ShopShellTest extends TestCase
{
    use RefreshDatabase;

    /** A real 1×1 PNG, the same one ProductImageApiTest uses. */
    private const PNG = 'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNkYAAAAAYAAjCB0C8AAAAASUVORK5CYII=';

    /**
     * Stands in for the built `shop.html`: a head with a title to replace, its
     * own generic description written over three lines exactly as the real file
     * writes it, and a body whose script tag has to survive intact — it names
     * the bundle, and a page that loses it does not start.
     */
    private const SHELL = <<<'HTML'
        <!doctype html>
        <html lang="en">
          <head>
            <meta charset="UTF-8" />
            <meta name="viewport" content="width=device-width, initial-scale=1.0" />
            <title>Shop — Omaykan</title>
            <meta
              name="description"
              content="Order from a local shop on Omaykan: browse the menu, fill a basket, and pay in cash or GCash when it arrives."
            />
          </head>
          <body>
            <div id="shop-app"></div>
            <script type="module" src="/assets/shop-abc123.js"></script>
          </body>
        </html>
        HTML;

    protected function setUp(): void
    {
        parent::setUp();

        Storage::fake('local');

        $root = storage_path('framework/testing/web-root');
        is_dir($root) || mkdir($root, 0o777, true);
        file_put_contents($root.'/shop.html', self::SHELL);

        config([
            'shops.root_domain' => 'omaykan.com',
            'shops.web_root' => $root,
        ]);
    }

    /** A shop that meets every condition the directory applies, so its page will open. */
    private function shop(string $slug = 'nenas-market-stall', array $store = []): Store
    {
        $organization = Organization::query()->create([
            'name' => "Nena's Market Stall",
            'slug' => $slug,
            'status' => 'active',
        ]);

        $row = Store::query()->create(array_merge([
            'organization_id' => $organization->id,
            'name' => "Nena's Market Stall",
            'code' => 'main',
            'timezone' => 'Asia/Manila',
            'currency_code' => 'PHP',
            'status' => 'active',
            'business_mode' => 'grocery',
            'business_type_label' => 'Sari-sari store',
            'address' => '12 Session Road, Baguio City',
        ], $store));

        Product::query()->create([
            'organization_id' => $organization->id,
            'name' => 'Rice, 1kg',
            'sku' => 'RICE-0001',
            'product_type' => 'standard',
            'price_cents' => 6500,
            'tax_rate' => 12,
            'is_active' => true,
            'business_modes' => ['grocery'],
        ]);

        return $row;
    }

    /**
     * The whole point is which host asked, and a `Host:` header does not carry
     * it here: the test client builds its request from the URL and overwrites
     * the header with what it finds there. So the host goes in the URL.
     */
    private function get_shop(string $host = 'nenas-market-stall.omaykan.com')
    {
        return $this->get('https://'.$host.'/');
    }

    public function test_a_shop_subdomain_answers_with_that_shops_preview_tags(): void
    {
        $this->shop();

        $response = $this->get_shop()->assertOk();
        $html = $response->getContent();

        // The title as well as og:title: Google reads the title tag in
        // preference, and the shell's says "Shop" for every shop there is.
        $this->assertStringContainsString('<title>Nena&#039;s Market Stall — order on Omaykan</title>', $html);
        $this->assertStringNotContainsString('<title>Shop — Omaykan</title>', $html);

        $this->assertStringContainsString('<meta property="og:title" content="Nena&#039;s Market Stall — order on Omaykan" />', $html);
        $this->assertStringContainsString('<meta property="og:url" content="https://nenas-market-stall.omaykan.com/" />', $html);
        $this->assertStringContainsString('<link rel="canonical" href="https://nenas-market-stall.omaykan.com/" />', $html);
        $this->assertStringContainsString('<meta property="og:site_name" content="Omaykan" />', $html);
        $this->assertStringContainsString('Sari-sari store in 12 Session Road, Baguio City.', $html);

        // And the page still works: the bundle's script tag is untouched.
        $this->assertStringContainsString('<script type="module" src="/assets/shop-abc123.js"></script>', $html);
        $this->assertStringContainsString('<div id="shop-app"></div>', $html);
    }

    /**
     * The shell brings its own generic description, and a crawler that finds two
     * reads the first — so the shop's has to replace it, not follow it.
     */
    public function test_the_shells_own_generic_description_is_replaced_not_duplicated(): void
    {
        $this->shop();

        $html = $this->get_shop()->assertOk()->getContent();

        $this->assertSame(1, substr_count($html, 'name="description"'));
        $this->assertStringNotContainsString('Order from a local shop on Omaykan', $html);
        // Tags the shell does not touch are left where they are.
        $this->assertStringContainsString('<meta name="viewport" content="width=device-width, initial-scale=1.0" />', $html);
    }

    public function test_the_preview_image_is_the_shops_own_photo_as_an_absolute_url(): void
    {
        $store = $this->shop();

        Storage::disk('local')->put('store-images/nena.png', base64_decode(self::PNG));
        $store->forceFill(['image_path' => 'store-images/nena.png'])->save();

        $html = $this->get_shop()->assertOk()->getContent();
        $expected = 'https://omaykan.com'.StoreImageController::urlFor($store->fresh());

        // Absolute, and on the apex: a relative og:image is ignored by most
        // scrapers, and one host means one certificate they have already met.
        $this->assertStringContainsString('<meta property="og:image" content="'.e($expected).'" />', $html);
        $this->assertStringContainsString('<meta property="og:image:secure_url" content="'.e($expected).'" />', $html);

        // Measured off the file, not guessed. 1×1 is far too small for the wide
        // card, so the small one is claimed instead — Twitter draws nothing at
        // all when an image misses the size it promised.
        $this->assertStringContainsString('<meta property="og:image:width" content="1" />', $html);
        $this->assertStringContainsString('<meta property="og:image:height" content="1" />', $html);
        $this->assertStringContainsString('<meta property="og:image:type" content="image/png" />', $html);
        $this->assertStringContainsString('<meta name="twitter:card" content="summary" />', $html);
    }

    public function test_a_shop_with_no_photo_falls_back_to_a_picture_every_scraper_accepts(): void
    {
        $this->shop();

        $html = $this->get_shop()->assertOk()->getContent();

        // JPEG, not the nicer hero.webp: Facebook will not draw a WebP card.
        $this->assertStringContainsString(
            '<meta property="og:image" content="https://omaykan.com/storefront/market-stall-cover.jpg" />',
            $html,
        );
        $this->assertStringContainsString('<meta name="twitter:card" content="summary_large_image" />', $html);
    }

    public function test_a_shops_own_shelf_photo_is_preferred_to_the_fallback(): void
    {
        $store = $this->shop();

        Product::query()->where('organization_id', $store->organization_id)->update([
            'image_url' => 'https://cdn.example.test/rice.jpg',
        ]);

        $html = $this->get_shop()->assertOk()->getContent();

        $this->assertStringContainsString('<meta property="og:image" content="https://cdn.example.test/rice.jpg" />', $html);
        // Not ours to measure, so nothing is claimed about its shape.
        $this->assertStringNotContainsString('og:image:width', $html);
        $this->assertStringContainsString('<meta name="twitter:card" content="summary" />', $html);
    }

    public function test_a_shop_name_cannot_break_out_of_the_tags_it_is_written_into(): void
    {
        $this->shop(store: ['name' => 'Tita\'s "Best" <Brew> & Co']);

        $html = $this->get_shop()->assertOk()->getContent();

        $this->assertStringNotContainsString('<Brew>', $html);
        $this->assertStringContainsString('Tita&#039;s &quot;Best&quot; &lt;Brew&gt; &amp; Co', $html);
    }

    /**
     * The rule that keeps this from ever taking a storefront down: no shop to
     * name means the file goes out exactly as nginx used to serve it, and the
     * page says its own piece about not finding the shop.
     */
    public function test_an_unknown_slug_still_gets_the_shell_with_no_tags(): void
    {
        $response = $this->get_shop('nobody-here.omaykan.com')->assertOk();

        $this->assertSame(self::SHELL, $response->getContent());
    }

    public function test_a_suspended_shop_gets_no_preview(): void
    {
        $store = $this->shop();
        $store->organization->forceFill(['suspended' => true])->save();

        $this->assertSame(self::SHELL, $this->get_shop()->assertOk()->getContent());
    }

    /**
     * A shop with an empty shelf is absent from `GET /api/stores`, which is what
     * the page matches its own hostname against — so its page says it cannot
     * find the shop, and a card would have been a promise of nothing.
     */
    public function test_a_shop_with_nothing_on_the_shelf_gets_no_preview(): void
    {
        $store = $this->shop();
        Product::query()->where('organization_id', $store->organization_id)->update(['is_active' => false]);

        $this->assertSame(self::SHELL, $this->get_shop()->assertOk()->getContent());
    }

    public function test_a_reserved_label_is_not_a_shop(): void
    {
        $this->shop('www');

        $this->get_shop('www.omaykan.com')->assertOk()->assertSee('Laravel', false);
    }

    public function test_a_host_that_is_not_a_shop_address_is_left_alone(): void
    {
        $this->shop();

        // The apex, a nested name under it, and another domain entirely. None
        // is one shop's address, and reading a slug out of any of them would be
        // inventing a name under the shops' certificate.
        foreach (['omaykan.com', 'a.b.omaykan.com', 'example.test'] as $host) {
            $this->get_shop($host)->assertOk()->assertSee('Laravel', false);
        }
    }

    /**
     * Against the file the build actually produces, not the stand-in above.
     *
     * The injection needs two things from it — a `</head>` and a `<title>` — and
     * both are properties of a file this application does not own. Skipped where
     * there is no build to read, which is every deployment of the backend on its
     * own and any checkout that has not run `npm run build:web`.
     */
    public function test_the_real_built_shell_takes_the_tags(): void
    {
        $dist = base_path('../apps/web/dist');

        if (! is_file($dist.'/shop.html')) {
            $this->markTestSkipped('No frontend build at '.$dist.'.');
        }

        config(['shops.web_root' => $dist]);
        $this->shop();

        $html = $this->get_shop()->assertOk()->getContent();

        $this->assertStringContainsString('<title>Nena&#039;s Market Stall — order on Omaykan</title>', $html);
        $this->assertStringContainsString('<meta property="og:title"', $html);
        // One description, and it is the shop's — the real file ships a generic
        // one of its own, over three lines.
        $this->assertSame(1, substr_count($html, 'name="description"'));
        $this->assertStringContainsString('Sari-sari store in 12 Session Road', $html);
        // The bundle survived: whatever else this did, the page still starts.
        $this->assertStringContainsString('id="shop-app"', $html);
        $this->assertMatchesRegularExpression('#<script type="module" [^>]*src="/assets/shop-[^"]+\.js"#', $html);
    }

    public function test_no_session_cookie_is_set_on_a_shop_page(): void
    {
        $this->shop();

        // SESSION_DRIVER is `database`. A session here would mean a row per
        // visitor and per crawler, and a Set-Cookie on a cacheable page.
        $response = $this->get_shop()->assertOk();

        $this->assertNull($response->headers->getCookies()[0] ?? null);

        // Symfony sorts the directives; what matters is that both are there.
        $this->assertSame('max-age=300, public', $response->headers->get('Cache-Control'));
    }
}
