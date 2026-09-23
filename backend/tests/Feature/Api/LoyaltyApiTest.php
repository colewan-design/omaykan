<?php

namespace Tests\Feature\Api;

use App\Models\LoyaltyEntry;
use App\Models\LoyaltyProgram;
use App\Models\Order;
use App\Models\Organization;
use App\Models\PosCustomer;
use App\Models\Product;
use App\Models\StoreMembership;
use App\Models\User;
use App\Services\Loyalty\Loyalty;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Illuminate\Support\Str;
use Tests\Concerns\SignsInStaff;
use Tests\TestCase;

/**
 * Counter customers on the server, and points on top of them.
 * See documentation/merchant-features.md §9.
 */
class LoyaltyApiTest extends TestCase
{
    use RefreshDatabase, SignsInStaff;

    private function org(): Organization
    {
        return Organization::query()->where('slug', 'demo-coffee')->firstOrFail();
    }

    private function enableProgram(array $attributes = []): void
    {
        LoyaltyProgram::query()->create(array_merge([
            'organization_id' => $this->org()->id,
            'enabled' => true,
            'spend_cents_per_point' => 10000, // a point per ₱100
            'point_value_cents' => 100,       // worth ₱1
            'min_redeem_points' => 1,
        ], $attributes));
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
    private function record(string $token, array $sale)
    {
        return $this->withToken($token)->postJson('/api/register/orders', $sale['payload']);
    }

    private function customerEvent(string $id, array $payload): array
    {
        return [
            'id' => (string) Str::uuid(), 'entityType' => 'customer', 'entityId' => $id,
            'operation' => 'upsert', 'occurredAt' => now()->toIso8601String(), 'payload' => $payload,
        ];
    }

    /** ₱240 of espresso, optionally with points spent. */
    private function sale(string $customerId, ?string $orderId = null, ?int $pointsSpent = null): array
    {
        $orderId ??= (string) Str::uuid();
        $discount = $pointsSpent !== null ? $pointsSpent * 100 : 0;

        return [
            'id' => (string) Str::uuid(), 'entityType' => 'order', 'entityId' => $orderId,
            'operation' => 'upsert', 'occurredAt' => now()->toIso8601String(),
            'payload' => [
                'order' => [
                    'id' => $orderId, 'ticketNumber' => 'TKT-'.Str::random(6), 'orderType' => 'takeaway',
                    'customerId' => $customerId,
                    'subtotalCents' => 24000, 'discountCents' => $discount,
                    'taxCents' => (int) round((24000 - $discount) * 0.12),
                    'totalCents' => 24000 - $discount + (int) round((24000 - $discount) * 0.12),
                ],
                'items' => [[
                    'productId' => Product::query()->where('sku', 'ESP-0001')->firstOrFail()->id,
                    'productName' => 'Espresso', 'quantity' => 2, 'unitPriceCents' => 12000, 'lineTotalCents' => 24000,
                ]],
                'discounts' => $pointsSpent !== null
                    ? [['kind' => 'loyalty', 'amountCents' => $discount, 'points' => $pointsSpent]]
                    : [],
                'payments' => [],
            ],
        ];
    }

    private function enrolledCustomer(string $token): string
    {
        $id = (string) Str::uuid();
        $this->push($token, [$this->customerEvent($id, [
            'name' => 'Rosa Dela Cruz', 'phone' => '09171112222', 'loyaltyConsentAt' => now()->toIso8601String(),
        ])]);

        return $id;
    }

    // -- Customers on the server ------------------------------------------

    public function test_a_customer_made_at_one_till_reaches_the_server_and_the_others(): void
    {
        $this->seed();
        $token = $this->staffToken();
        $id = (string) Str::uuid();

        $this->push($token, [$this->customerEvent($id, ['name' => ' Rosa ', 'phone' => '0917'])])
            ->assertJsonPath('results.0.status', 'applied');

        $this->assertSame('Rosa', PosCustomer::query()->findOrFail($id)->name);

        $this->withToken($token)->getJson('/api/sync/bootstrap')
            ->assertOk()
            ->assertJsonPath('catalog.customers.0.id', $id);

        $this->push($token, [$this->customerEvent($id, ['deletedAt' => now()->toIso8601String()])]);

        $this->withToken($token)->getJson('/api/sync/pull?cursor='.urlencode(now()->subMinute()->toIso8601String()))
            ->assertOk()
            ->assertJsonPath('changes.customers.0.id', $id)
            ->assertJsonPath('changes.customers.0.deletedAt', fn ($value) => $value !== null);
    }

    public function test_a_till_cannot_rewrite_another_shops_customer(): void
    {
        $this->seed();
        $foreign = PosCustomer::query()->create([
            'organization_id' => Organization::query()->create(['name' => 'Other', 'slug' => 'other'])->id,
            'name' => 'Theirs',
        ]);

        $this->push($this->staffToken(), [$this->customerEvent($foreign->id, ['name' => 'Mine now'])])
            ->assertJsonPath('results.0.status', 'failed');

        $this->assertSame('Theirs', $foreign->fresh()->name);
    }

    public function test_consent_is_kept_when_an_older_till_edits_without_it(): void
    {
        $this->seed();
        $token = $this->staffToken();
        $id = $this->enrolledCustomer($token);

        $this->push($token, [$this->customerEvent($id, ['name' => 'Rosa D.'])]);

        $this->assertNotNull(PosCustomer::query()->findOrFail($id)->loyalty_consent_at);
    }

    // -- Earning ----------------------------------------------------------

    public function test_a_sale_to_an_enrolled_customer_earns_points_once(): void
    {
        $this->seed();
        $this->enableProgram();
        $token = $this->staffToken();
        $customerId = $this->enrolledCustomer($token);
        $orderId = (string) Str::uuid();

        $this->record($token, $this->sale($customerId, $orderId));
        $this->record($token, $this->sale($customerId, $orderId));

        $this->assertSame($customerId, Order::query()->findOrFail($orderId)->pos_customer_id);
        // ₱240 at a point per ₱100.
        $this->assertSame(2, app(Loyalty::class)->balance(PosCustomer::query()->findOrFail($customerId)));
    }

    public function test_no_points_without_consent_or_with_the_programme_off(): void
    {
        $this->seed();
        $token = $this->staffToken();
        $id = (string) Str::uuid();
        $this->push($token, [$this->customerEvent($id, ['name' => 'Walk-in regular'])]);

        $this->enableProgram();
        $this->record($token, $this->sale($id));
        $this->assertSame(0, LoyaltyEntry::query()->count());

        $enrolled = $this->enrolledCustomer($token);
        LoyaltyProgram::query()->update(['enabled' => false]);
        $this->record($token, $this->sale($enrolled));
        $this->assertSame(0, LoyaltyEntry::query()->count());
    }

    // -- Spending ---------------------------------------------------------

    public function test_points_are_checked_at_the_counter_and_spent_when_the_sale_syncs(): void
    {
        $this->seed();
        $this->enableProgram();
        $token = $this->staffToken();
        $customerId = $this->enrolledCustomer($token);
        $customer = PosCustomer::query()->findOrFail($customerId);
        LoyaltyEntry::query()->create([
            'organization_id' => $this->org()->id, 'pos_customer_id' => $customerId,
            'reason' => LoyaltyEntry::ADJUST, 'points' => 50, 'note' => 'Opening balance',
        ]);

        $this->withToken($token)
            ->postJson('/api/seller/loyalty/check', ['customerId' => $customerId, 'points' => 30, 'subtotalCents' => 24000])
            ->assertOk()
            ->assertJsonPath('points', 30)
            ->assertJsonPath('discountCents', 3000);

        $this->withToken($token)
            ->postJson('/api/seller/loyalty/check', ['customerId' => $customerId, 'points' => 80, 'subtotalCents' => 24000])
            ->assertUnprocessable()
            ->assertJsonPath('errors.points.0', 'Rosa Dela Cruz has 50 points.');

        $orderId = (string) Str::uuid();
        $this->record($token, $this->sale($customerId, $orderId, pointsSpent: 30))->assertCreated();
        // Re-sent: spent once, not twice.
        $this->record($token, $this->sale($customerId, $orderId, pointsSpent: 30))->assertOk();

        // 50 − 30 spent + 2 earned on the ₱210 that was paid for.
        $this->assertSame(22, app(Loyalty::class)->balance($customer));

        $order = Order::query()->findOrFail($orderId);
        $this->assertSame('loyalty', $order->discounts()->sole()->kind);
    }

    public function test_spending_more_than_the_balance_is_refused(): void
    {
        $this->seed();
        $this->enableProgram();
        $token = $this->staffToken();
        $customerId = $this->enrolledCustomer($token);
        $orderId = (string) Str::uuid();

        $this->record($token, $this->sale($customerId, $orderId, pointsSpent: 10))
            ->assertUnprocessable()
            ->assertJsonPath('errors.order.0', "Rosa Dela Cruz doesn't have 10 points to spend.");

        $this->assertNull(Order::query()->find($orderId));
    }

    /** Points are the customer's, not the cashier's discretion. */
    public function test_points_do_not_count_against_a_cashiers_discount_limit(): void
    {
        $this->seed();
        $this->enableProgram();
        $cashier = User::query()->create(['name' => 'C', 'username' => 'cashier1', 'password' => 'password', 'status' => 'active']);
        StoreMembership::query()->create([
            'store_id' => $this->org()->stores()->firstOrFail()->id, 'user_id' => $cashier->id, 'membership_role' => 'cashier',
        ]);
        $token = $this->staffToken('cashier1');
        $customerId = $this->enrolledCustomer($token);
        LoyaltyEntry::query()->create([
            'organization_id' => $this->org()->id, 'pos_customer_id' => $customerId,
            'reason' => LoyaltyEntry::ADJUST, 'points' => 50, 'note' => 'Opening balance',
        ]);
        $orderId = (string) Str::uuid();

        $this->record($token, $this->sale($customerId, $orderId, pointsSpent: 30))->assertCreated();
    }

    // -- Rules and corrections --------------------------------------------

    public function test_the_programme_is_set_by_whoever_has_the_customers_page(): void
    {
        $this->seed();

        $this->withToken($this->staffToken())
            ->putJson('/api/seller/loyalty', ['enabled' => true, 'spendCentsPerPoint' => 5000, 'expiryMonths' => 12])
            ->assertOk()
            ->assertJsonPath('program.enabled', true)
            ->assertJsonPath('program.spendCentsPerPoint', 5000);

        $cashier = User::query()->create(['name' => 'C', 'username' => 'cashier1', 'password' => 'password', 'status' => 'active']);
        StoreMembership::query()->create([
            'store_id' => $this->org()->stores()->firstOrFail()->id, 'user_id' => $cashier->id, 'membership_role' => 'cashier',
        ]);
        $cashierToken = $this->staffToken('cashier1');

        $this->withToken($cashierToken)->putJson('/api/seller/loyalty', ['enabled' => false])->assertForbidden();
        $this->withToken($cashierToken)->getJson('/api/seller/loyalty')->assertOk()->assertJsonPath('program.enabled', true);
    }

    public function test_a_correction_is_a_new_entry_and_cannot_go_below_zero(): void
    {
        $this->seed();
        $token = $this->staffToken();
        $customerId = $this->enrolledCustomer($token);

        $this->withToken($token)
            ->postJson("/api/seller/customers/{$customerId}/loyalty/adjust", ['points' => 15, 'note' => 'Card from the old system'])
            ->assertOk()
            ->assertJsonPath('balance', 15);

        $this->withToken($token)
            ->postJson("/api/seller/customers/{$customerId}/loyalty/adjust", ['points' => -20, 'note' => 'Oops'])
            ->assertUnprocessable();

        $this->withToken($token)->getJson("/api/seller/customers/{$customerId}/loyalty")
            ->assertOk()
            ->assertJsonPath('balance', 15)
            ->assertJsonPath('entries.0.note', 'Card from the old system');

        $this->withToken($token)->getJson('/api/seller/loyalty/balances')
            ->assertOk()
            ->assertJsonPath("balances.{$customerId}", 15);
    }

    /** Oldest points are spent first, so only what is left of the old ones expires. */
    public function test_expiry_writes_off_only_the_unspent_old_points(): void
    {
        $this->seed();
        $token = $this->staffToken();
        $customer = PosCustomer::query()->findOrFail($this->enrolledCustomer($token));
        $entry = fn (string $reason, int $points, $expiresAt = null) => LoyaltyEntry::query()->create([
            'organization_id' => $this->org()->id, 'pos_customer_id' => $customer->id,
            'reason' => $reason, 'points' => $points, 'expires_at' => $expiresAt,
        ]);

        $entry(LoyaltyEntry::EARN, 10, now()->subDay());   // old, expired
        $entry(LoyaltyEntry::EARN, 5, now()->addMonth());  // still good
        $entry(LoyaltyEntry::REDEEM, -4);                  // spent from the old ones

        $this->artisan('loyalty:expire')->assertSuccessful();
        $this->assertSame(5, app(Loyalty::class)->balance($customer));

        $this->artisan('loyalty:expire')->assertSuccessful();
        $this->assertSame(5, app(Loyalty::class)->balance($customer));
    }
}
