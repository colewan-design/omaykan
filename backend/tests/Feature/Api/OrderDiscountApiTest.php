<?php

namespace Tests\Feature\Api;

use App\Models\Order;
use App\Models\OrderDiscount;
use App\Models\Organization;
use App\Models\PosRole;
use App\Models\Product;
use App\Models\StoreMembership;
use App\Models\User;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Illuminate\Support\Str;
use Tests\Concerns\SignsInStaff;
use Tests\TestCase;

/**
 * Discounts on till sales, the checks the server makes on a sale, and the
 * tax-rate unit. See documentation/merchant-features.md §7.
 */
class OrderDiscountApiTest extends TestCase
{
    use RefreshDatabase, SignsInStaff;

    private function org(): Organization
    {
        return Organization::query()->where('slug', 'demo-coffee')->firstOrFail();
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
     * Two espressos at ₱120, 10% off: subtotal 24000, discount 2400, VAT 12%
     * on 21600 = 2592, total 24192 — what priceOrder produces.
     */
    private function sale(array $orderOverrides = [], ?array $discounts = null, ?string $orderId = null): array
    {
        $orderId ??= (string) Str::uuid();
        $espresso = Product::query()->where('sku', 'ESP-0001')->firstOrFail();

        return [
            'id' => (string) Str::uuid(),
            'entityType' => 'order',
            'entityId' => $orderId,
            'operation' => 'upsert',
            'occurredAt' => now()->toIso8601String(),
            'payload' => [
                'order' => array_merge([
                    'id' => $orderId,
                    'ticketNumber' => 'TKT-'.Str::random(6),
                    'orderType' => 'takeaway',
                    'subtotalCents' => 24000,
                    'discountCents' => 2400,
                    'taxCents' => 2592,
                    'totalCents' => 24192,
                ], $orderOverrides),
                'items' => [[
                    'productId' => $espresso->id,
                    'productName' => 'Espresso',
                    'quantity' => 2,
                    'unitPriceCents' => 12000,
                    'lineTotalCents' => 24000,
                    'taxRate' => 0.12,
                ]],
                'discounts' => $discounts ?? [[
                    'kind' => 'manual', 'amountCents' => 2400, 'percent' => 10, 'reason' => 'Regular',
                ]],
                'payments' => [],
            ],
        ];
    }

    private function push(string $token, array $events)
    {
        return $this->withToken($token)->postJson('/api/sync/push', [
            'organizationId' => $this->org()->id,
            'storeId' => $this->org()->stores()->firstOrFail()->id,
            'events' => $events,
        ])->assertOk();
    }

    /** A sale, the way the till sends one: to the register, not the outbox. */
    private function record(string $token, array $event)
    {
        return $this->withToken($token)->postJson('/api/register/orders', $event['payload']);
    }

    public function test_a_discounted_sale_is_recorded_with_who_gave_it_and_why(): void
    {
        $this->seed();
        $event = $this->sale();

        $this->record($this->staffToken(), $event)->assertCreated();

        $order = Order::query()->findOrFail($event['entityId']);
        $owner = User::query()->where('username', 'admin')->firstOrFail();

        $this->assertSame(2400, $order->discount_cents);
        // Was always null: the payload's missing userId overwrote this.
        $this->assertSame($owner->id, $order->user_id);

        $discount = OrderDiscount::query()->where('order_id', $order->id)->sole();
        $this->assertSame('manual', $discount->kind);
        $this->assertSame(10.0, $discount->percent);
        $this->assertSame('Regular', $discount->reason);
        $this->assertSame($owner->id, $discount->applied_by);

        // The line keeps the rate it was sold at, as a percentage.
        $this->assertEquals(12.0, (float) $order->items()->sole()->tax_rate);
    }

    public function test_re_sending_a_sale_does_not_double_its_discounts(): void
    {
        $this->seed();
        $token = $this->staffToken();
        $orderId = (string) Str::uuid();

        $this->record($token, $this->sale(orderId: $orderId))->assertCreated();
        $this->record($token, $this->sale(orderId: $orderId))->assertOk();

        $this->assertSame(1, OrderDiscount::query()->where('order_id', $orderId)->count());
    }

    public function test_inconsistent_totals_are_refused(): void
    {
        $this->seed();
        $event = $this->sale(['totalCents' => 99999], [[
            'kind' => 'manual', 'amountCents' => 1000,
        ]]);

        $this->record($this->staffToken(), $event)
            ->assertUnprocessable()
            ->assertJsonValidationErrors('order');

        $this->assertNull(Order::withTrashed()->find($event['entityId']));
        $this->assertSame(0, OrderDiscount::query()->count());
    }

    public function test_a_discount_beyond_the_roles_limit_is_refused(): void
    {
        $this->seed();
        // Cashiers may give nothing by default.
        $event = $this->sale();

        $this->record($this->staffWithRole('cashier'), $event)
            ->assertUnprocessable()
            ->assertJsonPath('errors.order.0', 'Your role cannot give discounts. Ask a manager to sign in and apply it.');

        $this->assertNull(Order::query()->find($event['entityId']));
    }

    public function test_a_role_with_a_limit_is_told_what_it_is(): void
    {
        $this->seed();
        PosRole::query()
            ->where('organization_id', $this->org()->id)
            ->where('role_key', 'cashier')
            ->update(['max_discount_percent' => 5]);

        $this->record($this->staffWithRole('cashier'), $this->sale())
            ->assertUnprocessable()
            ->assertJsonPath('errors.order.0', 'Your role can take up to 5% off a sale. Ask a manager to sign in and apply this discount.');
    }

    public function test_a_role_given_a_limit_may_discount_up_to_it(): void
    {
        $this->seed();
        PosRole::query()
            ->where('organization_id', $this->org()->id)
            ->where('role_key', 'cashier')
            ->update(['max_discount_percent' => 10]);

        $event = $this->sale();
        $this->record($this->staffWithRole('cashier'), $event)->assertCreated();
    }

    /** A local-only till user has an id the server never saw; the sale must not fail on it. */
    public function test_an_unknown_giver_is_credited_to_whoever_pushed(): void
    {
        $this->seed();
        $event = $this->sale(discounts: [[
            'kind' => 'manual', 'amountCents' => 2400, 'appliedByUserId' => (string) Str::uuid(),
        ]]);

        $this->record($this->staffToken(), $event)->assertCreated();

        $this->assertSame(
            User::query()->where('username', 'admin')->value('id'),
            OrderDiscount::query()->where('order_id', $event['entityId'])->value('applied_by'),
        );
    }

    public function test_roles_carry_a_discount_limit_that_an_older_till_cannot_wipe(): void
    {
        $this->seed();
        $token = $this->staffToken();
        $cashier = fn (array $extra = []) => array_merge([
            'id' => 'cashier', 'name' => 'Cashier', 'permissions' => ['register' => true],
        ], $extra);

        $this->withToken($token)
            ->putJson('/api/staff-roles', ['roles' => [$cashier(['maxDiscountPercent' => 15])]])
            ->assertOk();

        // A till from before discount limits saves its roles without the key.
        $this->withToken($token)
            ->putJson('/api/staff-roles', ['roles' => [$cashier()]])
            ->assertOk();

        $roles = collect($this->withToken($token)->getJson('/api/staff-roles')->assertOk()->json('roles'));
        $this->assertSame(15, $roles->firstWhere('id', 'cashier')['maxDiscountPercent']);
    }

    // -----------------------------------------------------------------
    // The tax-rate unit
    // -----------------------------------------------------------------

    public function test_a_product_rate_is_stored_as_a_percentage_whichever_way_it_arrives(): void
    {
        $this->seed();
        $token = $this->staffToken();
        $espresso = Product::query()->where('sku', 'ESP-0001')->firstOrFail();

        $push = fn (float $rate) => $this->push($token, [[
            'id' => (string) Str::uuid(),
            'entityType' => 'product',
            'entityId' => $espresso->id,
            'operation' => 'upsert',
            'occurredAt' => now()->toIso8601String(),
            'payload' => [
                'name' => $espresso->name, 'categoryId' => $espresso->category_id,
                'sku' => $espresso->sku, 'priceCents' => 12000, 'taxRate' => $rate,
            ],
        ]]);

        $push(0.12); // the till
        $this->assertEquals(12.0, (float) $espresso->fresh()->tax_rate);

        $push(12); // the seller app, echoing what it read
        $this->assertEquals(12.0, (float) $espresso->fresh()->tax_rate);

        $push(0);
        $this->assertEquals(0.0, (float) $espresso->fresh()->tax_rate);
    }

    /** Served as-is, 12 was 1,200% VAT in the storefront's cart. */
    public function test_the_storefront_catalog_serves_the_rate_as_a_fraction(): void
    {
        $this->seed();

        $this->getJson('/api/storefront/catalog?orgSlug=demo-coffee&storeCode=main')
            ->assertOk()
            ->assertJsonPath('products.0.taxRate', 0.12);
    }
}
