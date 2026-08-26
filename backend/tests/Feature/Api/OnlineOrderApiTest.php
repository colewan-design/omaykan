<?php

namespace Tests\Feature\Api;

use App\Events\OrderPlaced;
use App\Models\InventoryLevel;
use App\Models\Order;
use App\Models\Organization;
use App\Models\Payment;
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

    // -- Confirming the cash changed hands --------------------------------------
    //
    // There is no gateway, so somebody has to say so. The seller can, from the
    // dashboard; these cover the customer doing it from their own order page,
    // which is the only side present when a rider takes the cash at the door.

    private function placeOrder(): Order
    {
        $this->seed();
        Event::fake([OrderPlaced::class]);

        $this->postJson('/api/online-orders', $this->payload())->assertCreated();

        return Order::query()->firstOrFail();
    }

    public function test_a_customer_can_confirm_they_paid(): void
    {
        $order = $this->placeOrder();
        $this->assertSame('unpaid', $order->payment_status);

        $this->postJson("/api/online-orders/{$order->id}/confirm-payment")
            ->assertOk()
            ->assertJsonPath('paymentStatus', 'paid')
            ->assertJsonPath('paymentConfirmedBy', 'customer');

        $order->refresh();

        $this->assertSame('paid', $order->payment_status);
        $this->assertNotNull($order->payment_confirmed_at);
        $this->assertSame('customer', $order->payment_confirmed_by_role);
        // A shopper is not a user of the merchant's organization.
        $this->assertNull($order->payment_confirmed_by_user_id);
    }

    public function test_confirming_payment_records_it_in_the_merchants_cash_ledger(): void
    {
        $order = $this->placeOrder();

        $this->postJson("/api/online-orders/{$order->id}/confirm-payment")->assertOk();

        $payment = Payment::query()->where('order_id', $order->id)->firstOrFail();

        $this->assertSame($order->total_cents, (int) $payment->amount_cents);
        $this->assertSame('cash', $payment->payment_method);
    }

    public function test_confirming_twice_does_not_pay_the_order_twice(): void
    {
        $order = $this->placeOrder();

        $this->postJson("/api/online-orders/{$order->id}/confirm-payment")->assertOk();
        $confirmedAt = $order->fresh()->payment_confirmed_at;

        $this->postJson("/api/online-orders/{$order->id}/confirm-payment")
            ->assertOk()
            ->assertJsonPath('paymentStatus', 'paid');

        $this->assertSame(1, Payment::query()->where('order_id', $order->id)->count());
        $this->assertEquals($confirmedAt, $order->fresh()->payment_confirmed_at);
    }

    public function test_a_customer_confirmation_does_not_overwrite_the_sellers(): void
    {
        $order = $this->placeOrder();

        // However it got there — the seller settled it at the till first.
        $order->forceFill([
            'payment_status' => 'paid',
            'payment_confirmed_at' => now(),
            'payment_confirmed_by_role' => 'seller',
        ])->save();

        $this->postJson("/api/online-orders/{$order->id}/confirm-payment")->assertOk();

        $this->assertSame('seller', $order->fresh()->payment_confirmed_by_role);
    }

    public function test_a_register_sale_cannot_be_confirmed_through_the_public_endpoint(): void
    {
        $order = $this->placeOrder();
        $order->forceFill(['channel' => 'pos'])->save();

        $this->postJson("/api/online-orders/{$order->id}/confirm-payment")->assertNotFound();

        $this->assertSame('unpaid', $order->fresh()->payment_status);
    }

    public function test_the_tracked_order_says_when_nobody_has_confirmed_yet(): void
    {
        $order = $this->placeOrder();

        $this->getJson("/api/online-orders/{$order->id}")
            ->assertOk()
            ->assertJsonPath('paymentStatus', 'unpaid')
            ->assertJsonPath('paymentConfirmedAt', null)
            ->assertJsonPath('paymentConfirmedBy', null);
    }
}
