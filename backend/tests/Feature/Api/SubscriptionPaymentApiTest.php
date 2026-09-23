<?php

namespace Tests\Feature\Api;

use App\Models\Organization;
use App\Models\PlatformAdmin;
use App\Models\PlatformSetting;
use App\Models\StoreMembership;
use App\Models\Subscription;
use App\Models\SubscriptionPayment;
use App\Models\User;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Tests\Concerns\SignsInStaff;
use Tests\TestCase;

/**
 * Manual collection, end to end: a merchant says they paid, an operator says
 * what it bought.
 *
 * There is no payment gateway and none planned (documentation/plan.md §4a), so
 * this *is* billing. It replaces `subscriptions.gcash_reference` — one string,
 * written once at signup by a form that stopped sending it, displayed nowhere.
 *
 * The load-bearing case is the first one below: an **unpaid** shop must be
 * able to reach the pay-us screen. Every other merchant write is refused for
 * an unpaid tenant, and if this one were too, a lapsed shop could never get
 * back.
 *
 * See documentation/subscription-and-suspension.md §6.3.
 */
class SubscriptionPaymentApiTest extends TestCase
{
    use RefreshDatabase, SignsInStaff;

    private const OPERATOR_PASSWORD = 'operator-password';

    private PlatformAdmin $operator;

    protected function setUp(): void
    {
        parent::setUp();

        $this->seed();

        $this->operator = PlatformAdmin::query()->create([
            'name' => 'Platform Operator',
            'email' => 'operator@example.test',
            'password' => self::OPERATOR_PASSWORD,
        ]);
    }

    private function demoCoffee(): Organization
    {
        return Organization::query()->where('slug', 'demo-coffee')->firstOrFail();
    }

    private function subscribe(array $attributes = []): Subscription
    {
        return Subscription::query()->create(array_merge([
            'organization_id' => $this->demoCoffee()->id,
            'status' => Subscription::STATUS_ACTIVE,
            'plan' => 'standard-monthly',
            'amount_cents' => 49900,
            'gcash_reference' => '',
        ], $attributes));
    }

    private function operatorToken(): string
    {
        return $this->postJson('/api/platform-admin/login', [
            'email' => $this->operator->email,
            'password' => self::OPERATOR_PASSWORD,
        ])->assertOk()->json('token');
    }

    private function asOperator(array $body)
    {
        return $this->withToken($this->operatorToken())->postJson('/api/platform-admin', $body);
    }

    private function submit(array $overrides = [])
    {
        return $this->withToken($this->staffToken())
            ->postJson('/api/seller/subscription/payments', array_merge([
                'reference' => 'GC-12345678',
                'amountCents' => 49900,
                'note' => 'Sent this morning.',
            ], $overrides));
    }

    // ---------------------------------------------------------------------
    // The door that must open from inside
    // ---------------------------------------------------------------------

    /**
     * The whole point. An unpaid tenant is refused every other write; if it
     * were refused this one, the only way to stop being unpaid would be to
     * already not be unpaid.
     */
    public function test_an_unpaid_shop_can_still_reach_the_pay_us_screen(): void
    {
        config(['billing.enforce' => true]);

        // No trial, rejected subscription — as unpaid as it gets.
        $this->subscribe([
            'status' => Subscription::STATUS_REJECTED,
            'trial_ends_at' => null,
        ]);

        $token = $this->staffToken();

        $this->withToken($token)->getJson('/api/seller/subscription')->assertOk();

        $this->withToken($token)
            ->postJson('/api/seller/subscription/payments', [
                'reference' => 'GC-12345678',
                'amountCents' => 49900,
            ])
            ->assertCreated();
    }

    /** A write that is not this one stays refused, so the exemption is narrow. */
    public function test_an_unpaid_shop_is_still_refused_other_writes(): void
    {
        config(['billing.enforce' => true]);

        $this->subscribe([
            'status' => Subscription::STATUS_REJECTED,
            'trial_ends_at' => null,
        ]);

        $this->withToken($this->staffToken())
            ->postJson('/api/shifts/open', ['openingFloatCents' => 100000])
            ->assertStatus(403);
    }

    // ---------------------------------------------------------------------
    // The merchant's side
    // ---------------------------------------------------------------------

    public function test_the_shop_is_quoted_the_current_platform_price(): void
    {
        $this->subscribe();

        $settings = PlatformSetting::current();
        $settings->plan = ['id' => 'standard-monthly', 'amountCents' => 29900];
        $settings->save();

        $response = $this->withToken($this->staffToken())
            ->getJson('/api/seller/subscription')
            ->assertOk();

        // The operator's number, not the one frozen on the row at signup.
        $this->assertSame(29900, $response->json('plan.amountCents'));
        $this->assertSame(49900, $response->json('subscription.agreedAmountCents'));
    }

    public function test_a_submitted_payment_is_recorded_as_a_claim(): void
    {
        $subscription = $this->subscribe();

        $this->submit()->assertCreated();

        $payment = SubscriptionPayment::query()->firstOrFail();

        $this->assertSame(SubscriptionPayment::STATUS_SUBMITTED, $payment->status);
        $this->assertSame('GC-12345678', $payment->reference);
        $this->assertSame(49900, $payment->amount_cents);
        $this->assertSame($subscription->id, $payment->subscription_id);
        $this->assertNotNull($payment->submitted_by_user_id);

        // A claim alone changes nothing about what the shop may do.
        $this->assertNull($subscription->fresh()->current_period_ends_at);
    }

    /**
     * An amount that disagrees with the plan price is recorded as sent. A
     * short payment is a fact about the conversation, not an error to round
     * away before an operator sees it.
     */
    public function test_an_amount_that_disagrees_with_the_price_is_kept_as_sent(): void
    {
        $this->subscribe();

        $this->submit(['amountCents' => 20000])->assertCreated();

        $this->assertSame(20000, SubscriptionPayment::query()->firstOrFail()->amount_cents);
    }

    public function test_only_one_claim_can_be_open_at_a_time(): void
    {
        $this->subscribe();

        $this->submit()->assertCreated();
        $this->submit(['reference' => 'GC-87654321'])->assertStatus(422);

        $this->assertSame(1, SubscriptionPayment::query()->count());
    }

    public function test_a_new_claim_is_allowed_once_the_last_was_reviewed(): void
    {
        $this->subscribe();
        $this->submit()->assertCreated();

        $payment = SubscriptionPayment::query()->firstOrFail();
        $this->asOperator([
            'action' => 'rejectPayment',
            'paymentId' => $payment->id,
            'reason' => 'No transfer with that reference.',
        ])->assertOk();

        $this->submit(['reference' => 'GC-87654321'])->assertCreated();
    }

    public function test_the_shop_sees_its_own_history_and_a_rejection_reason(): void
    {
        $this->subscribe();
        $this->submit()->assertCreated();

        $payment = SubscriptionPayment::query()->firstOrFail();
        $this->asOperator([
            'action' => 'rejectPayment',
            'paymentId' => $payment->id,
            'reason' => 'No transfer with that reference.',
        ])->assertOk();

        $response = $this->withToken($this->staffToken())
            ->getJson('/api/seller/subscription')
            ->assertOk();

        $this->assertSame('rejected', $response->json('payments.0.status'));
        $this->assertSame('No transfer with that reference.', $response->json('payments.0.rejectionReason'));
    }

    public function test_a_cashier_cannot_see_or_submit_payments(): void
    {
        $this->subscribe();

        $cashier = User::query()->create([
            'name' => 'Cashier', 'username' => 'cashier1', 'password' => 'password', 'status' => 'active',
        ]);
        StoreMembership::query()->create([
            'store_id' => $this->demoCoffee()->stores()->firstOrFail()->id,
            'user_id' => $cashier->id,
            'membership_role' => 'cashier',
        ]);

        $token = $this->staffToken('cashier1');

        $this->withToken($token)->getJson('/api/seller/subscription')->assertStatus(403);
        $this->withToken($token)
            ->postJson('/api/seller/subscription/payments', [
                'reference' => 'GC-1',
                'amountCents' => 49900,
            ])
            ->assertStatus(403);
    }

    public function test_a_reference_is_required(): void
    {
        $this->subscribe();

        $this->submit(['reference' => ''])->assertStatus(422);
        $this->submit(['amountCents' => 0])->assertStatus(422);
    }

    // ---------------------------------------------------------------------
    // The operator's side
    // ---------------------------------------------------------------------

    public function test_accepting_a_payment_sets_the_period_and_activates(): void
    {
        $subscription = $this->subscribe([
            'status' => Subscription::STATUS_PAST_DUE,
            'dunning_stage' => 'past_due',
            'dunned_at' => now()->subDay(),
        ]);

        $this->submit()->assertCreated();
        $payment = SubscriptionPayment::query()->firstOrFail();

        $periodEnd = now()->addMonth()->startOfDay();

        $this->asOperator([
            'action' => 'acceptPayment',
            'paymentId' => $payment->id,
            'periodStart' => now()->startOfDay()->toDateString(),
            'periodEnd' => $periodEnd->toDateString(),
        ])->assertOk();

        $fresh = $subscription->fresh();

        $this->assertSame(Subscription::STATUS_ACTIVE, $fresh->status);
        $this->assertSame($periodEnd->toDateString(), $fresh->current_period_ends_at->toDateString());

        // Renewing stops the chase.
        $this->assertNull($fresh->dunning_stage);
        $this->assertNull($fresh->dunned_at);

        $this->assertSame(SubscriptionPayment::STATUS_ACCEPTED, $payment->fresh()->status);
    }

    /**
     * Accepting is the only thing in the product that ever writes a billing
     * period — which is why the price being a placeholder is survivable: until
     * an operator does this, every shop rests on its trial.
     */
    public function test_nothing_else_sets_a_billing_period(): void
    {
        $subscription = $this->subscribe();

        $this->asOperator(['action' => 'verify', 'organizationSlug' => 'demo-coffee'])->assertOk();

        $this->assertNull($subscription->fresh()->current_period_ends_at);
    }

    public function test_a_payment_cannot_be_reviewed_twice(): void
    {
        $this->subscribe();
        $this->submit()->assertCreated();
        $payment = SubscriptionPayment::query()->firstOrFail();

        $accept = [
            'action' => 'acceptPayment',
            'paymentId' => $payment->id,
            'periodStart' => now()->toDateString(),
            'periodEnd' => now()->addMonth()->toDateString(),
        ];

        $this->asOperator($accept)->assertOk();
        $this->asOperator($accept)->assertStatus(409);
    }

    public function test_a_period_must_end_after_it_starts(): void
    {
        $this->subscribe();
        $this->submit()->assertCreated();
        $payment = SubscriptionPayment::query()->firstOrFail();

        $this->asOperator([
            'action' => 'acceptPayment',
            'paymentId' => $payment->id,
            'periodStart' => now()->addMonth()->toDateString(),
            'periodEnd' => now()->toDateString(),
        ])->assertStatus(422);
    }

    /** A rejection leaves the shop exactly where it was. It is not a penalty. */
    public function test_rejecting_a_payment_does_not_change_the_subscription(): void
    {
        $subscription = $this->subscribe([
            'status' => Subscription::STATUS_PAST_DUE,
            'current_period_ends_at' => now()->subDay(),
        ]);

        $this->submit()->assertCreated();
        $payment = SubscriptionPayment::query()->firstOrFail();

        $this->asOperator([
            'action' => 'rejectPayment',
            'paymentId' => $payment->id,
            'reason' => 'Could not find it.',
        ])->assertOk();

        $this->assertSame(Subscription::STATUS_PAST_DUE, $subscription->fresh()->status);
    }

    public function test_a_rejection_needs_a_reason(): void
    {
        $this->subscribe();
        $this->submit()->assertCreated();
        $payment = SubscriptionPayment::query()->firstOrFail();

        $this->asOperator([
            'action' => 'rejectPayment',
            'paymentId' => $payment->id,
        ])->assertStatus(422);
    }

    public function test_the_queue_puts_pending_payments_first(): void
    {
        $this->subscribe();

        $this->submit()->assertCreated();
        $first = SubscriptionPayment::query()->firstOrFail();

        $this->asOperator([
            'action' => 'rejectPayment',
            'paymentId' => $first->id,
            'reason' => 'No.',
        ])->assertOk();

        $this->submit(['reference' => 'GC-87654321'])->assertCreated();

        $response = $this->asOperator(['action' => 'listPayments'])->assertOk();

        $this->assertSame('submitted', $response->json('payments.0.status'));
        $this->assertSame('GC-87654321', $response->json('payments.0.reference'));
        $this->assertSame('demo-coffee', $response->json('payments.0.organizationSlug'));
    }

    public function test_the_payment_queue_needs_an_operator(): void
    {
        $this->postJson('/api/platform-admin', ['action' => 'listPayments'])->assertStatus(401);
    }
}
