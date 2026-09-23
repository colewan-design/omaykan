<?php

namespace Tests\Feature\Api;

use App\Http\Controllers\Api\ProductImageController;
use App\Models\Organization;
use App\Models\Product;
use App\Models\StoreMembership;
use App\Models\SyncEvent;
use App\Models\User;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Illuminate\Support\Facades\Storage;
use Illuminate\Support\Str;
use Tests\Concerns\SignsInStaff;
use Tests\TestCase;

/**
 * Product photos as files, not base64 in rows; and product descriptions.
 * See documentation/merchant-features.md §4.
 */
class ProductImageApiTest extends TestCase
{
    use RefreshDatabase, SignsInStaff;

    /** A real 1×1 PNG. */
    private const PNG = 'data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNkYAAAAAYAAjCB0C8AAAAASUVORK5CYII=';

    /** PHP, wearing a PNG label. */
    private const DISGUISED = 'data:image/png;base64,PD9waHAgZWNobyAiaGkiOyA/Pg==';

    protected function setUp(): void
    {
        parent::setUp();
        Storage::fake('local');
    }

    private function org(): Organization
    {
        return Organization::query()->where('slug', 'demo-coffee')->firstOrFail();
    }

    private function espresso(): Product
    {
        return Product::query()->where('sku', 'ESP-0001')->firstOrFail();
    }

    private function pushProduct(string $token, array $fields)
    {
        $product = $this->espresso();

        return $this->withToken($token)->postJson('/api/sync/push', [
            'organizationId' => $this->org()->id,
            'storeId' => $this->org()->stores()->firstOrFail()->id,
            'events' => [[
                'id' => (string) Str::uuid(),
                'entityType' => 'product',
                'entityId' => $product->id,
                'operation' => 'upsert',
                'occurredAt' => now()->toIso8601String(),
                'payload' => array_merge([
                    'name' => $product->name,
                    'categoryId' => $product->category_id,
                    'sku' => $product->sku,
                    'priceCents' => $product->price_cents,
                ], $fields),
            ]],
        ])->assertOk();
    }

    public function test_a_photo_synced_inline_is_stored_as_a_file_and_the_row_holds_a_url(): void
    {
        $this->seed();

        $this->pushProduct($this->staffToken(), [
            'imageUrl' => self::PNG,
            'photoUrls' => [self::PNG, 'https://cdn.example/label.png'],
        ])->assertJsonPath('results.0.status', 'applied');

        $product = $this->espresso();

        $this->assertStringNotContainsString('base64', $product->image_url);
        $this->assertNotNull($path = ProductImageController::pathFor($product->image_url));
        Storage::disk('local')->assertExists($path);

        $this->assertCount(2, $product->photo_urls);
        $this->assertNotNull(ProductImageController::pathFor($product->photo_urls[0]));
        $this->assertSame('https://cdn.example/label.png', $product->photo_urls[1]);

        // Served back, publicly.
        $this->withoutToken()->get('/api/product-images/'.basename($path))->assertOk();

        // And the audit copy of the event does not keep the bytes either.
        $this->assertStringNotContainsString('base64', json_encode(SyncEvent::query()->latest('received_at')->first()->payload));
    }

    /** A bad picture must not cost the merchant the price change beside it. */
    public function test_a_photo_that_is_not_an_image_is_dropped_and_the_product_still_saves(): void
    {
        $this->seed();
        $this->espresso()->forceFill(['image_url' => 'https://cdn.example/espresso.png'])->save();

        $this->pushProduct($this->staffToken(), ['imageUrl' => self::DISGUISED, 'priceCents' => 15000])
            ->assertJsonPath('results.0.status', 'applied');

        $this->assertSame(15000, $this->espresso()->price_cents);
        $this->assertSame('https://cdn.example/espresso.png', $this->espresso()->image_url);
    }

    public function test_the_upload_endpoint_stores_a_photo_for_whoever_may_change_products(): void
    {
        $this->seed();

        $url = $this->withToken($this->staffToken())
            ->postJson('/api/seller/product-images', ['image' => self::PNG])
            ->assertCreated()
            ->json('url');

        Storage::disk('local')->assertExists(ProductImageController::pathFor($url));
    }

    public function test_the_upload_endpoint_refuses_a_cashier_and_a_disguised_file(): void
    {
        $this->seed();

        $this->withToken($this->staffToken())
            ->postJson('/api/seller/product-images', ['image' => self::DISGUISED])
            ->assertUnprocessable()
            ->assertJsonPath('message', 'That file is not an image.');

        $cashier = User::query()->create(['name' => 'C', 'username' => 'cashier1', 'password' => 'password', 'status' => 'active']);
        StoreMembership::query()->create([
            'store_id' => $this->org()->stores()->firstOrFail()->id, 'user_id' => $cashier->id, 'membership_role' => 'cashier',
        ]);

        $this->withToken($this->staffToken('cashier1'))
            ->postJson('/api/seller/product-images', ['image' => self::PNG])
            ->assertForbidden();
    }

    public function test_the_route_only_serves_uuid_file_names(): void
    {
        $this->get('/api/product-images/..%2F..%2F.env')->assertNotFound();
        $this->get('/api/product-images/not-a-uuid.png')->assertNotFound();
    }

    public function test_a_description_is_synced_capped_and_served_on_the_storefront(): void
    {
        $this->seed();
        $token = $this->staffToken();

        $this->pushProduct($token, ['description' => '  Pulled to order, double shot.  ']);
        $this->assertSame('Pulled to order, double shot.', $this->espresso()->description);

        $this->withoutToken()
            ->getJson('/api/storefront/catalog?orgSlug=demo-coffee&storeCode=main')
            ->assertJsonPath('products.0.description', 'Pulled to order, double shot.');

        // An update that does not mention it keeps it.
        $this->pushProduct($token, ['priceCents' => 12500]);
        $this->assertSame('Pulled to order, double shot.', $this->espresso()->description);

        $this->pushProduct($token, ['description' => str_repeat('a', 2500)]);
        $this->assertSame(2000, mb_strlen($this->espresso()->description));
    }

    public function test_the_backfill_moves_existing_inline_photos_and_is_safe_to_rerun(): void
    {
        $this->seed();
        $this->espresso()->forceFill(['image_url' => self::PNG, 'photo_urls' => [self::PNG]])->save();

        $this->artisan('products:extract-inline-images', ['--dry-run' => true])->assertSuccessful();
        $this->assertSame(self::PNG, $this->espresso()->image_url);

        $this->artisan('products:extract-inline-images')->assertSuccessful();
        $moved = $this->espresso();
        $this->assertNotNull(ProductImageController::pathFor($moved->image_url));
        $this->assertNotNull(ProductImageController::pathFor($moved->photo_urls[0]));

        $this->artisan('products:extract-inline-images')->assertSuccessful();
        $this->assertSame($moved->image_url, $this->espresso()->image_url);
    }

    public function test_the_sweep_deletes_only_old_unreferenced_files(): void
    {
        $this->seed();
        $disk = Storage::disk('local');

        $kept = 'product-images/'.Str::uuid().'.png';
        $orphan = 'product-images/'.Str::uuid().'.png';
        $fresh = 'product-images/'.Str::uuid().'.png';
        foreach ([$kept, $orphan, $fresh] as $path) {
            $disk->put($path, 'x');
        }

        // Real file ages, not Carbon's clock: the sweep reads mtimes.
        touch($disk->path($kept), time() - 3 * 86400);
        touch($disk->path($orphan), time() - 3 * 86400);

        $this->espresso()->forceFill(['image_url' => ProductImageController::urlFor($kept)])->save();

        $this->artisan('products:sweep-images')->assertSuccessful();

        $disk->assertExists($kept);
        $disk->assertExists($fresh);
        $disk->assertMissing($orphan);
    }
}
