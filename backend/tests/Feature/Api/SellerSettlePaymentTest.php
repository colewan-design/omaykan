<?php

namespace Tests\Feature\Api;

use App\Models\Order;
use App\Models\Payment;
use App\Models\Shift;
use App\Models\Store;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Tests\TestCase;

/**
 * Settling an order twice must not record the payment twice.
 *
 * Until 2026-08-27 it did: `settlePayment` had no guard on `payment_status`,
 * returned 200 both times, and created a second Payment row for the full total.
 * That is a money bug rather than a stray row — ShiftController sums this table
 * into the cash a drawer is expected to hold at close, so a duplicated cash
 * payment makes an honest till reconcile short by that amount and points the
 * discrepancy at whoever was on the counter. A double tap on a slow connection
 * was enough. See documentation/e2e-findings.md §6.1.
 */
class SellerSettlePaymentTest extends TestCase
{
    use RefreshDatabase;

    /** @return array{token: string, orderId: string, totalCents: int} */
    private function placedOrder(): array
    {
        $token = $this->postJson('/api/device-sessions', [
            'organizationSlug' => 'demo-coffee',
            'storeCode' => 'main',
            'pairingCode' => '123456',
            'deviceName' => 'Counter 1',
            'platform' => 'web',
        ])->json('token');

        $product = $this->getJson('/api/storefront/catalog?orgSlug=demo-coffee&storeCode=main')
            ->json('products.0');

        $order = $this->postJson('/api/online-orders', [
            'orgSlug' => 'demo-coffee',
            'storeCode' => 'main',
            'businessMode' => 'coffee-shop',
            'items' => [['productId' => $product['id'], 'quantity' => 1]],
            'guest' => ['name' => 'Buyer', 'phone' => '09170000000'],
            'fulfillment' => ['method' => 'pickup'],
            'paymentMethod' => 'cash',
        ])->assertCreated()->json();

        return ['token' => $token, 'orderId' => $order['orderId'], 'totalCents' => $order['totalCents']];
    }

    public function test_settling_once_records_exactly_one_payment(): void
    {
        $this->seed();
        ['token' => $token, 'orderId' => $id, 'totalCents' => $total] = $this->placedOrder();

        $this->withToken($token)
            ->postJson("/api/seller/online-orders/{$id}/settle-payment", [
                'paymentMethod' => 'cash',
                'tenderedCents' => $total,
            ])->assertOk();

        $this->assertSame(1, Payment::query()->where('order_id', $id)->count());
        $this->assertSame($total, (int) Payment::query()->where('order_id', $id)->sum('amount_cents'));
        $this->assertSame('paid', Order::query()->findOrFail($id)->payment_status);
    }

    public function test_settling_an_already_paid_order_is_refused(): void
    {
        $this->seed();
        ['token' => $token, 'orderId' => $id, 'totalCents' => $total] = $this->placedOrder();

        $this->withToken($token)
            ->postJson("/api/seller/online-orders/{$id}/settle-payment", [
                'paymentMethod' => 'cash',
                'tenderedCents' => $total,
            ])->assertOk();

        $this->withToken($token)
            ->postJson("/api/seller/online-orders/{$id}/settle-payment", [
                'paymentMethod' => 'cash',
                'tenderedCents' => $total,
            ])->assertStatus(422);
    }

    public function test_a_refused_second_settlement_leaves_the_money_untouched(): void
    {
        $this->seed();
        ['token' => $token, 'orderId' => $id, 'totalCents' => $total] = $this->placedOrder();

        foreach ([1, 2, 3] as $_) {
            $this->withToken($token)->postJson("/api/seller/online-orders/{$id}/settle-payment", [
                'paymentMethod' => 'cash',
                'tenderedCents' => $total,
            ]);
        }

        $this->assertSame(1, Payment::query()->where('order_id', $id)->count());
        $this->assertSame(
            $total,
            (int) Payment::query()->where('order_id', $id)->sum('amount_cents'),
            'A repeated settlement must not inflate what the drawer is expected to hold.',
        );
    }

    public function test_expected_cash_at_shift_close_matches_a_single_settlement(): void
    {
        $this->seed();
        ['token' => $token, 'orderId' => $id, 'totalCents' => $total] = $this->placedOrder();

        // The order has to fall inside the shift window for cashSalesForShift.
        $store = Store::query()->firstOrFail();
        $shift = Shift::query()->create([
            'id' => (string) str()->uuid(),
            'organization_id' => $store->organization_id,
            'store_id' => $store->id,
            'opened_at' => now()->subHour(),
            'opening_cash_cents' => 100000,
        ]);

        foreach ([1, 2] as $_) {
            $this->withToken($token)->postJson("/api/seller/online-orders/{$id}/settle-payment", [
                'paymentMethod' => 'cash',
                'tenderedCents' => $total,
            ]);
        }

        Order::query()->whereKey($id)->update(['completed_at' => now()]);

        $cash = (int) Payment::query()
            ->where('store_id', $store->id)
            ->where('payment_method', 'cash')
            ->whereHas('order', fn ($q) => $q->where('completed_at', '>=', $shift->opened_at))
            ->sum('amount_cents');

        $this->assertSame(
            $total,
            $cash,
            'Expected cash counted only the one real payment, not a duplicate.',
        );
    }
}
