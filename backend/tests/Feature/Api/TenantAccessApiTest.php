<?php

namespace Tests\Feature\Api;

use App\Models\Order;
use App\Models\Organization;
use App\Models\PlatformAdmin;
use App\Models\PlatformSetting;
use App\Models\Product;
use App\Models\StoreMembership;
use App\Models\Subscription;
use App\Models\User;
use App\Services\Billing\TenantAccess;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Illuminate\Support\Facades\Broadcast;
use Tests\Concerns\SignsInStaff;
use Tests\TestCase;

/**
 * Whether an organization may trade — the verdict, and every place it bites.
 *
 * The seeded fixture is demo-coffee, which has **no subscription at all**. That
 * is useful twice over: with billing enforcement off it must trade exactly as
 * it always has (the mechanism ships inert), and with it on it is the plainest
 * possible unpaid tenant.
 *
 * See documentation/subscription-and-suspension.md.
 */
class TenantAccessApiTest extends TestCase
{
    use RefreshDatabase, SignsInStaff;

    private function demoCoffee(): Organization
    {
        return Organization::query()->where('slug', 'demo-coffee')->firstOrFail();
    }

    private function suspendDemoCoffee(): void
    {
        $this->demoCoffee()->forceFill(['suspended' => true])->save();
    }

    private function enforceBilling(): void
    {
        config(['billing.enforce' => true]);
    }

    private function subscribe(Organization $organization, array $attributes): Subscription
    {
        return Subscription::query()->create(array_merge([
            'organization_id' => $organization->id,
            'status' => Subscription::STATUS_ACTIVE,
            'plan' => 'standard-monthly',
            'amount_cents' => 49900,
            'gcash_reference' => '',
        ], $attributes));
    }

    private function verdictFor(Organization $organization): TenantAccess
    {
        return $organization->fresh(['subscription'])->accessVerdict();
    }

    private function orderPayload(): array
    {
        return [
            'orgSlug' => 'demo-coffee',
            'storeCode' => 'main',
            'businessMode' => 'coffee-shop',
            'items' => [
                ['productId' => Product::query()->where('sku', 'ESP-0001')->firstOrFail()->id, 'quantity' => 1],
            ],
            'guest' => ['name' => 'Maria Santos', 'phone' => '09171234567'],
            'fulfillment' => ['method' => 'pickup'],
        ];
    }

    // ---------------------------------------------------------------------
    // The verdict
    // ---------------------------------------------------------------------

    public function test_with_enforcement_off_an_org_with_no_subscription_is_allowed(): void
    {
        $this->seed();

        $this->assertSame(TenantAccess::Allowed, $this->verdictFor($this->demoCoffee()));
    }

    public function test_suspension_outranks_a_paid_up_subscription(): void
    {
        $this->seed();
        $this->enforceBilling();
        $this->subscribe($this->demoCoffee(), []);
        $this->suspendDemoCoffee();

        $this->assertSame(TenantAccess::Suspended, $this->verdictFor($this->demoCoffee()));
    }

    public function test_an_org_whose_own_status_is_not_active_reads_as_suspended(): void
    {
        $this->seed();
        $this->demoCoffee()->forceFill(['status' => 'closed'])->save();

        $this->assertSame(TenantAccess::Suspended, $this->verdictFor($this->demoCoffee()));
    }

    /**
     * The column defaults to active in the database; a model built without it
     * holds null until refreshed. That must not read as a switched-off tenant.
     */
    public function test_a_status_not_yet_loaded_is_not_mistaken_for_suspension(): void
    {
        $organization = new Organization(['name' => 'Fresh', 'slug' => 'fresh']);

        $this->assertSame(TenantAccess::Allowed, $organization->accessVerdict());
    }

    public function test_with_enforcement_on_no_subscription_is_unpaid(): void
    {
        $this->seed();
        $this->enforceBilling();

        $this->assertSame(TenantAccess::Unpaid, $this->verdictFor($this->demoCoffee()));
    }

    /**
     * Every signup is pending and only an operator moves it. Penalising a
     * merchant for the length of our own queue would be backwards.
     */
    public function test_pending_verification_is_not_unpaid(): void
    {
        $this->seed();
        $this->enforceBilling();
        $this->subscribe($this->demoCoffee(), ['status' => Subscription::STATUS_PENDING]);

        $this->assertSame(TenantAccess::Allowed, $this->verdictFor($this->demoCoffee()));
    }

    public function test_rejected_is_unpaid_even_inside_a_trial(): void
    {
        $this->seed();
        $this->enforceBilling();
        $this->subscribe($this->demoCoffee(), [
            'status' => Subscription::STATUS_REJECTED,
            'trial_ends_at' => now()->addMonth(),
        ]);

        $this->assertSame(TenantAccess::Unpaid, $this->verdictFor($this->demoCoffee()));
    }

    public function test_an_active_subscription_is_current_until_its_period_ends(): void
    {
        $this->seed();
        $this->enforceBilling();
        $subscription = $this->subscribe($this->demoCoffee(), ['current_period_ends_at' => now()->addDay()]);

        $this->assertSame(TenantAccess::Allowed, $this->verdictFor($this->demoCoffee()));

        $subscription->forceFill(['current_period_ends_at' => now()->subDay()])->save();

        $this->assertSame(TenantAccess::Unpaid, $this->verdictFor($this->demoCoffee()));
    }

    public function test_an_active_subscription_with_no_billing_cycle_yet_is_current(): void
    {
        $this->seed();
        $this->enforceBilling();
        $this->subscribe($this->demoCoffee(), ['current_period_ends_at' => null]);

        $this->assertSame(TenantAccess::Allowed, $this->verdictFor($this->demoCoffee()));
    }

    public function test_past_due_is_usable_through_the_grace_window_and_not_after(): void
    {
        $this->seed();
        $this->enforceBilling();
        config(['billing.grace_days' => 7]);

        $subscription = $this->subscribe($this->demoCoffee(), [
            'status' => Subscription::STATUS_PAST_DUE,
            'current_period_ends_at' => now()->subDays(3),
        ]);

        $this->assertSame(TenantAccess::Allowed, $this->verdictFor($this->demoCoffee()));

        $subscription->forceFill(['current_period_ends_at' => now()->subDays(8)])->save();

        $this->assertSame(TenantAccess::Unpaid, $this->verdictFor($this->demoCoffee()));
    }

    public function test_a_trial_covers_a_lapsed_period(): void
    {
        $this->seed();
        $this->enforceBilling();
        $this->subscribe($this->demoCoffee(), [
            'current_period_ends_at' => now()->subMonth(),
            'trial_ends_at' => now()->addMonth(),
        ]);

        $this->assertSame(TenantAccess::Allowed, $this->verdictFor($this->demoCoffee()));
    }

    // ---------------------------------------------------------------------
    // Suspension, end to end
    // ---------------------------------------------------------------------

    /**
     * The case that matters most. Tokens do not expire, so a suspension that
     * only bit at sign-in would leave every till already signed in working.
     */
    public function test_a_suspension_stops_a_token_minted_before_it(): void
    {
        $this->seed();
        $token = $this->staffToken();

        $this->withToken($token)->getJson('/api/sync/bootstrap')->assertOk();

        $this->suspendDemoCoffee();

        $this->withToken($token)->getJson('/api/sync/bootstrap')
            ->assertForbidden()
            ->assertJsonPath('tenantAccess', 'suspended');
    }

    /**
     * 403, never 401: clients clear a stored token on 401, and a merchant
     * silently signed out on suspension day would report a broken login.
     */
    public function test_a_suspended_shop_is_refused_at_the_store_picker_with_a_reason(): void
    {
        $this->seed();
        $this->suspendDemoCoffee();

        $signIn = $this->postJson('/api/staff/sign-in', [
            'identifier' => 'admin',
            'password' => 'password',
        ])->assertOk();

        // Marked rather than hidden, so the picker can say why.
        $store = collect($signIn->json('stores'))->firstWhere('code', 'main');
        $this->assertSame('suspended', $store['tenantAccess']);

        $this->withToken($signIn->json('token'))
            ->postJson('/api/staff/session-store', ['storeId' => $store['id']])
            ->assertForbidden()
            ->assertJsonPath('tenantAccess', 'suspended');
    }

    public function test_a_suspended_shops_storefront_is_closed(): void
    {
        $this->seed();
        $this->suspendDemoCoffee();

        $this->getJson('/api/storefront/catalog?orgSlug=demo-coffee&storeCode=main')
            ->assertNotFound()
            ->assertJsonPath('tenantAccess', 'closed');
    }

    /**
     * Refused independently of the catalog: the storefront keeps its cart in
     * localStorage, so a checkout can arrive from a page loaded before the
     * suspension.
     */
    public function test_a_suspended_shop_takes_no_new_orders(): void
    {
        $this->seed();
        $this->suspendDemoCoffee();

        $this->postJson('/api/online-orders', $this->orderPayload())
            ->assertNotFound()
            ->assertJsonPath('tenantAccess', 'closed');

        $this->assertSame(0, Order::query()->count());
    }

    public function test_an_inactive_store_takes_no_orders_either(): void
    {
        $this->seed();
        $this->demoCoffee()->stores()->update(['status' => 'inactive']);

        $this->postJson('/api/online-orders', $this->orderPayload())->assertNotFound();

        $this->assertSame(0, Order::query()->count());
    }

    /** The order was placed while the shop was trading; the customer is owed its outcome. */
    public function test_an_order_placed_before_the_suspension_can_still_be_tracked(): void
    {
        $this->seed();

        $orderId = $this->postJson('/api/online-orders', $this->orderPayload())
            ->assertCreated()
            ->json('orderId');

        $this->suspendDemoCoffee();

        $this->getJson("/api/online-orders/{$orderId}")->assertOk();
    }

    /**
     * The dashboard's live feed. Without this, a suspended shop's open tab
     * keeps streaming new orders while every API call from it is refused.
     */
    public function test_a_suspended_shops_staff_lose_the_live_channel(): void
    {
        $this->seed();
        $store = $this->demoCoffee()->stores()->firstOrFail();
        $member = User::query()->findOrFail(
            StoreMembership::query()->where('store_id', $store->id)->firstOrFail()->user_id,
        );
        $authorize = Broadcast::driver()->getChannels()['store.{storeId}'];

        $this->assertTrue($authorize($member, $store->id));

        $this->enforceBilling();
        $this->assertTrue($authorize($member, $store->id), 'An unpaid shop keeps its feed: it still has orders to finish.');

        $this->suspendDemoCoffee();
        $this->assertFalse($authorize($member, $store->id));
    }

    public function test_a_suspended_shop_leaves_the_directory(): void
    {
        $this->seed();

        $this->getJson('/api/stores')->assertOk()->assertJsonPath('stores.0.orgSlug', 'demo-coffee');

        $this->suspendDemoCoffee();

        $this->getJson('/api/stores')->assertOk()->assertJsonCount(0, 'stores');
    }

    // ---------------------------------------------------------------------
    // Unpaid, with enforcement on
    // ---------------------------------------------------------------------

    public function test_an_unpaid_shop_is_let_in_and_told_so(): void
    {
        $this->seed();
        $this->enforceBilling();

        $signIn = $this->postJson('/api/staff/sign-in', [
            'identifier' => 'admin',
            'password' => 'password',
        ])->assertOk();

        $this->withToken($signIn->json('token'))
            ->postJson('/api/staff/session-store', ['storeId' => $signIn->json('stores.0.id')])
            ->assertOk()
            ->assertJsonPath('store.tenantAccess', 'unpaid');
    }

    /** Read access to its own records survives; starting anything new does not. */
    public function test_an_unpaid_shop_can_read_but_not_start_anything(): void
    {
        $this->seed();
        $this->enforceBilling();
        $token = $this->staffToken();

        $this->withToken($token)->getJson('/api/sync/bootstrap')->assertOk();
        $this->withToken($token)->getJson('/api/shifts/history')->assertOk();

        $this->withToken($token)
            ->postJson('/api/sync/push', [
                'organizationId' => $this->demoCoffee()->id,
                'storeId' => $this->demoCoffee()->stores()->firstOrFail()->id,
                'events' => [],
            ])
            ->assertForbidden()
            ->assertJsonPath('tenantAccess', 'unpaid');

        $this->withToken($token)
            ->postJson('/api/shifts/open', ['openingCashCents' => 0])
            ->assertForbidden()
            ->assertJsonPath('tenantAccess', 'unpaid');
    }

    public function test_an_unpaid_shops_storefront_is_closed_and_unlisted(): void
    {
        $this->seed();
        $this->enforceBilling();

        $this->getJson('/api/storefront/catalog?orgSlug=demo-coffee&storeCode=main')->assertNotFound();
        $this->postJson('/api/online-orders', $this->orderPayload())->assertNotFound();
        $this->getJson('/api/stores')->assertOk()->assertJsonCount(0, 'stores');
    }

    /** The whole point of shipping inert: nothing about today's merchants changes. */
    public function test_with_enforcement_off_the_same_shop_trades_normally(): void
    {
        $this->seed();
        $token = $this->staffToken();

        $this->withToken($token)
            ->postJson('/api/shifts/open', ['openingCashCents' => 0])
            ->assertSuccessful();

        $this->getJson('/api/storefront/catalog?orgSlug=demo-coffee&storeCode=main')->assertOk();
        $this->postJson('/api/online-orders', $this->orderPayload())->assertCreated();
    }

    // ---------------------------------------------------------------------
    // Price and trial
    // ---------------------------------------------------------------------

    public function test_signup_records_the_trial_and_the_price_from_settings(): void
    {
        $settings = PlatformSetting::current();
        $settings->plan = ['amountCents' => 29900];
        $settings->save();

        $slug = $this->postJson('/api/signup', [
            'businessName' => 'Hill Station Cafe',
            'ownerFullName' => 'Ana Reyes',
            'username' => 'anareyes',
            'email' => 'anareyes@example.test',
            'password' => 'secret123',
            'businessTypeLabel' => 'Coffee shop',
            'businessMode' => 'coffee-shop',
        ])->assertCreated()->json('organizationSlug');

        $subscription = Organization::query()->where('slug', $slug)->firstOrFail()->subscription;

        $this->assertSame(29900, $subscription->amount_cents);
        $this->assertSame('standard-monthly', $subscription->plan);
        $this->assertTrue($subscription->trial_ends_at->between(
            now()->addDays(config('billing.trial_days') - 1),
            now()->addDays(config('billing.trial_days') + 1),
        ));
    }

    public function test_the_operator_sets_the_price_without_repricing_anyone(): void
    {
        $this->seed();
        $existing = $this->subscribe($this->demoCoffee(), ['amount_cents' => 49900]);

        PlatformAdmin::query()->create([
            'name' => 'Operator',
            'email' => 'operator@example.test',
            'password' => 'operator-password-1234',
        ]);
        $token = $this->postJson('/api/platform-admin/login', [
            'email' => 'operator@example.test',
            'password' => 'operator-password-1234',
        ])->assertOk()->json('token');

        $this->withToken($token)
            ->putJson('/api/platform-admin/settings', ['plan' => ['amountCents' => 59900]])
            ->assertOk()
            ->assertJsonPath('settings.plan.amountCents', 59900)
            ->assertJsonPath('settings.plan.id', 'standard-monthly')
            ->assertJsonPath('settings.billingEnforced', false);

        $this->assertSame(49900, $existing->fresh()->amount_cents);

        $this->withToken($token)
            ->postJson('/api/platform-admin', ['action' => 'listOrgs'])
            ->assertOk()
            ->assertJsonPath('organizations.0.tenantAccess', 'allowed');
    }

    // ---------------------------------------------------------------------
    // The backfill
    // ---------------------------------------------------------------------

    public function test_the_backfill_gives_every_org_a_trial_and_is_safe_to_rerun(): void
    {
        $this->seed();
        $this->enforceBilling();
        $this->assertSame(TenantAccess::Unpaid, $this->verdictFor($this->demoCoffee()));

        $this->artisan('billing:backfill-trials', ['--dry-run' => true])->assertSuccessful();
        $this->assertNull($this->demoCoffee()->fresh()->subscription, 'A dry run wrote something.');

        $until = now()->addYear()->toDateString();

        $this->artisan('billing:backfill-trials', ['--until' => $until])->assertSuccessful();

        $subscription = $this->demoCoffee()->fresh()->subscription;
        $this->assertSame(Subscription::STATUS_PENDING, $subscription->status);
        $this->assertSame($until, $subscription->trial_ends_at->toDateString());
        $this->assertSame(TenantAccess::Allowed, $this->verdictFor($this->demoCoffee()));

        // Again, same date: nothing changes, nothing duplicates.
        $this->artisan('billing:backfill-trials', ['--until' => $until])->assertSuccessful();
        $this->assertSame(1, Subscription::query()->where('organization_id', $this->demoCoffee()->id)->count());
    }

    public function test_the_backfill_never_shortens_a_trial_and_leaves_rejections_alone(): void
    {
        $this->seed();
        $organization = $this->demoCoffee();
        $later = now()->addYears(2)->startOfDay();
        $subscription = $this->subscribe($organization, ['trial_ends_at' => $later]);

        $this->artisan('billing:backfill-trials', ['--until' => now()->addYear()->toDateString()])
            ->assertSuccessful();

        $this->assertTrue($subscription->fresh()->trial_ends_at->equalTo($later));

        $subscription->forceFill(['status' => Subscription::STATUS_REJECTED, 'trial_ends_at' => null])->save();

        $this->artisan('billing:backfill-trials')->assertSuccessful();

        $this->assertNull($subscription->fresh()->trial_ends_at);
    }

    public function test_the_backfill_refuses_a_date_in_the_past(): void
    {
        $this->artisan('billing:backfill-trials', ['--until' => '2020-01-01'])->assertFailed();
    }
}
