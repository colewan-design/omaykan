<?php

namespace Tests\Feature\Api;

use App\Events\OrderDeliveryUpdated;
use App\Models\Order;
use App\Models\Product;
use App\Models\Rider;
use Illuminate\Foundation\Testing\DatabaseMigrations;
use Tests\Concerns\ActsAsShopper;
use Illuminate\Support\Facades\Event;
use Tests\TestCase;

/**
 * The job board and the road: what a rider can see, take, and move.
 *
 * DatabaseMigrations rather than RefreshDatabase, for the reason
 * SellerOrderApiTest gives — the broadcasts ride DB::afterCommit, which never
 * fires inside a transaction that is rolled back.
 */
class RiderDeliveryApiTest extends TestCase
{
    use ActsAsShopper, DatabaseMigrations;

    private function approvedRider(string $email = 'jun@example.com', string $name = 'Jun Dela Cruz'): string
    {
        $rider = Rider::query()->create([
            'name' => $name,
            'email' => $email,
            'phone' => '0917 555 0101',
            'password' => 'ride-with-me-1',
            'license_number' => 'N01-23-456789',
            'plate_number' => 'NBC 1234',
            'license_image_path' => 'rider-documents/license.jpg',
            'plate_image_path' => 'rider-documents/plate.jpg',
            'status' => Rider::STATUS_APPROVED,
        ]);

        return $rider->createToken('rider-portal', ['rider'])->plainTextToken;
    }

    private function deviceToken(): string
    {
        return $this->postJson('/api/device-sessions', [
            'organizationSlug' => 'demo-coffee',
            'storeCode' => 'main',
            'pairingCode' => '123456',
            'deviceName' => 'Counter 1',
            'platform' => 'web',
            'appVersion' => '0.1.0',
        ])->assertOk()->json('token');
    }

    private function placeDeliveryOrder(): string
    {
        $product = Product::query()->where('sku', 'ESP-0001')->firstOrFail();

        return $this->asShopper()->postJson('/api/online-orders', [
            'orgSlug' => 'demo-coffee',
            'storeCode' => 'main',
            'businessMode' => 'coffee-shop',
            'items' => [['productId' => $product->id, 'quantity' => 2]],
            'guest' => ['name' => 'Maria Santos', 'phone' => '09171234567'],
            'fulfillment' => ['method' => 'delivery', 'address' => '12 Session Road, Sto. Tomas, Baguio City'],
        ])->assertCreated()->json('orderId');
    }

    /** @return \Illuminate\Testing\TestResponse */
    private function asRider(string $token, string $method, string $uri, array $body = [])
    {
        // Guards cache the identity they resolved, and a test reuses one
        // container across requests — so each hop starts clean.
        $this->app['auth']->forgetGuards();

        return $this->withHeader('Authorization', "Bearer {$token}")
            ->json($method, $uri, $body);
    }

    public function test_the_board_shows_unclaimed_deliveries_without_the_customers_details(): void
    {
        $this->seed();
        $orderId = $this->placeDeliveryOrder();

        $offer = $this->asRider($this->approvedRider(), 'GET', '/api/rider/board')
            ->assertOk()
            ->assertJsonPath('orders.0.id', $orderId)
            ->assertJsonPath('orders.0.pickup.storeName', 'Main Branch')
            // Enough of the address to judge the trip, no house number.
            ->assertJsonPath('orders.0.dropoffArea', 'Sto. Tomas, Baguio City')
            ->assertJsonPath('orders.0.itemCount', 2)
            ->json('orders.0');

        // Who lives there is not on offer — that arrives with the claim.
        $this->assertArrayNotHasKey('customerName', $offer);
        $this->assertArrayNotHasKey('customerPhone', $offer);
        $this->assertArrayNotHasKey('deliveryAddress', $offer);

        // Unpaid on placement, so the rider is told there is money to collect.
        $this->assertSame('unpaid', $offer['paymentStatus']);
        $this->assertGreaterThan(0, $offer['collectCents']);
    }

    public function test_pickup_orders_never_reach_the_board(): void
    {
        $this->seed();
        $product = Product::query()->where('sku', 'ESP-0001')->firstOrFail();

        $this->asShopper()->postJson('/api/online-orders', [
            'orgSlug' => 'demo-coffee',
            'storeCode' => 'main',
            'businessMode' => 'coffee-shop',
            'items' => [['productId' => $product->id, 'quantity' => 1]],
            'guest' => ['name' => 'Maria Santos', 'phone' => '09171234567'],
            'fulfillment' => ['method' => 'pickup'],
        ])->assertCreated();

        $this->asRider($this->approvedRider(), 'GET', '/api/rider/board')
            ->assertOk()
            ->assertJsonCount(0, 'orders');
    }

    public function test_accepting_claims_the_order_and_hands_over_the_delivery_details(): void
    {
        $this->seed();
        $orderId = $this->placeDeliveryOrder();
        Event::fake([OrderDeliveryUpdated::class]);

        $this->asRider($this->approvedRider(), 'POST', "/api/rider/deliveries/{$orderId}/accept")
            ->assertOk()
            ->assertJsonPath('order.deliveryStage', 'assigned')
            ->assertJsonPath('order.deliveryAddress', '12 Session Road, Sto. Tomas, Baguio City')
            ->assertJsonPath('order.customerName', 'Maria Santos')
            ->assertJsonPath('order.customerPhone', '09171234567');

        // The shop's dashboard and the customer's tracking page read the free
        // text columns, so claiming has to fill them in from the account.
        $order = Order::query()->findOrFail($orderId);
        $this->assertSame('Jun Dela Cruz', $order->rider_name);
        $this->assertSame('0917 555 0101', $order->rider_phone);
        $this->assertNotNull($order->rider_id);
        $this->assertNotNull($order->rider_accepted_at);

        Event::assertDispatched(OrderDeliveryUpdated::class);
    }

    public function test_a_claimed_order_leaves_the_board_and_a_second_rider_is_told(): void
    {
        $this->seed();
        $orderId = $this->placeDeliveryOrder();
        $first = $this->approvedRider();
        $second = $this->approvedRider('mika@example.com', 'Mika Ramos');

        $this->asRider($first, 'POST', "/api/rider/deliveries/{$orderId}/accept")->assertOk();

        $this->asRider($second, 'GET', '/api/rider/board')
            ->assertOk()
            ->assertJsonCount(0, 'orders');

        // The race is the normal case, not the exotic one: the loser gets a
        // 409 rather than silently stealing the job.
        $this->asRider($second, 'POST', "/api/rider/deliveries/{$orderId}/accept")
            ->assertStatus(409);

        $this->assertSame('Jun Dela Cruz', Order::query()->findOrFail($orderId)->rider_name);
    }

    public function test_the_stages_run_in_order_and_only_for_the_rider_who_claimed_it(): void
    {
        $this->seed();
        $orderId = $this->placeDeliveryOrder();
        $mine = $this->approvedRider();
        $other = $this->approvedRider('mika@example.com', 'Mika Ramos');

        $this->asRider($mine, 'POST', "/api/rider/deliveries/{$orderId}/accept")->assertOk();

        // Delivered before picked up is a typo, not a workflow.
        $this->asRider($mine, 'POST', "/api/rider/deliveries/{$orderId}/stage", ['stage' => 'delivered'])
            ->assertStatus(422);

        $this->asRider($mine, 'POST', "/api/rider/deliveries/{$orderId}/stage", ['stage' => 'picked_up'])
            ->assertOk()
            ->assertJsonPath('order.deliveryStage', 'picked_up');

        // Somebody else's delivery is none of this rider's business.
        $this->asRider($other, 'POST', "/api/rider/deliveries/{$orderId}/stage", ['stage' => 'delivered'])
            ->assertForbidden();

        $this->asRider($mine, 'POST', "/api/rider/deliveries/{$orderId}/stage", ['stage' => 'delivered'])
            ->assertOk()
            ->assertJsonPath('order.deliveryStage', 'delivered');

        $mineList = $this->asRider($mine, 'GET', '/api/rider/deliveries')->assertOk();
        $mineList->assertJsonCount(0, 'active');
        $mineList->assertJsonCount(1, 'completed');
    }

    public function test_a_rider_can_give_back_a_job_until_they_pick_it_up(): void
    {
        $this->seed();
        $orderId = $this->placeDeliveryOrder();
        $token = $this->approvedRider();

        $this->asRider($token, 'POST', "/api/rider/deliveries/{$orderId}/accept")->assertOk();
        $this->asRider($token, 'POST', "/api/rider/deliveries/{$orderId}/release")->assertOk();

        // Back on the board, and with no trace of the rider left on the order.
        $this->asRider($token, 'GET', '/api/rider/board')
            ->assertOk()
            ->assertJsonPath('orders.0.id', $orderId);

        $order = Order::query()->findOrFail($orderId);
        $this->assertNull($order->rider_id);
        $this->assertNull($order->rider_name);
        $this->assertSame('pending', $order->delivery_stage);

        // Once the food is in the bag, handing it back is a phone call.
        $this->asRider($token, 'POST', "/api/rider/deliveries/{$orderId}/accept")->assertOk();
        $this->asRider($token, 'POST', "/api/rider/deliveries/{$orderId}/stage", ['stage' => 'picked_up'])->assertOk();
        $this->asRider($token, 'POST', "/api/rider/deliveries/{$orderId}/release")->assertStatus(422);
    }

    public function test_the_shop_sees_the_rider_the_portal_assigned(): void
    {
        $this->seed();
        $orderId = $this->placeDeliveryOrder();

        $this->asRider($this->approvedRider(), 'POST', "/api/rider/deliveries/{$orderId}/accept")->assertOk();

        // Nothing about SellerOrderController changed — the columns the
        // dashboard already reads are the ones the portal writes.
        $this->app['auth']->forgetGuards();
        $this->withHeader('Authorization', "Bearer {$this->deviceToken()}")
            ->getJson('/api/seller/online-orders')
            ->assertOk()
            ->assertJsonPath('orders.0.riderName', 'Jun Dela Cruz')
            ->assertJsonPath('orders.0.deliveryStage', 'assigned');
    }

    public function test_a_rider_cannot_claim_another_shops_order_through_the_seller_api(): void
    {
        $this->seed();
        $orderId = $this->placeDeliveryOrder();

        // The rider API is the only door a rider has. The seller's version of
        // the same action is device-authenticated and stays that way.
        $this->asRider($this->approvedRider(), 'POST', "/api/seller/online-orders/{$orderId}/rider", [
            'riderName' => 'Jun Dela Cruz',
        ])->assertForbidden();
    }
}
