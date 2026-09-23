<?php

namespace Tests\Feature\Api;

use App\Models\Order;
use App\Models\Organization;
use App\Models\Product;
use App\Models\Rider;
use App\Models\Store;
use App\Models\StoreSavedRider;
use Illuminate\Foundation\Testing\DatabaseMigrations;
use Tests\Concerns\SignsInStaff;
use Tests\TestCase;

/**
 * The shop choosing its own rider, and keeping them.
 *
 * Two things are being protected here at once. The first is the feature: a shop
 * names its rider once and picks them thereafter, and a shop with no rider of
 * its own is unaffected because the board still exists. The second is the
 * boundary — a saved-rider list must not become a way for any merchant to read
 * the platform's rider roster, and the tests that would catch that regression
 * are the ones about riderId and about another shop's rows.
 */
class SellerSavedRiderApiTest extends TestCase
{
    use SignsInStaff, DatabaseMigrations;

    private function rider(string $email, string $name, string $phone, string $status = Rider::STATUS_APPROVED): Rider
    {
        return Rider::query()->create([
            'name' => $name,
            'email' => $email,
            'phone' => $phone,
            'password' => 'ride-with-me-1',
            'license_number' => 'N01-23-456789',
            'plate_number' => 'NBC 1234',
            'license_image_path' => 'rider-documents/license.jpg',
            'plate_image_path' => 'rider-documents/plate.jpg',
            'status' => $status,
        ]);
    }

    /** A second shop in its own organization, with an owner of its own. */
    private function rivalStaffToken(): string
    {
        return $this->staffTokenForNewTenant();
    }

    private function placeDeliveryOrder(): string
    {
        $product = Product::query()->where('sku', 'ESP-0001')->firstOrFail();

        return $this->postJson('/api/online-orders', [
            'orgSlug' => 'demo-coffee',
            'storeCode' => 'main',
            'businessMode' => 'coffee-shop',
            'items' => [['productId' => $product->id, 'quantity' => 1]],
            'guest' => ['name' => 'Maria Santos', 'phone' => '09171234567'],
            'fulfillment' => ['method' => 'delivery', 'address' => '12 Session Road, Sto. Tomas, Baguio City'],
        ])->assertCreated()->json('orderId');
    }

    /** @return \Illuminate\Testing\TestResponse */
    private function asDevice(string $token, string $method, string $uri, array $body = [])
    {
        $this->app['auth']->forgetGuards();

        return $this->withHeader('Authorization', "Bearer {$token}")->json($method, $uri, $body);
    }

    public function test_a_shop_saves_a_rider_who_has_no_account_at_all(): void
    {
        $this->seed();
        $token = $this->staffToken();

        $this->asDevice($token, 'POST', '/api/seller/riders', [
            'name' => 'Kuya Jun',
            'phone' => '0917 555 0101',
            'note' => 'Weekday mornings, own tricycle',
        ])
            ->assertCreated()
            ->assertJsonPath('savedRider.name', 'Kuya Jun')
            // No account matched, so this is a name and a number the shop rings.
            ->assertJsonPath('savedRider.onPlatform', false)
            ->assertJsonPath('savedRider.status', null);

        $this->asDevice($token, 'GET', '/api/seller/riders')
            ->assertOk()
            ->assertJsonPath('saved.0.note', 'Weekday mornings, own tricycle');
    }

    public function test_saving_by_phone_number_links_a_platform_account_when_one_matches(): void
    {
        $this->seed();
        $this->rider('jun@example.com', 'Jun Dela Cruz', '+63 917 555 0101');

        // The shop types the number the way they have it in their own phone.
        // A different format for the same person must still find them.
        $saved = $this->asDevice($this->staffToken(), 'POST', '/api/seller/riders', [
            'name' => 'Kuya Jun',
            'phone' => '0917 555 0101',
        ])
            ->assertCreated()
            ->assertJsonPath('savedRider.onPlatform', true)
            ->assertJsonPath('savedRider.status', Rider::STATUS_APPROVED)
            // What the shop calls them, not what is on the licence.
            ->assertJsonPath('savedRider.name', 'Kuya Jun')
            ->json('savedRider');

        $this->assertNotNull($saved['riderId']);
    }

    public function test_a_shop_cannot_bind_a_rider_it_has_never_worked_with_by_id(): void
    {
        $this->seed();
        $stranger = $this->rider('stranger@example.com', 'Someone Else', '0917 555 9999');

        // The only reason this route exists is the "recent" list, which is
        // riders who already delivered here. Naming a UUID outright is how a
        // rider roster would leak, so it is refused.
        $this->asDevice($this->staffToken(), 'POST', '/api/seller/riders', [
            'riderId' => $stranger->id,
            'name' => 'Someone Else',
        ])->assertStatus(422);

        $this->assertSame(0, StoreSavedRider::query()->count());
    }

    public function test_the_picker_offers_riders_who_have_delivered_here(): void
    {
        $this->seed();
        $rider = $this->rider('jun@example.com', 'Jun Dela Cruz', '0917 555 0101');
        $token = $this->staffToken();

        $orderId = $this->placeDeliveryOrder();
        Order::query()->whereKey($orderId)->update([
            'rider_id' => $rider->id,
            'rider_name' => $rider->name,
            'delivery_stage' => 'delivered',
        ]);

        $this->asDevice($token, 'GET', '/api/seller/riders')
            ->assertOk()
            ->assertJsonPath('saved', [])
            ->assertJsonPath('recent.0.riderId', $rider->id)
            ->assertJsonPath('recent.0.name', 'Jun Dela Cruz');

        // And having worked here, they can now be saved by id in one tap.
        $this->asDevice($token, 'POST', '/api/seller/riders', [
            'riderId' => $rider->id,
            'name' => 'Kuya Jun',
        ])->assertCreated()->assertJsonPath('savedRider.onPlatform', true);

        // Saved riders leave the "recent" list rather than appearing twice.
        $this->asDevice($token, 'GET', '/api/seller/riders')
            ->assertOk()
            ->assertJsonPath('recent', [])
            ->assertJsonPath('saved.0.name', 'Kuya Jun');
    }

    public function test_assigning_a_saved_platform_rider_puts_the_order_in_their_app(): void
    {
        $this->seed();
        $rider = $this->rider('jun@example.com', 'Jun Dela Cruz', '0917 555 0101');
        $token = $this->staffToken();
        $orderId = $this->placeDeliveryOrder();

        Order::query()->whereKey($orderId)->update(['rider_id' => $rider->id, 'delivery_stage' => 'delivered']);
        $saved = $this->asDevice($token, 'POST', '/api/seller/riders', [
            'riderId' => $rider->id,
            'name' => 'Kuya Jun',
        ])->assertCreated()->json('savedRider');
        Order::query()->whereKey($orderId)->update(['rider_id' => null, 'delivery_stage' => 'pending']);

        $this->asDevice($token, 'POST', "/api/seller/online-orders/{$orderId}/rider", [
            'savedRiderId' => $saved['id'],
        ])
            ->assertOk()
            ->assertJsonPath('order.riderName', 'Kuya Jun')
            ->assertJsonPath('order.riderId', $rider->id)
            ->assertJsonPath('order.deliveryStage', 'assigned');

        // The rider's own app reads `mine` off rider_id, so this is the whole
        // point: a shop-chosen order reaches the same screen a board-claimed
        // one does, with no second code path.
        $riderToken = $rider->createToken('rider-portal', ['rider'])->plainTextToken;
        $this->app['auth']->forgetGuards();
        $this->withHeader('Authorization', "Bearer {$riderToken}")
            ->getJson('/api/rider/deliveries')
            ->assertOk()
            ->assertJsonPath('active.0.id', $orderId);

        // And it is no longer up for grabs.
        $this->app['auth']->forgetGuards();
        $this->withHeader('Authorization', "Bearer {$riderToken}")
            ->getJson('/api/rider/board')
            ->assertOk()
            ->assertJsonPath('orders', []);

        // Picking someone promotes them in the picker without anyone ranking.
        $this->assertSame(1, StoreSavedRider::query()->findOrFail($saved['id'])->times_used);
    }

    public function test_a_typed_in_rider_can_be_saved_on_the_way_past(): void
    {
        $this->seed();
        $token = $this->staffToken();
        $orderId = $this->placeDeliveryOrder();

        $this->asDevice($token, 'POST', "/api/seller/online-orders/{$orderId}/rider", [
            'riderName' => 'Kuya Jun',
            'riderPhone' => '0917 555 0101',
            'saveRider' => true,
            'saveNote' => 'Tricycle, afternoons',
        ])
            ->assertOk()
            ->assertJsonPath('order.riderName', 'Kuya Jun')
            // Typed in, so no account and no live map — just a name and a number.
            ->assertJsonPath('order.riderId', null);

        $this->asDevice($token, 'GET', '/api/seller/riders')
            ->assertOk()
            ->assertJsonPath('saved.0.name', 'Kuya Jun')
            ->assertJsonPath('saved.0.note', 'Tricycle, afternoons')
            ->assertJsonPath('saved.0.onPlatform', false);
    }

    public function test_assigning_a_suspended_rider_is_refused_with_a_reason(): void
    {
        $this->seed();
        $rider = $this->rider('jun@example.com', 'Jun Dela Cruz', '0917 555 0101');
        $token = $this->staffToken();
        $orderId = $this->placeDeliveryOrder();

        Order::query()->whereKey($orderId)->update(['rider_id' => $rider->id, 'delivery_stage' => 'delivered']);
        $saved = $this->asDevice($token, 'POST', '/api/seller/riders', [
            'riderId' => $rider->id,
            'name' => 'Kuya Jun',
        ])->assertCreated()->json('savedRider');
        Order::query()->whereKey($orderId)->update(['rider_id' => null, 'delivery_stage' => 'pending']);

        $rider->forceFill(['status' => Rider::STATUS_SUSPENDED])->save();

        // Silently assigning would park the order at `assigned` forever: the
        // rider's app is 403 on every route past sign-in.
        $this->asDevice($token, 'POST', "/api/seller/online-orders/{$orderId}/rider", [
            'savedRiderId' => $saved['id'],
        ])->assertStatus(422);

        $this->assertSame('pending', Order::query()->findOrFail($orderId)->delivery_stage);
    }

    public function test_unassigning_returns_the_order_to_the_board(): void
    {
        $this->seed();
        $token = $this->staffToken();
        $orderId = $this->placeDeliveryOrder();

        $this->asDevice($token, 'POST', "/api/seller/online-orders/{$orderId}/rider", ['riderName' => 'Kuya Jun'])
            ->assertOk();

        $this->asDevice($token, 'DELETE', "/api/seller/online-orders/{$orderId}/rider")
            ->assertOk()
            ->assertJsonPath('order.riderName', null)
            ->assertJsonPath('order.deliveryStage', 'pending');

        $riderToken = $this->rider('jun@example.com', 'Jun Dela Cruz', '0917 555 0101')
            ->createToken('rider-portal', ['rider'])->plainTextToken;
        $this->app['auth']->forgetGuards();
        $this->withHeader('Authorization', "Bearer {$riderToken}")
            ->getJson('/api/rider/board')
            ->assertOk()
            ->assertJsonPath('orders.0.id', $orderId);
    }

    public function test_unassigning_is_refused_once_the_food_is_in_the_bag(): void
    {
        $this->seed();
        $token = $this->staffToken();
        $orderId = $this->placeDeliveryOrder();

        $this->asDevice($token, 'POST', "/api/seller/online-orders/{$orderId}/rider", ['riderName' => 'Kuya Jun'])
            ->assertOk();
        $this->asDevice($token, 'POST', "/api/seller/online-orders/{$orderId}/delivery-stage", ['stage' => 'picked_up'])
            ->assertOk();

        $this->asDevice($token, 'DELETE', "/api/seller/online-orders/{$orderId}/rider")
            ->assertStatus(422);
    }

    public function test_a_shop_cannot_read_or_delete_another_shops_saved_rider(): void
    {
        $this->seed();

        $mine = $this->asDevice($this->staffToken(), 'POST', '/api/seller/riders', [
            'name' => 'Kuya Jun',
            'phone' => '0917 555 0101',
        ])->assertCreated()->json('savedRider');

        $otherToken = $this->rivalStaffToken();

        $this->asDevice($otherToken, 'GET', '/api/seller/riders')
            ->assertOk()
            ->assertJsonPath('saved', []);

        $this->asDevice($otherToken, 'DELETE', "/api/seller/riders/{$mine['id']}")
            ->assertForbidden();

        $this->assertSame(1, StoreSavedRider::query()->count());
    }

    public function test_two_riders_saved_without_a_number_stay_two_riders(): void
    {
        $this->seed();
        $token = $this->staffToken();

        // No phone on either, so the number cannot be the key. Keying on
        // nothing would let the second save silently rename the first, and a
        // shop would watch one of their riders vanish from the picker.
        $this->asDevice($token, 'POST', '/api/seller/riders', ['name' => 'Jun'])->assertCreated();
        $this->asDevice($token, 'POST', '/api/seller/riders', ['name' => 'Ana'])->assertCreated();

        $this->asDevice($token, 'GET', '/api/seller/riders')
            ->assertOk()
            ->assertJsonCount(2, 'saved');

        // The same nameless-number rider twice is still one rider, though.
        $this->asDevice($token, 'POST', '/api/seller/riders', ['name' => 'Ana', 'note' => 'Mornings'])
            ->assertCreated();

        $this->asDevice($token, 'GET', '/api/seller/riders')
            ->assertOk()
            ->assertJsonCount(2, 'saved');
    }

    public function test_saving_the_same_number_twice_edits_rather_than_duplicates(): void
    {
        $this->seed();
        $token = $this->staffToken();

        $this->asDevice($token, 'POST', '/api/seller/riders', ['name' => 'Jun', 'phone' => '0917 555 0101'])
            ->assertCreated();
        $this->asDevice($token, 'POST', '/api/seller/riders', ['name' => 'Kuya Jun', 'phone' => '0917 555 0101'])
            ->assertCreated();

        $this->asDevice($token, 'GET', '/api/seller/riders')
            ->assertOk()
            ->assertJsonCount(1, 'saved')
            ->assertJsonPath('saved.0.name', 'Kuya Jun');
    }
}
