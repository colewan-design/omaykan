<?php

namespace Tests\Feature\Api;

use App\Mail\SubscriptionDunningMail;
use App\Models\Organization;
use App\Models\OrganizationMembership;
use App\Models\Subscription;
use App\Services\Billing\SubscriptionBilling;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Illuminate\Support\Facades\Mail;
use Tests\TestCase;

/**
 * The subscription lifecycle: when a period lapses, and who we tell.
 *
 * The companion to TenantAccessApiTest, which covers the *verdict*. This file
 * covers the thing that moves a row from one status to the next as time
 * passes — `billing:advance-subscriptions` and the three notices in §6.4.
 *
 * Two rules are load-bearing and each has its own test below: the status
 * machine runs whatever `billing.enforce` says, and the mail does not go out
 * unless `billing.dunning` is explicitly on. Every merchant on the platform
 * was promised free early access, so a dunning email sent by accident is the
 * worst thing in this file.
 *
 * See documentation/subscription-and-suspension.md §6.4.
 */
class SubscriptionLifecycleTest extends TestCase
{
    use RefreshDatabase;

    /**
     * Seeded in setUp rather than per-test: unlike the verdict tests, every
     * case here needs the same fixture — demo-coffee and its owner, who is
     * the one the notices are addressed to.
     *
     * Explicitly, not via `protected $seed`, which does not survive the full
     * suite run against the in-memory database. Same call the rest of the
     * suite makes.
     */
    protected function setUp(): void
    {
        parent::setUp();

        $this->seed();
    }

    private function demoCoffee(): Organization
    {
        return Organization::query()->where('slug', 'demo-coffee')->firstOrFail();
    }

    /**
     * A subscription with no trial, because a trial outranks everything here
     * and would make every test below a no-op. The trial's own precedence is
     * tested explicitly.
     */
    private function subscribe(array $attributes = []): Subscription
    {
        return Subscription::query()->create(array_merge([
            'organization_id' => $this->demoCoffee()->id,
            'status' => Subscription::STATUS_ACTIVE,
            'plan' => 'standard-monthly',
            'amount_cents' => 49900,
            'gcash_reference' => '',
            'trial_ends_at' => null,
            'current_period_ends_at' => now()->addMonth(),
        ], $attributes));
    }

    private function billing(): SubscriptionBilling
    {
        return app(SubscriptionBilling::class);
    }

    private function allowDunning(): void
    {
        config(['billing.dunning' => true]);
    }

    // ---------------------------------------------------------------------
    // The status machine
    // ---------------------------------------------------------------------

    public function test_an_active_subscription_past_its_period_becomes_past_due(): void
    {
        $subscription = $this->subscribe(['current_period_ends_at' => now()->subDay()]);

        $this->assertSame(Subscription::STATUS_PAST_DUE, $this->billing()->advance($subscription));
        $this->assertSame(Subscription::STATUS_PAST_DUE, $subscription->fresh()->status);
    }

    public function test_a_subscription_inside_its_period_is_left_alone(): void
    {
        $subscription = $this->subscribe(['current_period_ends_at' => now()->addDay()]);

        $this->assertNull($this->billing()->advance($subscription));
        $this->assertSame(Subscription::STATUS_ACTIVE, $subscription->fresh()->status);
    }

    /**
     * A null period end is an organization nothing has ever billed. There is
     * no billing cycle yet, so every real row is in this state — it must not
     * lapse.
     */
    public function test_a_subscription_that_was_never_billed_does_not_lapse(): void
    {
        $subscription = $this->subscribe(['current_period_ends_at' => null]);

        $this->assertNull($this->billing()->advance($subscription));
        $this->assertSame(Subscription::STATUS_ACTIVE, $subscription->fresh()->status);
    }

    public function test_a_trial_outranks_a_lapsed_period(): void
    {
        $subscription = $this->subscribe([
            'current_period_ends_at' => now()->subMonth(),
            'trial_ends_at' => now()->addYear(),
        ]);

        $this->assertNull($this->billing()->advance($subscription));
        $this->assertSame(Subscription::STATUS_ACTIVE, $subscription->fresh()->status);
    }

    public function test_advancing_is_idempotent(): void
    {
        $subscription = $this->subscribe(['current_period_ends_at' => now()->subDay()]);

        $this->billing()->advance($subscription);

        $this->assertNull($this->billing()->advance($subscription->fresh()));
    }

    public function test_a_rejected_subscription_is_not_moved_by_the_scheduler(): void
    {
        $subscription = $this->subscribe([
            'status' => Subscription::STATUS_REJECTED,
            'current_period_ends_at' => now()->subDay(),
        ]);

        $this->assertNull($this->billing()->advance($subscription));
        $this->assertSame(Subscription::STATUS_REJECTED, $subscription->fresh()->status);
    }

    /**
     * The status machine is deliberately independent of enforcement: the dates
     * must already be right on the day enforcement is switched on.
     */
    public function test_statuses_advance_even_with_enforcement_off(): void
    {
        config(['billing.enforce' => false]);

        $subscription = $this->subscribe(['current_period_ends_at' => now()->subDay()]);

        $this->artisan('billing:advance-subscriptions')->assertSuccessful();

        $this->assertSame(Subscription::STATUS_PAST_DUE, $subscription->fresh()->status);
    }

    // ---------------------------------------------------------------------
    // Which notice is due
    // ---------------------------------------------------------------------

    public function test_a_renewal_notice_is_due_inside_the_warning_window(): void
    {
        config(['billing.renewal_notice_days' => 3]);

        $subscription = $this->subscribe(['current_period_ends_at' => now()->addDays(2)]);

        $this->assertSame(SubscriptionBilling::STAGE_RENEWAL, $this->billing()->noticeDue($subscription));
    }

    public function test_no_notice_is_due_well_before_the_period_ends(): void
    {
        config(['billing.renewal_notice_days' => 3]);

        $subscription = $this->subscribe(['current_period_ends_at' => now()->addDays(20)]);

        $this->assertNull($this->billing()->noticeDue($subscription));
    }

    public function test_a_past_due_subscription_earns_the_past_due_notice(): void
    {
        config(['billing.grace_days' => 7, 'billing.final_notice_days' => 2]);

        $subscription = $this->subscribe([
            'status' => Subscription::STATUS_PAST_DUE,
            'current_period_ends_at' => now()->subDay(),
        ]);

        $this->assertSame(SubscriptionBilling::STAGE_PAST_DUE, $this->billing()->noticeDue($subscription));
    }

    public function test_the_final_notice_arrives_before_grace_elapses(): void
    {
        config(['billing.grace_days' => 7, 'billing.final_notice_days' => 2]);

        $subscription = $this->subscribe([
            'status' => Subscription::STATUS_PAST_DUE,
            'current_period_ends_at' => now()->subDays(6),
            'dunning_stage' => SubscriptionBilling::STAGE_PAST_DUE,
        ]);

        $this->assertSame(SubscriptionBilling::STAGE_FINAL, $this->billing()->noticeDue($subscription));
    }

    public function test_a_notice_is_not_sent_twice(): void
    {
        config(['billing.grace_days' => 7, 'billing.final_notice_days' => 2]);

        $subscription = $this->subscribe([
            'status' => Subscription::STATUS_PAST_DUE,
            'current_period_ends_at' => now()->subDay(),
            'dunning_stage' => SubscriptionBilling::STAGE_PAST_DUE,
        ]);

        $this->assertNull($this->billing()->noticeDue($subscription));
    }

    /**
     * The sequence only ever moves forward. A row that somehow reached the
     * final notice must not fall back to the gentler one.
     */
    public function test_the_sequence_never_goes_backwards(): void
    {
        config(['billing.grace_days' => 7, 'billing.final_notice_days' => 2]);

        $subscription = $this->subscribe([
            'status' => Subscription::STATUS_PAST_DUE,
            'current_period_ends_at' => now()->subDay(),
            'dunning_stage' => SubscriptionBilling::STAGE_FINAL,
        ]);

        $this->assertNull($this->billing()->noticeDue($subscription));
    }

    public function test_a_merchant_in_their_trial_is_never_chased(): void
    {
        $subscription = $this->subscribe([
            'status' => Subscription::STATUS_PAST_DUE,
            'current_period_ends_at' => now()->subDay(),
            'trial_ends_at' => now()->addYear(),
        ]);

        $this->assertNull($this->billing()->noticeDue($subscription));
    }

    public function test_a_pending_subscription_is_not_chased(): void
    {
        $subscription = $this->subscribe([
            'status' => Subscription::STATUS_PENDING,
            'current_period_ends_at' => now()->subDay(),
        ]);

        $this->assertNull($this->billing()->noticeDue($subscription));
    }

    public function test_a_rejected_subscription_is_not_chased(): void
    {
        $subscription = $this->subscribe([
            'status' => Subscription::STATUS_REJECTED,
            'current_period_ends_at' => now()->subDay(),
        ]);

        $this->assertNull($this->billing()->noticeDue($subscription));
    }

    /**
     * A one-day grace window has no moment that is both after the past-due
     * notice and before trading stops, so there is no separate final notice.
     * The past-due mail already carries the date, which is the thing that
     * matters.
     */
    public function test_a_short_grace_window_gets_one_notice_not_two(): void
    {
        config(['billing.grace_days' => 1, 'billing.final_notice_days' => 5]);

        $subscription = $this->subscribe([
            'status' => Subscription::STATUS_PAST_DUE,
            'current_period_ends_at' => now()->subHours(2),
        ]);

        $this->assertSame(SubscriptionBilling::STAGE_PAST_DUE, $this->billing()->noticeDue($subscription));
    }

    /**
     * And the final notice never arrives late — a warning that trading stops
     * "soon", delivered after it stopped, is worse than no warning.
     */
    public function test_the_final_notice_lands_before_trading_stops(): void
    {
        config(['billing.grace_days' => 7, 'billing.final_notice_days' => 2]);

        $subscription = $this->subscribe([
            'status' => Subscription::STATUS_PAST_DUE,
            'current_period_ends_at' => now()->subDays(6),
            'dunning_stage' => SubscriptionBilling::STAGE_PAST_DUE,
        ]);

        $this->assertSame(SubscriptionBilling::STAGE_FINAL, $this->billing()->noticeDue($subscription));
        $this->assertTrue(
            $this->billing()->graceEndsAt($subscription)->isFuture(),
            'The final notice must go out while the shop is still trading.',
        );
    }

    // ---------------------------------------------------------------------
    // The mail
    // ---------------------------------------------------------------------

    public function test_no_mail_goes_out_while_dunning_is_off(): void
    {
        config(['billing.dunning' => false]);
        Mail::fake();

        $this->subscribe([
            'status' => Subscription::STATUS_PAST_DUE,
            'current_period_ends_at' => now()->subDay(),
        ]);

        $this->artisan('billing:advance-subscriptions')->assertSuccessful();

        Mail::assertNothingQueued();
    }

    public function test_the_past_due_notice_reaches_the_owner(): void
    {
        $this->allowDunning();
        Mail::fake();

        $subscription = $this->subscribe([
            'status' => Subscription::STATUS_PAST_DUE,
            'current_period_ends_at' => now()->subDay(),
        ]);

        $this->artisan('billing:advance-subscriptions')->assertSuccessful();

        $ownerEmail = OrganizationMembership::query()
            ->where('organization_id', $this->demoCoffee()->id)
            ->where('membership_role', 'admin')
            ->with('user')
            ->first()->user->email;

        Mail::assertQueued(
            SubscriptionDunningMail::class,
            fn (SubscriptionDunningMail $mail) => $mail->hasTo($ownerEmail)
                && $mail->stage === SubscriptionBilling::STAGE_PAST_DUE
        );

        $this->assertSame(
            SubscriptionBilling::STAGE_PAST_DUE,
            $subscription->fresh()->dunning_stage,
        );
        $this->assertNotNull($subscription->fresh()->dunned_at);
    }

    /**
     * The command runs daily. Running it twice in one day must not mail twice
     * — this is the test that keeps a helpful system from becoming a reason
     * to filter our domain.
     */
    public function test_running_the_command_twice_mails_once(): void
    {
        $this->allowDunning();
        Mail::fake();

        $this->subscribe([
            'status' => Subscription::STATUS_PAST_DUE,
            'current_period_ends_at' => now()->subDay(),
        ]);

        $this->artisan('billing:advance-subscriptions')->assertSuccessful();
        $this->artisan('billing:advance-subscriptions')->assertSuccessful();

        Mail::assertQueuedCount(1);
    }

    /**
     * A subscription that lapses today earns the past-due notice on the same
     * run, rather than waiting for tomorrow's.
     */
    public function test_a_subscription_lapsing_today_is_advanced_and_mailed_in_one_run(): void
    {
        $this->allowDunning();
        Mail::fake();

        $subscription = $this->subscribe(['current_period_ends_at' => now()->subHour()]);

        $this->artisan('billing:advance-subscriptions')->assertSuccessful();

        $this->assertSame(Subscription::STATUS_PAST_DUE, $subscription->fresh()->status);
        Mail::assertQueued(
            SubscriptionDunningMail::class,
            fn (SubscriptionDunningMail $mail) => $mail->stage === SubscriptionBilling::STAGE_PAST_DUE
        );
    }

    public function test_a_dry_run_writes_nothing_and_sends_nothing(): void
    {
        $this->allowDunning();
        Mail::fake();

        $subscription = $this->subscribe(['current_period_ends_at' => now()->subDay()]);

        $this->artisan('billing:advance-subscriptions', ['--dry-run' => true])->assertSuccessful();

        $this->assertSame(Subscription::STATUS_ACTIVE, $subscription->fresh()->status);
        $this->assertNull($subscription->fresh()->dunning_stage);
        Mail::assertNothingQueued();
    }

    /**
     * The dry run has to predict the real run, including the notice a shop
     * lapsing *today* earns — which is the past-due one, not the renewal
     * reminder its pre-advance status would suggest.
     */
    public function test_a_dry_run_predicts_the_notice_the_real_run_sends(): void
    {
        $this->allowDunning();
        Mail::fake();

        $this->subscribe(['current_period_ends_at' => now()->subHour()]);

        $this->artisan('billing:advance-subscriptions', ['--dry-run' => true])
            ->expectsOutputToContain('would mail demo-coffee: '.SubscriptionBilling::STAGE_PAST_DUE)
            ->assertSuccessful();

        $this->artisan('billing:advance-subscriptions')->assertSuccessful();

        Mail::assertQueued(
            SubscriptionDunningMail::class,
            fn (SubscriptionDunningMail $mail) => $mail->stage === SubscriptionBilling::STAGE_PAST_DUE
        );
    }

    public function test_the_renewal_notice_renders(): void
    {
        $subscription = $this->subscribe(['current_period_ends_at' => now()->addDays(2)]);

        $owner = OrganizationMembership::query()
            ->where('organization_id', $this->demoCoffee()->id)
            ->where('membership_role', 'admin')
            ->with('user')
            ->first()->user;

        $rendered = (new SubscriptionDunningMail(
            $owner,
            $this->demoCoffee(),
            $subscription,
            SubscriptionBilling::STAGE_RENEWAL,
        ))->render();

        $this->assertStringContainsString('renews soon', $rendered);
        $this->assertStringContainsString('499.00', $rendered);
    }

    public function test_the_final_notice_says_when_the_shop_stops(): void
    {
        config(['billing.grace_days' => 7]);

        $subscription = $this->subscribe([
            'status' => Subscription::STATUS_PAST_DUE,
            'current_period_ends_at' => now()->subDays(5),
        ]);

        $owner = OrganizationMembership::query()
            ->where('organization_id', $this->demoCoffee()->id)
            ->where('membership_role', 'admin')
            ->with('user')
            ->first()->user;

        $rendered = (new SubscriptionDunningMail(
            $owner,
            $this->demoCoffee(),
            $subscription,
            SubscriptionBilling::STAGE_FINAL,
        ))->render();

        $this->assertStringContainsString('stops accepting new orders', $rendered);
        $this->assertStringContainsString(now()->subDays(5)->addDays(7)->format('j F Y'), $rendered);
    }

    // ---------------------------------------------------------------------
    // Payment — the gateway seam
    // ---------------------------------------------------------------------

    public function test_recording_a_payment_clears_the_dunning_trail(): void
    {
        $subscription = $this->subscribe([
            'status' => Subscription::STATUS_PAST_DUE,
            'current_period_ends_at' => now()->subDay(),
            'dunning_stage' => SubscriptionBilling::STAGE_FINAL,
            'dunned_at' => now()->subDay(),
        ]);

        $this->billing()->recordPayment($subscription, now()->addMonth());

        $fresh = $subscription->fresh();

        $this->assertSame(Subscription::STATUS_ACTIVE, $fresh->status);
        $this->assertNull($fresh->dunning_stage);
        $this->assertNull($fresh->dunned_at);
        $this->assertNotNull($fresh->verified_at);
        $this->assertTrue($fresh->current_period_ends_at->isFuture());
    }

    /**
     * A merchant who lapses, renews, then lapses again gets the whole sequence
     * a second time rather than resuming at the final notice.
     */
    public function test_a_renewed_subscription_starts_the_sequence_again_if_it_lapses(): void
    {
        config(['billing.grace_days' => 7, 'billing.final_notice_days' => 2]);

        $subscription = $this->subscribe([
            'status' => Subscription::STATUS_PAST_DUE,
            'dunning_stage' => SubscriptionBilling::STAGE_FINAL,
        ]);

        $this->billing()->recordPayment($subscription, now()->addMonth());

        $subscription->forceFill(['current_period_ends_at' => now()->subDay()])->save();
        $this->billing()->advance($subscription);

        $this->assertSame(SubscriptionBilling::STAGE_PAST_DUE, $this->billing()->noticeDue($subscription->fresh()));
    }

    public function test_payment_does_not_retroactively_shorten_an_untouched_period(): void
    {
        $subscription = $this->subscribe(['current_period_ends_at' => now()->addMonth()]);
        $original = $subscription->current_period_ends_at;

        // No date given — an operator verifying a transfer today, with no
        // billing cycle to write.
        $this->billing()->recordPayment($subscription);

        $this->assertTrue($original->equalTo($subscription->fresh()->current_period_ends_at));
    }
}
