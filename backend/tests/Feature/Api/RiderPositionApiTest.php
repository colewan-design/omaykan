<?php

namespace Tests\Feature\Api;

use App\Events\RiderPositionUpdated;
use App\Models\Order;
use App\Models\Product;
use App\Models\Rider;
use Illuminate\Foundation\Testing\DatabaseMigrations;
use Illuminate\Support\Facades\Event;
use Tests\Concerns\SignsInStaff;
use Tests\TestCase;

/**
 * Where the rider is, and who is allowed to know.
 *
 * The interesting assertions here are all negative ones. A live location feed
 * is easy to build and easy to over-share, and every rule that bounds it —
 * only while carrying, only to the two parties, never retained — is a rule
 * that will look removable to somebody in a hurry unless a test fails when
 * they remove it.
 */
class RiderPositionApiTest extends TestCase
{
    use SignsInStaff, DatabaseMigrations;

    private function approvedRider(string $email = 'jun@example.com'): Rider
    {
        return Rider::query()->create([
            'name' => 'Jun Dela Cruz',
            'email' => $email,
            'phone' => '0917 555 0101',
            'password' => 'ride-with-me-1',
            'license_number' => 'N01-23-456789',
            'plate_number' => 'NBC 1234',
            'license_image_path' => 'rider-documents/license.jpg',
            'plate_image_path' => 'rider-documents/plate.jpg',
            'status' => Rider::STATUS_APPROVED,
        ]);
    }

    private function tokenFor(Rider $rider): string
    {
        return $rider->createToken('rider-portal', ['rider'])->plainTextToken;
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
    private function asRider(string $token, string $method, string $uri, array $body = [])
    {
        $this->app['auth']->forgetGuards();

        return $this->withHeader('Authorization', "Bearer {$token}")
            ->json($method, $uri, $body);
    }

    public function test_a_ping_is_recorded_and_broadcast_for_each_order_being_carried(): void
    {
        $this->seed();
        Event::fake([RiderPositionUpdated::class]);

        $rider = $this->approvedRider();
        $token = $this->tokenFor($rider);
        $orderId = $this->placeDeliveryOrder();

        $this->asRider($token, 'POST', "/api/rider/deliveries/{$orderId}/accept")->assertOk();

        $this->asRider($token, 'POST', '/api/rider/position', [
            'lat' => 16.4023,
            'lng' => 120.5960,
            'headingDeg' => 91.5,
            'speedKph' => 22.4,
            'accuracyM' => 8,
        ])
            ->assertOk()
            ->assertJsonPath('activeDeliveries', 1)
            // Carrying something means the fast cadence; the server, not the
            // app, decides that.
            ->assertJsonPath('nextPingSeconds', 10);

        $this->assertEqualsWithDelta(16.4023, (float) $rider->fresh()->last_lat, 0.0000001);

        Event::assertDispatched(
            RiderPositionUpdated::class,
            fn (RiderPositionUpdated $event) => $event->orderId === $orderId
                && $event->position['headingDeg'] === 91.5,
        );
    }

    public function test_a_rider_carrying_nothing_tells_nobody(): void
    {
        $this->seed();
        Event::fake([RiderPositionUpdated::class]);

        $rider = $this->approvedRider();

        $this->asRider($this->tokenFor($rider), 'POST', '/api/rider/position', [
            'lat' => 16.4023,
            'lng' => 120.5960,
        ])
            ->assertOk()
            ->assertJsonPath('activeDeliveries', 0)
            // Nobody is watching, so the phone is told to ease off.
            ->assertJsonPath('nextPingSeconds', 60);

        // The fix is still kept — a rider who opens the app before taking a job
        // should be placeable the instant they take one.
        $this->assertNotNull($rider->fresh()->last_lat);
        Event::assertNotDispatched(RiderPositionUpdated::class);
    }

    public function test_the_customer_sees_the_position_only_while_the_delivery_is_live(): void
    {
        $this->seed();

        $rider = $this->approvedRider();
        $token = $this->tokenFor($rider);
        $orderId = $this->placeDeliveryOrder();

        $this->asRider($token, 'POST', "/api/rider/deliveries/{$orderId}/accept")->assertOk();
        $this->asRider($token, 'POST', '/api/rider/position', ['lat' => 16.4023, 'lng' => 120.5960])->assertOk();

        $this->getJson("/api/online-orders/{$orderId}")
            ->assertOk()
            ->assertJsonPath('riderPosition.lat', 16.4023)
            ->assertJsonPath('riderPosition.stale', false)
            // The two fixed ends of the trip travel with it, or the map has
            // nothing to draw the rider between.
            ->assertJsonPath('route.dropoff.address', '12 Session Road, Sto. Tomas, Baguio City');

        $this->asRider($token, 'POST', "/api/rider/deliveries/{$orderId}/stage", ['stage' => 'picked_up'])->assertOk();
        $this->asRider($token, 'POST', "/api/rider/deliveries/{$orderId}/stage", ['stage' => 'delivered'])->assertOk();

        // Handed over. The tracking link is forwardable and forever; a rider's
        // movements are neither.
        $this->getJson("/api/online-orders/{$orderId}")
            ->assertOk()
            ->assertJsonPath('deliveryStage', 'delivered')
            ->assertJsonPath('riderPosition', null);
    }

    public function test_an_order_carried_by_a_typed_in_rider_has_no_position(): void
    {
        $this->seed();

        $orderId = $this->placeDeliveryOrder();
        $sellerToken = $this->staffToken();

        $this->app['auth']->forgetGuards();
        $this->withHeader('Authorization', "Bearer {$sellerToken}")
            ->postJson("/api/seller/online-orders/{$orderId}/rider", ['riderName' => 'Kuya Jun'])
            ->assertOk();

        $this->app['auth']->forgetGuards();
        $this->getJson("/api/online-orders/{$orderId}")
            ->assertOk()
            ->assertJsonPath('riderName', 'Kuya Jun')
            ->assertJsonPath('riderPosition', null);
    }

    public function test_turning_sharing_off_forgets_the_last_fix(): void
    {
        $this->seed();

        $rider = $this->approvedRider();
        $token = $this->tokenFor($rider);

        $this->asRider($token, 'POST', '/api/rider/position', ['lat' => 16.4023, 'lng' => 120.5960])->assertOk();
        $this->asRider($token, 'DELETE', '/api/rider/position')->assertOk();

        $fresh = $rider->fresh();
        $this->assertNull($fresh->last_lat);
        $this->assertNull($fresh->position_updated_at);
        $this->assertFalse($fresh->isReportingPosition());
    }

    public function test_a_pending_rider_cannot_report_a_position(): void
    {
        $this->seed();

        $rider = $this->approvedRider();
        $rider->forceFill(['status' => Rider::STATUS_PENDING])->save();

        $this->asRider($this->tokenFor($rider), 'POST', '/api/rider/position', [
            'lat' => 16.4023,
            'lng' => 120.5960,
        ])->assertForbidden();
    }

    public function test_a_nonsense_coordinate_is_rejected(): void
    {
        $this->seed();

        $this->asRider($this->tokenFor($this->approvedRider()), 'POST', '/api/rider/position', [
            'lat' => 916.4023,
            'lng' => 120.5960,
        ])->assertStatus(422);
    }

    public function test_a_stale_fix_is_served_as_stale_rather_than_hidden(): void
    {
        $this->seed();

        $rider = $this->approvedRider();
        $token = $this->tokenFor($rider);
        $orderId = $this->placeDeliveryOrder();

        $this->asRider($token, 'POST', "/api/rider/deliveries/{$orderId}/accept")->assertOk();
        $this->asRider($token, 'POST', '/api/rider/position', ['lat' => 16.4023, 'lng' => 120.5960])->assertOk();

        // Ten minutes in a dead spot. "Last seen here, 10 minutes ago" is a
        // far more useful answer to a waiting customer than an empty map.
        $rider->forceFill(['position_updated_at' => now()->subMinutes(10)])->save();

        $position = $this->getJson("/api/online-orders/{$orderId}")
            ->assertOk()
            ->assertJsonPath('riderPosition.stale', true)
            ->json('riderPosition');

        $this->assertGreaterThan(500, $position['ageSeconds']);
        $this->assertEquals(16.4023, $position['lat']);

        $this->assertFalse(Order::query()->findOrFail($orderId)->rider->isReportingPosition());
    }
}
