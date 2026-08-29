<?php

namespace Tests\Feature\Api;

use App\Events\OrderDeliveryUpdated;
use App\Events\OrderPlaced;
use App\Models\InventoryLevel;
use App\Models\Order;
use App\Models\Organization;
use App\Models\Product;
use App\Models\Store;
use Illuminate\Foundation\Testing\DatabaseMigrations;
use Illuminate\Support\Facades\Event;
use Tests\TestCase;

/**
 * Covers api/create-online-order.ts's replacement.
 *
 * Uses DatabaseMigrations rather than RefreshDatabase on purpose: RefreshDatabase
 * wraps each test in a transaction that never commits, so the DB::afterCommit
 * callback carrying OrderPlaced would never run and the broadcast assertions
 * would pass for the wrong reason.
 */
class OnlineOrderApiTest extends TestCase
{
    use DatabaseMigrations;

    /** Session Road, Baguio — matches the seeded store pin. */
    private const STORE_LAT = 16.4123;

    private const STORE_LNG = 120.5960;

    private function payload(array $overrides = []): array
    {
        $product = Product::query()->where('sku', 'ESP-0001')->firstOrFail();

        return array_replace_recursive([
            'orgSlug' => 'demo-coffee',
            'storeCode' => 'main',
            'businessMode' => 'coffee-shop',
            'items' => [
                ['productId' => $product->id, 'quantity' => 2],
            ],
            'guest' => ['name' => 'Maria Santos', 'phone' => '09171234567'],
            'fulfillment' => ['method' => 'pickup'],
        ], $overrides);
    }

    public function test_pickup_order_is_recorded_and_priced_from_the_merchants_own_records(): void
    {
        $this->seed();
        Event::fake([OrderPlaced::class]);

        $response = $this->postJson('/api/online-orders', $this->payload());

        // Espresso is ₱120.00 at 12% tax; two of them, no delivery fee.
        $response
            ->assertCreated()
            ->assertJsonPath('totalCents', 26880)
            ->assertJsonPath('deliveryFeeCents', 0)
            ->assertJsonStructure(['orderId', 'ticketNumber', 'totalCents', 'deliveryFeeCents']);

        $order = Order::query()->firstOrFail();

        $this->assertSame('online', $order->channel);
        $this->assertSame('preparing', $order->order_status);
        $this->assertSame('unpaid', $order->payment_status);
        $this->assertSame('pickup', $order->fulfillment_method);
        $this->assertSame(24000, $order->subtotal_cents);
        $this->assertSame(2880, $order->tax_cents);
        $this->assertSame('Maria Santos', $order->guest_contact['name']);
        $this->assertSame('09171234567', $order->guest_contact['phone']);
        // No till rang this up and it is not finished.
        $this->assertNull($order->device_id);
        $this->assertNull($order->completed_at);
        // Pickup orders have no rider stage.
        $this->assertNull($order->delivery_stage);

        $this->assertCount(1, $order->items);
        $this->assertSame(8, strlen($order->ticket_number));
    }

    public function test_placing_an_order_broadcasts_order_placed_after_the_commit(): void
    {
        $this->seed();
        Event::fake([OrderPlaced::class]);

        $this->postJson('/api/online-orders', $this->payload())->assertCreated();

        $order = Order::query()->firstOrFail();

        Event::assertDispatched(
            OrderPlaced::class,
            fn (OrderPlaced $event) => $event->order->id === $order->id,
        );
    }

    /**
     * The order is committed before anything is announced, and Laravel runs
     * after-commit callbacks outside the try/catch around the transaction — so
     * an unguarded failure here would 500 an order that exists, and the
     * customer would place it a second time.
     */
    public function test_a_failing_broadcast_does_not_fail_an_order_that_is_already_recorded(): void
    {
        $this->seed();

        Event::listen(OrderPlaced::class, function (): void {
            throw new \RuntimeException('queue is unreachable');
        });

        $this->postJson('/api/online-orders', $this->payload())->assertCreated();

        $this->assertSame(1, Order::query()->count());
    }

    /**
     * The id that took production down on 2026-08-27. `products.id` is a uuid
     * column, so a slug-shaped id from the bundled demo catalog reached the
     * database as one and was rejected at the type boundary — a 500, not a
     * miss. It must be a plain rejection the shopper can act on.
     *
     * The crash was on the database the backend ran on at the time, which
     * rejected the malformed uuid outright; MySQL stores the column as
     * char(36) and would simply fail to match. The validation is what makes
     * the answer the same either way.
     */
    public function test_a_product_id_that_is_not_a_uuid_is_rejected_rather_than_crashing(): void
    {
        $this->seed();
        Event::fake([OrderPlaced::class]);

        $response = $this->postJson('/api/online-orders', $this->payload([
            'items' => [
                ['productId' => 'sm-meat-master-beef-sukiyaki-cut-7m', 'quantity' => 1],
            ],
        ]));

        $response->assertStatus(422)->assertJsonValidationErrors('items.0.productId');
        $this->assertSame(0, Order::query()->count());
    }

    public function test_stock_is_decremented_and_an_adjustment_recorded(): void
    {
        $this->seed();
        Event::fake([OrderPlaced::class]);

        $this->postJson('/api/online-orders', $this->payload())->assertCreated();

        $order = Order::query()->firstOrFail();
        $product = Product::query()->where('sku', 'ESP-0001')->firstOrFail();

        $level = InventoryLevel::query()->where('product_id', $product->id)->firstOrFail();
        $this->assertEquals(98, $level->qty_on_hand);

        $this->assertDatabaseHas('inventory_adjustments', [
            'order_id' => $order->id,
            'product_id' => $product->id,
            'adjustment_type' => 'sale',
        ]);
    }

    public function test_delivery_fee_is_charged_by_distance_from_the_store_pin(): void
    {
        $this->seed();
        Event::fake([OrderPlaced::class]);

        // ~3.5km north of the store: past the 2km flag-down, so two started
        // extra kilometres at ₱15 each on top of the ₱49 base.
        $response = $this->postJson('/api/online-orders', $this->payload([
            'fulfillment' => [
                'method' => 'delivery',
                'address' => '12 Leonard Wood Road',
                'lat' => self::STORE_LAT + 0.031653,
                'lng' => self::STORE_LNG,
            ],
        ]));

        $response
            ->assertCreated()
            ->assertJsonPath('deliveryFeeCents', 7900)
            ->assertJsonPath('totalCents', 26880 + 7900);

        $order = Order::query()->firstOrFail();
        $this->assertSame('delivery', $order->fulfillment_method);
        $this->assertSame('pending', $order->delivery_stage);
        $this->assertEqualsWithDelta(3.5, $order->delivery_distance_km, 0.1);
    }

    public function test_delivery_without_coordinates_falls_back_to_the_flat_base_fee(): void
    {
        $this->seed();
        Event::fake([OrderPlaced::class]);

        $this->postJson('/api/online-orders', $this->payload([
            'fulfillment' => ['method' => 'delivery', 'address' => '12 Leonard Wood Road'],
        ]))
            ->assertCreated()
            ->assertJsonPath('deliveryFeeCents', 4900);
    }

    public function test_address_outside_the_delivery_radius_is_rejected(): void
    {
        $this->seed();

        // ~22km out, past the 15km ceiling.
        $this->postJson('/api/online-orders', $this->payload([
            'fulfillment' => [
                'method' => 'delivery',
                'address' => 'Somewhere far',
                'lat' => self::STORE_LAT + 0.2,
                'lng' => self::STORE_LNG,
            ],
        ]))
            ->assertStatus(422)
            ->assertJsonValidationErrors('fulfillment.address');

        $this->assertDatabaseCount('orders', 0);
    }

    public function test_a_lone_coordinate_is_rejected_rather_than_quietly_charging_the_flat_fee(): void
    {
        $this->seed();

        $this->postJson('/api/online-orders', $this->payload([
            'fulfillment' => [
                'method' => 'delivery',
                'address' => '12 Leonard Wood Road',
                'lat' => self::STORE_LAT,
            ],
        ]))
            ->assertStatus(422)
            ->assertJsonValidationErrors('fulfillment.lng');
    }

    public function test_order_beyond_available_stock_is_rejected(): void
    {
        $this->seed();

        $this->postJson('/api/online-orders', $this->payload([
            'items' => [['quantity' => 500]],
        ]))->assertStatus(422);

        $this->assertDatabaseCount('orders', 0);
    }

    public function test_product_outside_the_requested_business_mode_is_rejected(): void
    {
        $this->seed();

        Product::query()->where('sku', 'ESP-0001')->update(['business_modes' => json_encode(['grocery'])]);

        $this->postJson('/api/online-orders', $this->payload())->assertStatus(422);

        $this->assertDatabaseCount('orders', 0);
    }

    public function test_guest_must_leave_a_phone_or_an_email(): void
    {
        $this->seed();

        $payload = $this->payload();
        $payload['guest'] = ['name' => 'Maria Santos'];

        $this->postJson('/api/online-orders', $payload)
            ->assertStatus(422)
            ->assertJsonValidationErrors(['guest.phone', 'guest.email']);
    }

    public function test_unknown_store_is_a_404(): void
    {
        $this->seed();

        $this->postJson('/api/online-orders', $this->payload(['storeCode' => 'nope']))
            ->assertNotFound();
    }

    public function test_business_modes_without_online_ordering_are_rejected(): void
    {
        $this->seed();

        $this->postJson('/api/online-orders', $this->payload(['businessMode' => 'nail-salon']))
            ->assertStatus(422)
            ->assertJsonValidationErrors('businessMode');
    }

    public function test_customer_can_track_an_order_by_its_id(): void
    {
        $this->seed();
        Event::fake([OrderPlaced::class]);

        $placed = $this->postJson('/api/online-orders', $this->payload())->assertCreated()->json();

        $this->getJson("/api/online-orders/{$placed['orderId']}")
            ->assertOk()
            ->assertJsonPath('ticketNumber', $placed['ticketNumber'])
            ->assertJsonPath('status', 'preparing')
            ->assertJsonPath('paymentStatus', 'unpaid')
            ->assertJsonPath('totalCents', 26880)
            ->assertJsonPath('fulfillmentMethod', 'pickup')
            ->assertJsonCount(1, 'items')
            ->assertJsonPath('items.0.name', 'Espresso')
            ->assertJsonPath('items.0.lineTotalCents', 24000);
    }

    public function test_tracking_does_not_expose_the_customers_contact_details(): void
    {
        $this->seed();
        Event::fake([OrderPlaced::class]);

        $placed = $this->postJson('/api/online-orders', $this->payload())->assertCreated()->json();

        $body = $this->getJson("/api/online-orders/{$placed['orderId']}")->assertOk()->getContent();

        // The link is the capability; forwarding it must not hand over PII.
        $this->assertStringNotContainsString('09171234567', $body);
        $this->assertStringNotContainsString('Maria Santos', $body);
    }

    public function test_tracking_serves_the_riders_number_only_while_the_delivery_is_live(): void
    {
        $this->seed();
        Event::fake([OrderPlaced::class]);

        $placed = $this->postJson('/api/online-orders', $this->payload())->assertCreated()->json();
        $order = Order::query()->findOrFail($placed['orderId']);

        // A rider has it and the customer is about to meet them at the door.
        foreach (['assigned', 'picked_up'] as $stage) {
            $order->forceFill([
                'rider_name' => 'Ramon Cruz',
                'rider_phone' => '09181234567',
                'delivery_stage' => $stage,
            ])->save();

            $this->getJson("/api/online-orders/{$order->id}")
                ->assertOk()
                ->assertJsonPath('riderName', 'Ramon Cruz')
                ->assertJsonPath('riderPhone', '09181234567');
        }

        // Handed over. The tracking link outlives the delivery and is meant to
        // be forwarded, so the rider's mobile stops being served with it.
        $order->forceFill(['delivery_stage' => 'delivered'])->save();

        $body = $this->getJson("/api/online-orders/{$order->id}")
            ->assertOk()
            ->assertJsonPath('riderName', 'Ramon Cruz')
            ->assertJsonPath('riderPhone', null)
            ->getContent();

        $this->assertStringNotContainsString('09181234567', $body);
    }

    public function test_the_public_delivery_broadcast_drops_the_riders_number_after_handover(): void
    {
        $this->seed();
        Event::fake([OrderPlaced::class]);

        $placed = $this->postJson('/api/online-orders', $this->payload())->assertCreated()->json();
        $order = Order::query()->findOrFail($placed['orderId']);

        $order->forceFill([
            'rider_name' => 'Ramon Cruz',
            'rider_phone' => '09181234567',
            'delivery_stage' => 'picked_up',
        ])->save();

        // This payload rides the public order.{uuid} channel, not just the
        // store's private one — see OrderDeliveryUpdated::broadcastOn().
        $live = (new OrderDeliveryUpdated($order, 'assigned'))->broadcastWith();
        $this->assertSame('09181234567', $live['riderPhone']);

        $order->forceFill(['delivery_stage' => 'delivered'])->save();

        $done = (new OrderDeliveryUpdated($order, 'picked_up'))->broadcastWith();
        $this->assertNull($done['riderPhone']);
        $this->assertSame('Ramon Cruz', $done['riderName']);
    }

    public function test_tracking_an_unknown_order_is_a_404(): void
    {
        $this->seed();

        $this->getJson('/api/online-orders/'.str()->uuid())->assertNotFound();
    }

    public function test_register_orders_are_not_trackable_through_the_public_endpoint(): void
    {
        $this->seed();
        Event::fake([OrderPlaced::class]);

        $this->postJson('/api/online-orders', $this->payload())->assertCreated();

        // A till sale is not the customer's to look up by guessing a UUID.
        $order = Order::query()->firstOrFail();
        $order->forceFill(['channel' => 'pos'])->save();

        $this->getJson("/api/online-orders/{$order->id}")->assertNotFound();
    }

    public function test_orders_are_scoped_to_the_store_they_were_placed_at(): void
    {
        $this->seed();
        Event::fake([OrderPlaced::class]);

        $this->postJson('/api/online-orders', $this->payload())->assertCreated();

        $organization = Organization::query()->where('slug', 'demo-coffee')->firstOrFail();
        $store = Store::query()->where('organization_id', $organization->id)->where('code', 'main')->firstOrFail();

        $order = Order::query()->firstOrFail();
        $this->assertSame($store->id, $order->store_id);
        $this->assertSame($organization->id, $order->organization_id);
    }
}
