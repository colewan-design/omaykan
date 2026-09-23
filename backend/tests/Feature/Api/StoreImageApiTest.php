<?php

namespace Tests\Feature\Api;

use App\Models\Product;
use App\Models\Store;
use Illuminate\Foundation\Testing\DatabaseMigrations;
use Illuminate\Support\Facades\Storage;
use Tests\Concerns\SignsInStaff;
use Tests\TestCase;

/**
 * The shop photo an owner uploads in Settings, on its way to the customer.
 *
 * DatabaseMigrations rather than RefreshDatabase: pairing a device issues a
 * Sanctum token, and the token guard reads it back on a second connection that
 * cannot see inside a rolled-back transaction.
 */
class StoreImageApiTest extends TestCase
{
    use SignsInStaff, DatabaseMigrations;

    /** A real 1x1 PNG — small enough to inline, real enough for getimagesize(). */
    private const PNG = 'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==';

    private function pngDataUrl(string $mime = 'image/png'): string
    {
        return "data:{$mime};base64,".self::PNG;
    }

    private function putImage(?string $image, ?string $token = null)
    {
        return $this->withHeader('Authorization', 'Bearer '.($token ?? $this->staffToken()))
            ->putJson('/api/seller/store-image', ['image' => $image]);
    }

    /** Gives the seeded shop's one product a photo, so there is a fallback to lose to. */
    private function giveTheShelfAPhoto(): void
    {
        Product::query()->where('sku', 'ESP-0001')->update(['image_url' => '/products/espresso.jpg']);
    }

    public function test_a_till_publishes_its_own_shops_photo(): void
    {
        Storage::fake('local');
        $this->seed();

        $url = $this->putImage($this->pngDataUrl())->assertOk()->json('imageUrl');

        $store = Store::query()->firstOrFail();
        $this->assertNotNull($store->image_path);
        Storage::disk('local')->assertExists($store->image_path);
        $this->assertStringStartsWith("/api/stores/{$store->id}/image?v=", $url);
    }

    public function test_the_photo_is_streamed_back_to_anyone(): void
    {
        Storage::fake('local');
        $this->seed();

        $url = $this->putImage($this->pngDataUrl())->assertOk()->json('imageUrl');

        // No Authorization header: this is the picture the shop puts on its
        // own storefront, and the carousel that shows it has no account.
        $this->get($url)
            ->assertOk()
            ->assertHeader('Content-Type', 'image/png');
    }

    public function test_a_shop_that_has_uploaded_nothing_has_no_photo_to_serve(): void
    {
        $this->seed();
        $store = Store::query()->firstOrFail();

        $this->get("/api/stores/{$store->id}/image")->assertNotFound();
    }

    public function test_the_directory_prefers_the_owners_photo_over_one_off_the_shelf(): void
    {
        Storage::fake('local');
        $this->seed();
        $this->giveTheShelfAPhoto();

        $this->getJson('/api/stores')
            ->assertOk()
            ->assertJsonPath('stores.0.imageUrl', '/products/espresso.jpg');

        $url = $this->putImage($this->pngDataUrl())->assertOk()->json('imageUrl');

        $this->getJson('/api/stores')
            ->assertOk()
            ->assertJsonPath('stores.0.imageUrl', $url);
    }

    public function test_clearing_the_photo_falls_back_to_the_shelf_and_deletes_the_file(): void
    {
        Storage::fake('local');
        $this->seed();
        $this->giveTheShelfAPhoto();

        $token = $this->staffToken();
        $this->putImage($this->pngDataUrl(), $token)->assertOk();
        $path = Store::query()->firstOrFail()->image_path;

        $this->putImage(null, $token)->assertOk()->assertJsonPath('imageUrl', null);

        $this->assertNull(Store::query()->firstOrFail()->image_path);
        Storage::disk('local')->assertMissing($path);
        $this->getJson('/api/stores')->assertJsonPath('stores.0.imageUrl', '/products/espresso.jpg');
    }

    public function test_replacing_the_photo_does_not_leave_the_old_file_behind(): void
    {
        Storage::fake('local');
        $this->seed();

        $token = $this->staffToken();
        $this->putImage($this->pngDataUrl(), $token)->assertOk();
        $first = Store::query()->firstOrFail()->image_path;

        $this->putImage($this->pngDataUrl(), $token)->assertOk();
        $second = Store::query()->firstOrFail()->image_path;

        $this->assertNotSame($first, $second);
        Storage::disk('local')->assertMissing($first);
        Storage::disk('local')->assertExists($second);
    }

    public function test_something_that_is_not_an_image_is_rejected(): void
    {
        Storage::fake('local');
        $this->seed();

        $this->putImage('data:image/png;base64,'.base64_encode('<?php echo "hello";'))
            ->assertStatus(422);

        $this->assertNull(Store::query()->firstOrFail()->image_path);
    }

    /**
     * The mime in a data URL is the sender's claim about what it sent. A PNG
     * announced as a JPEG is the cheapest possible probe of whether anything
     * checks, so it has to fail.
     */
    public function test_a_photo_mislabelled_as_another_type_is_rejected(): void
    {
        Storage::fake('local');
        $this->seed();

        $this->putImage($this->pngDataUrl('image/jpeg'))->assertStatus(422);
    }

    public function test_a_type_we_do_not_accept_is_rejected(): void
    {
        Storage::fake('local');
        $this->seed();

        $this->putImage('data:image/svg+xml;base64,'.base64_encode('<svg xmlns="http://www.w3.org/2000/svg"/>'))
            ->assertStatus(422);
    }

    public function test_a_garbled_payload_is_rejected(): void
    {
        Storage::fake('local');
        $this->seed();

        $this->putImage('not a data url at all')->assertStatus(422);
    }

    public function test_an_unpaired_caller_cannot_change_the_photo(): void
    {
        Storage::fake('local');
        $this->seed();

        $this->putJson('/api/seller/store-image', ['image' => $this->pngDataUrl()])
            ->assertUnauthorized();

        $this->assertNull(Store::query()->firstOrFail()->image_path);
    }
}
