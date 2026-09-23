<?php

namespace Tests\Feature\Api;

use App\Events\OrderDeliveryUpdated;
use App\Events\OrderStatusChanged;
use App\Models\Order;
use App\Models\Organization;
use App\Models\Product;
use App\Models\Store;
use Illuminate\Foundation\Testing\DatabaseMigrations;
use Illuminate\Support\Facades\Event;
use Tests\Concerns\SignsInStaff;
use Tests\TestCase;

/**
 * The seller's side of a storefront order — the endpoints behind the dashboard.
 *
 * DatabaseMigrations rather than RefreshDatabase, for the same reason
 * OnlineOrderApiTest gives: the broadcasts ride DB::afterCommit, which never
 * fires inside a transaction that is rolled back.
 */
class SellerOrderApiTest extends TestCase
{
    use SignsInStaff, DatabaseMigrations;

    /** Places a real storefront order so the seller has something to act on. */
    private function placeDeliveryOrder(): string
    {
        $product = Product::query()->where('sku', 'ESP-0001')->firstOrFail();

        return $this->postJson('/api/online-orders', [
            'orgSlug' => 'demo-coffee',
            'storeCode' => 'main',
            'businessMode' => 'coffee-shop',
            'items' => [['productId' => $product->id, 'quantity' => 1]],
            'guest' => ['name' => 'Maria Santos', 'phone' => '09171234567'],
            'fulfillment' => ['method' => 'delivery', 'address' => '12 Session Road, Baguio'],
        ])->assertCreated()->json('orderId');
    }

    public function test_device_sees_its_own_stores_online_orders(): void
    {
        $this->seed();
        $orderId = $this->placeDeliveryOrder();

        $response = $this->withHeader('Authorization', "Bearer {$this->staffToken()}")
            ->getJson('/api/seller/online-orders')
            ->assertOk();

        $response
            ->assertJsonPath('orders.0.id', $orderId)
            ->assertJsonPath('orders.0.channel', 'online')
            ->assertJsonPath('orders.0.paymentStatus', 'unpaid')
            ->assertJsonPath('orders.0.fulfillmentMethod', 'delivery')
            ->assertJsonPath('orders.0.deliveryStage', 'pending')
            ->assertJsonPath('orders.0.customerName', 'Maria Santos')
            // The shape packages/data expects, matching mapFsOrder's output.
            ->assertJsonStructure([
                'orders' => [['id', 'ticketNumber', 'status', 'totalCents', 'items', 'riderName', 'riderPhone']],
            ]);
    }

    public function test_naming_a_rider_assigns_the_order_and_broadcasts(): void
    {
        $this->seed();
        $orderId = $this->placeDeliveryOrder();
        Event::fake([OrderDeliveryUpdated::class]);

        $this->withHeader('Authorization', "Bearer {$this->staffToken()}")
            ->postJson("/api/seller/online-orders/{$orderId}/rider", [
                'riderName' => 'Jun Dela Cruz',
                'riderPhone' => '0917 555 0101',
            ])
            ->assertOk()
            ->assertJsonPath('order.riderName', 'Jun Dela Cruz')
            ->assertJsonPath('order.riderPhone', '0917 555 0101')
            ->assertJsonPath('order.deliveryStage', 'assigned');

        Event::assertDispatched(
            OrderDeliveryUpdated::class,
            fn (OrderDeliveryUpdated $event) => $event->order->id === $orderId && $event->previousStage === 'pending',
        );
    }

    public function test_reassigning_a_rider_does_not_walk_the_stage_backwards(): void
    {
        $this->seed();
        $orderId = $this->placeDeliveryOrder();
        $token = $this->staffToken();

        $this->withHeader('Authorization', "Bearer {$token}")
            ->postJson("/api/seller/online-orders/{$orderId}/rider", ['riderName' => 'Jun'])
            ->assertOk();

        $this->withHeader('Authorization', "Bearer {$token}")
            ->postJson("/api/seller/online-orders/{$orderId}/delivery-stage", ['stage' => 'picked_up'])
            ->assertOk()
            ->assertJsonPath('order.deliveryStage', 'picked_up');

        // The first rider broke down and a second took over mid-run: the
        // customer's tracking must not drop back to "rider assigned".
        $this->withHeader('Authorization', "Bearer {$token}")
            ->postJson("/api/seller/online-orders/{$orderId}/rider", ['riderName' => 'Ana'])
            ->assertOk()
            ->assertJsonPath('order.riderName', 'Ana')
            ->assertJsonPath('order.deliveryStage', 'picked_up');
    }

    public function test_pickup_orders_have_no_rider_to_assign(): void
    {
        $this->seed();
        $product = Product::query()->where('sku', 'ESP-0001')->firstOrFail();

        $orderId = $this->postJson('/api/online-orders', [
            'orgSlug' => 'demo-coffee',
            'storeCode' => 'main',
            'businessMode' => 'coffee-shop',
            'items' => [['productId' => $product->id, 'quantity' => 1]],
            'guest' => ['name' => 'Maria Santos', 'phone' => '09171234567'],
            'fulfillment' => ['method' => 'pickup'],
        ])->assertCreated()->json('orderId');

        $this->withHeader('Authorization', "Bearer {$this->staffToken()}")
            ->postJson("/api/seller/online-orders/{$orderId}/rider", ['riderName' => 'Jun'])
            ->assertStatus(422);
    }

    public function test_status_change_broadcasts_and_completes_the_order(): void
    {
        $this->seed();
        $orderId = $this->placeDeliveryOrder();
        Event::fake([OrderStatusChanged::class]);

        $this->withHeader('Authorization', "Bearer {$this->staffToken()}")
            ->postJson("/api/seller/online-orders/{$orderId}/status", ['status' => 'served'])
            ->assertOk()
            ->assertJsonPath('order.status', 'served');

        $this->assertNotNull(Order::query()->findOrFail($orderId)->completed_at);
        Event::assertDispatched(OrderStatusChanged::class);
    }

    public function test_settling_payment_records_who_confirmed_it(): void
    {
        $this->seed();
        $orderId = $this->placeDeliveryOrder();
        $userId = (string) str()->uuid();

        $this->withHeader('Authorization', "Bearer {$this->staffToken()}")
            ->postJson("/api/seller/online-orders/{$orderId}/settle-payment", [
                'paymentMethod' => 'cash',
                'tenderedCents' => 20000,
                'changeCents' => 500,
                'userId' => $userId,
            ])
            ->assertOk()
            ->assertJsonPath('order.paymentStatus', 'paid')
            ->assertJsonPath('order.paymentMethod', 'cash')
            ->assertJsonPath('order.paymentConfirmedByUserId', $userId);

        $this->assertNotNull(Order::query()->findOrFail($orderId)->payment_confirmed_at);
    }

    public function test_a_device_cannot_touch_another_stores_order(): void
    {
        $this->seed();
        $orderId = $this->placeDeliveryOrder();

        // A second store in its own organization, with an owner of its own.
        $otherToken = $this->staffTokenForNewTenant();

        $this->withHeader('Authorization', "Bearer {$otherToken}")
            ->postJson("/api/seller/online-orders/{$orderId}/rider", ['riderName' => 'Snooper'])
            ->assertForbidden();

        $this->withHeader('Authorization', "Bearer {$otherToken}")
            ->getJson('/api/seller/online-orders')
            ->assertOk()
            ->assertJsonCount(0, 'orders');
    }

    public function test_the_endpoints_require_an_authenticated_device(): void
    {
        $this->seed();
        $orderId = $this->placeDeliveryOrder();

        $this->getJson('/api/seller/online-orders')->assertUnauthorized();
        $this->postJson("/api/seller/online-orders/{$orderId}/rider", ['riderName' => 'Jun'])->assertUnauthorized();
    }
}
