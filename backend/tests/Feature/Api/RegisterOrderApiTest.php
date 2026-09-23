<?php

namespace Tests\Feature\Api;

use App\Models\InventoryLevel;
use App\Models\LoyaltyEntry;
use App\Models\LoyaltyProgram;
use App\Models\Order;
use App\Models\Organization;
use App\Models\PosCustomer;
use App\Models\Product;
use App\Models\PromoCode;
use App\Models\StoreMembership;
use App\Models\User;
use App\Services\Loyalty\Loyalty;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Illuminate\Support\Str;
use Tests\Concerns\SignsInStaff;
use Tests\TestCase;

/**
 * The till's sales, sent before the cashier finishes them — recorded, or
 * refused with a reason — and its voids. See RegisterOrderController.
 */
class RegisterOrderApiTest extends TestCase
{
    use RefreshDatabase, SignsInStaff;

    private function org(): Organization
    {
        return Organization::query()->where('slug', 'demo-coffee')->firstOrFail();
    }

    private function espresso(): Product
    {
        return Product::query()->where('sku', 'ESP-0001')->firstOrFail();
    }

    private function stock(): float
    {
        return (float) InventoryLevel::query()->where('product_id', $this->espresso()->id)->value('qty_on_hand');
    }

    private function staffWithRole(string $role): string
    {
        $user = User::query()->create(['name' => 'Counter', 'username' => 'counter', 'password' => 'password', 'status' => 'active']);
        StoreMembership::query()->create([
            'store_id' => $this->org()->stores()->firstOrFail()->id, 'user_id' => $user->id, 'membership_role' => $role,
        ]);

        return $this->staffToken('counter');
    }

    /**
     * Two espressos at ₱120 with `$discountCents` off, VAT 12% on the rest.
     *
     * @param  list<array<string, mixed>>  $discounts
     */
    private function sale(int $discountCents = 0, array $discounts = [], array $order = []): array
    {
        $tax = (int) round((24000 - $discountCents) * 0.12);

        return [
            'order' => array_merge([
                'id' => (string) Str::uuid(),
                'ticketNumber' => 'TKT-'.Str::random(6),
                'orderType' => 'takeaway',
                'subtotalCents' => 24000,
                'discountCents' => $discountCents,
                'taxCents' => $tax,
                'totalCents' => 24000 - $discountCents + $tax,
            ], $order),
            'items' => [[
                'productId' => $this->espresso()->id,
                'productName' => 'Espresso',
                'quantity' => 2,
                'unitPriceCents' => 12000,
                'lineTotalCents' => 24000,
                'taxRate' => 0.12,
            ]],
            'discounts' => $discounts,
            'payments' => [['paymentMethod' => 'cash', 'amountCents' => 24000 - $discountCents + $tax]],
        ];
    }

    private function send(string $token, array $sale)
    {
        return $this->withToken($token)->postJson('/api/register/orders', $sale);
    }

    // -- Recording ------------------------------------------------------------

    public function test_a_sale_is_recorded_and_takes_its_stock_once(): void
    {
        $this->seed();
        $token = $this->staffToken();
        $before = $this->stock();
        $sale = $this->sale();

        $this->send($token, $sale)
            ->assertCreated()
            ->assertJsonPath('orderId', $sale['order']['id']);

        $order = Order::query()->findOrFail($sale['order']['id']);
        $this->assertSame(26880, $order->total_cents);
        $this->assertSame($before - 2, $this->stock());

        // Retried after a lost response: answered, not recorded twice.
        $this->send($token, $sale)->assertOk();
        $this->assertSame(1, Order::query()->count());
        $this->assertSame($before - 2, $this->stock());
    }

    public function test_totals_that_do_not_add_up_are_refused_and_nothing_is_kept(): void
    {
        $this->seed();
        $before = $this->stock();

        $this->send($this->staffToken(), $this->sale(order: ['totalCents' => 100]))
            ->assertUnprocessable()
            ->assertJsonValidationErrors('order');

        $this->assertSame(0, Order::withTrashed()->count());
        $this->assertSame($before, $this->stock());
    }

    public function test_a_discount_over_the_cashiers_limit_is_refused_with_what_to_do(): void
    {
        $this->seed();
        $discount = [['kind' => 'manual', 'amountCents' => 2400, 'percent' => 10, 'reason' => 'Regular']];

        $this->send($this->staffWithRole('cashier'), $this->sale(2400, $discount))
            ->assertUnprocessable()
            ->assertJsonPath('errors.order.0', 'Your role cannot give discounts. Ask a manager to sign in and apply it.');

        $this->assertSame(0, Order::query()->count());

        // The owner can give it.
        $this->send($this->staffToken(), $this->sale(2400, $discount))->assertCreated();
    }

    public function test_a_promo_code_used_up_since_the_check_is_refused(): void
    {
        $this->seed();
        $promo = PromoCode::query()->create([
            'organization_id' => $this->org()->id, 'code' => 'ONCE', 'kind' => PromoCode::KIND_AMOUNT,
            'value' => 2400, 'channel' => PromoCode::CHANNEL_BOTH, 'max_redemptions' => 1,
        ]);
        $discount = [['kind' => 'promo', 'amountCents' => 2400, 'promoCodeId' => $promo->id]];
        $token = $this->staffToken();

        $this->send($token, $this->sale(2400, $discount))->assertCreated();

        $this->send($token, $this->sale(2400, $discount))
            ->assertUnprocessable()
            ->assertJsonPath('errors.order.0', 'ONCE has been used up.');

        $this->assertSame(1, $promo->fresh()->redemptionCount());
    }

    public function test_points_the_customer_does_not_have_are_refused(): void
    {
        $this->seed();
        LoyaltyProgram::query()->create([
            'organization_id' => $this->org()->id, 'enabled' => true,
            'spend_cents_per_point' => 10000, 'point_value_cents' => 100, 'min_redeem_points' => 1,
        ]);
        $customer = PosCustomer::query()->create([
            'organization_id' => $this->org()->id, 'name' => 'Rosa', 'loyalty_consent_at' => now(),
        ]);

        $this->send($this->staffToken(), $this->sale(
            500,
            [['kind' => 'loyalty', 'amountCents' => 500, 'points' => 5]],
            ['customerId' => $customer->id],
        ))
            ->assertUnprocessable()
            ->assertJsonPath('errors.order.0', "Rosa doesn't have 5 points to spend.");

        $this->assertSame(0, LoyaltyEntry::query()->count());
    }

    public function test_a_sales_stock_sent_again_by_an_old_till_is_not_taken_twice(): void
    {
        $this->seed();
        $token = $this->staffToken();
        $before = $this->stock();
        $sale = $this->sale();

        $this->send($token, $sale)->assertCreated();

        // What an outbox from before this change still carries.
        $this->withToken($token)->postJson('/api/sync/push', [
            'organizationId' => $this->org()->id,
            'storeId' => $this->org()->stores()->firstOrFail()->id,
            'events' => [[
                'id' => (string) Str::uuid(), 'entityType' => 'inventory_adjustment', 'entityId' => (string) Str::uuid(),
                'operation' => 'upsert', 'occurredAt' => now()->toIso8601String(),
                'payload' => [
                    'productId' => $this->espresso()->id, 'quantityDelta' => -2, 'adjustmentType' => 'sale',
                    'orderId' => $sale['order']['id'], 'reason' => 'order:TKT',
                ],
            ]],
        ])->assertOk();

        $this->assertSame($before - 2, $this->stock());
    }

    // -- Voiding --------------------------------------------------------------

    public function test_a_void_takes_the_sale_off_the_books_and_puts_the_stock_back(): void
    {
        $this->seed();
        $token = $this->staffToken();
        $before = $this->stock();
        $sale = $this->sale();
        $id = $sale['order']['id'];
        $admin = User::query()->where('username', 'admin')->firstOrFail();

        $this->send($token, $sale)->assertCreated();

        $this->withToken($token)->postJson("/api/register/orders/{$id}/void", [
            'voidedByUserId' => $admin->id,
            'reason' => 'Rang up twice',
        ])->assertOk();

        $this->assertNull(Order::query()->find($id));
        $order = Order::withTrashed()->findOrFail($id);
        $this->assertSame($admin->id, $order->voided_by_user_id);
        $this->assertSame('Rang up twice', $order->void_reason);
        $this->assertSame($before, $this->stock());

        // Twice is harmless: the stock does not go back again.
        $this->withToken($token)->postJson("/api/register/orders/{$id}/void")->assertOk();
        $this->assertSame($before, $this->stock());

        // And the sale cannot come back.
        $this->send($token, $sale)->assertOk();
        $this->assertTrue(Order::withTrashed()->findOrFail($id)->trashed());
    }

    public function test_a_voided_sale_gives_back_its_points(): void
    {
        $this->seed();
        LoyaltyProgram::query()->create([
            'organization_id' => $this->org()->id, 'enabled' => true,
            'spend_cents_per_point' => 10000, 'point_value_cents' => 100, 'min_redeem_points' => 1,
        ]);
        $customer = PosCustomer::query()->create([
            'organization_id' => $this->org()->id, 'name' => 'Rosa', 'loyalty_consent_at' => now(),
        ]);
        $token = $this->staffToken();
        $sale = $this->sale(order: ['customerId' => $customer->id]);

        $this->send($token, $sale)->assertCreated();
        $this->assertSame(2, app(Loyalty::class)->balance($customer));

        $this->withToken($token)->postJson("/api/register/orders/{$sale['order']['id']}/void")->assertOk();
        $this->assertSame(0, app(Loyalty::class)->balance($customer));
    }

    public function test_only_the_owner_can_void(): void
    {
        $this->seed();
        $sale = $this->sale();
        $this->send($this->staffToken(), $sale)->assertCreated();

        $this->withToken($this->staffWithRole('manager'))
            ->postJson("/api/register/orders/{$sale['order']['id']}/void")
            ->assertForbidden()
            ->assertJsonPath('message', 'Only the owner can void a sale.');

        $this->assertFalse(Order::withTrashed()->findOrFail($sale['order']['id'])->trashed());
    }

    public function test_voiding_a_sale_the_server_never_had_is_a_404(): void
    {
        $this->seed();

        $this->withToken($this->staffToken())
            ->postJson('/api/register/orders/'.Str::uuid().'/void')
            ->assertNotFound();
    }
}
