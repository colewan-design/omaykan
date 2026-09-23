<?php

namespace App\Services\Billing;

use App\Models\Subscription;
use Carbon\CarbonInterface;
use Illuminate\Support\Carbon;

/**
 * The subscription status machine: when a period lapses, and who we have told.
 *
 * `Subscription::isCurrent()` answers *is this row current right now* from the
 * dates it already holds. This class is the other half — the thing that moves
 * a row from one status to the next as time passes, and decides which of the
 * three notices in §6.4 is due.
 *
 * Two rules shape everything here:
 *
 * 1. **The status machine always runs; the mail does not.** Moving a lapsed
 *    subscription to `past_due` costs a merchant nothing while
 *    `billing.enforce` is false — `accessVerdict()` never consults the
 *    subscription at all — and it means the dates are already correct on the
 *    day enforcement is switched on. Mail is gated separately on
 *    `billing.dunning`, because an email is the one thing here a merchant who
 *    was promised free access can actually be hurt by.
 *
 * 2. **A trial outranks a period.** Nobody inside their trial is chased, in
 *    any status. Every organization on the platform is backfilled with one, so
 *    in practice this is what keeps the whole mechanism silent today.
 *
 * Deliberately not a payment gateway, and not a step towards one: collection
 * is a manual GCash transfer an operator verifies, which §6.3 settled on
 * 2026-09-20 as the answer rather than a stopgap. `recordPayment()` is simply
 * the one place a subscription becomes paid — which is worth having on its own,
 * and is where something else would attach if that decision is ever revisited.
 *
 * See documentation/subscription-and-suspension.md §6.3–6.4.
 */
class SubscriptionBilling
{
    /** Renewal is coming. The only notice that arrives while nothing is wrong. */
    public const STAGE_RENEWAL = 'renewal_due';

    /** The period ended. Access is unaffected until grace elapses. */
    public const STAGE_PAST_DUE = 'past_due';

    /** Grace is nearly up. The last message before the verdict turns Unpaid. */
    public const STAGE_FINAL = 'final_notice';

    /**
     * The notices in the order they are sent.
     *
     * Order is the whole reason this is a list: a stage is only ever due if it
     * comes strictly after the one already recorded on the row, which is what
     * stops a daily command re-sending the same warning every morning.
     */
    public const STAGES = [self::STAGE_RENEWAL, self::STAGE_PAST_DUE, self::STAGE_FINAL];

    /**
     * Move a subscription on if its paid period has run out.
     *
     * Returns the new status, or null if nothing changed. Idempotent: a
     * subscription already `past_due` is left alone, so the daily command can
     * run any number of times.
     *
     * Only `active` moves. `pending_verification` is our queue rather than the
     * merchant's fault, and `rejected` is an operator's decision that a
     * scheduled command has no business revisiting.
     */
    public function advance(Subscription $subscription): ?string
    {
        if (! $this->wouldAdvance($subscription)) {
            return null;
        }

        $subscription->status = Subscription::STATUS_PAST_DUE;
        $subscription->save();

        return Subscription::STATUS_PAST_DUE;
    }

    /**
     * Whether `advance()` would move this subscription, without moving it.
     *
     * Exists so `--dry-run` can preview the run by asking the same question
     * the real run asks, rather than re-implementing the conditions beside
     * it. A dry run whose logic can drift from the live path is worse than no
     * dry run: it is a preview you would trust and should not.
     */
    public function wouldAdvance(Subscription $subscription): bool
    {
        if ($subscription->status !== Subscription::STATUS_ACTIVE) {
            return false;
        }

        $periodEnd = $subscription->current_period_ends_at;

        // A null period end is an org nothing has ever billed. There is no
        // billing cycle yet, so `active` stands on its own and there is
        // nothing to lapse.
        if ($periodEnd === null || $periodEnd->isFuture()) {
            return false;
        }

        return ! $this->inTrial($subscription);
    }

    /**
     * Which notice is due for this subscription right now, or null for none.
     *
     * Only ever returns a stage later than the one already recorded, so the
     * sequence moves forward and never repeats.
     */
    public function noticeDue(Subscription $subscription): ?string
    {
        if (! $this->mayBeChased($subscription)) {
            return null;
        }

        $stage = $this->currentStage($subscription);

        if ($stage === null) {
            return null;
        }

        $sent = $subscription->dunning_stage;

        if ($sent !== null && $this->rank($stage) <= $this->rank($sent)) {
            return null;
        }

        return $stage;
    }

    /**
     * The furthest-along notice this subscription has earned, ignoring what
     * has already been sent.
     */
    private function currentStage(Subscription $subscription): ?string
    {
        $periodEnd = $subscription->current_period_ends_at;

        if ($periodEnd === null) {
            return null;
        }

        if ($subscription->status === Subscription::STATUS_PAST_DUE) {
            $graceEnd = $this->graceEndsAt($subscription);
            $finalDays = $this->finalNoticeDays();

            if ($graceEnd !== null
                && $finalDays !== null
                && now()->gte($graceEnd->copy()->subDays($finalDays))) {
                return self::STAGE_FINAL;
            }

            return self::STAGE_PAST_DUE;
        }

        if ($subscription->status !== Subscription::STATUS_ACTIVE) {
            return null;
        }

        // Still active, but close enough to the end of the period to warn.
        // `advance()` runs first in the command, so an active row here has a
        // period end that is genuinely still in the future.
        $noticeDays = max(0, (int) config('billing.renewal_notice_days'));

        return now()->gte($periodEnd->copy()->subDays($noticeDays))
            ? self::STAGE_RENEWAL
            : null;
    }

    /**
     * When a past-due subscription stops being current.
     *
     * Mirrors the grace arithmetic in `Subscription::isCurrent()` — if one
     * changes, both must. Null when there is no period to count from.
     */
    public function graceEndsAt(Subscription $subscription): ?CarbonInterface
    {
        $from = $subscription->current_period_ends_at
            ?? $subscription->updated_at
            ?? $subscription->created_at;

        return $from?->copy()->addDays((int) config('billing.grace_days'));
    }

    /**
     * Record that this subscription has been paid for, through a date.
     *
     * **The gateway seam** (§6.3). An operator clicking Verify calls this; a
     * PayMongo or Xendit webhook will call the same method with a date from
     * the payment, and nothing above it has to change.
     *
     * Clears the dunning trail, so a merchant who lapses again later gets the
     * whole sequence from the start rather than resuming at the final notice.
     */
    public function recordPayment(Subscription $subscription, ?CarbonInterface $paidThrough = null): void
    {
        $subscription->status = Subscription::STATUS_ACTIVE;
        // Always now, not the first time: each renewal is its own act of
        // verification, and the operator portal shows this as "verified on".
        $subscription->verified_at = now();
        $subscription->rejection_reason = null;
        $subscription->dunning_stage = null;
        $subscription->dunned_at = null;

        if ($paidThrough !== null) {
            $subscription->current_period_ends_at = Carbon::instance($paidThrough->toDateTime());
        }

        $subscription->save();
    }

    /** Write down that a notice went out, so the next run does not repeat it. */
    public function recordNotice(Subscription $subscription, string $stage): void
    {
        $subscription->dunning_stage = $stage;
        $subscription->dunned_at = now();
        $subscription->save();
    }

    /**
     * Whether this subscription is one we would ever chase.
     *
     * `rejected` is excluded because an operator looked at a payment and said
     * no; that conversation is already happening by other means, and a
     * templated "renew to keep trading" would read as a system that has not
     * noticed. `pending_verification` is excluded because it is our backlog.
     */
    private function mayBeChased(Subscription $subscription): bool
    {
        if ($this->inTrial($subscription)) {
            return false;
        }

        return in_array(
            $subscription->status,
            [Subscription::STATUS_ACTIVE, Subscription::STATUS_PAST_DUE],
            true,
        );
    }

    private function inTrial(Subscription $subscription): bool
    {
        return $subscription->trial_ends_at !== null && $subscription->trial_ends_at->isFuture();
    }

    /**
     * How many days before grace elapses the final notice goes out, or null
     * when the grace window is too short to hold a second notice at all.
     *
     * Two constraints, and they are the reason this is not just a config
     * read. The notice has to land strictly *after* the past-due one, or the
     * merchant gets two emails in one morning saying different things; and
     * strictly *before* grace elapses, or it is a warning about something
     * that has already happened — "your shop stops accepting orders soon",
     * arriving after it stopped.
     *
     * With a grace window of a day or less there is no moment that satisfies
     * both, so there is no separate final notice. That is not a degraded
     * case: the past-due mail already prints the date trading stops, so a
     * merchant on a one-day grace has been told exactly once, with the
     * deadline in it.
     */
    private function finalNoticeDays(): ?int
    {
        $grace = max(0, (int) config('billing.grace_days'));
        $final = max(0, (int) config('billing.final_notice_days'));

        if ($grace < 2 || $final < 1) {
            return null;
        }

        return min($final, $grace - 1);
    }

    private function rank(string $stage): int
    {
        $index = array_search($stage, self::STAGES, true);

        // An unknown stage — a value left by an older release, say — ranks
        // below everything, so the sequence restarts rather than jamming.
        return $index === false ? -1 : $index;
    }
}
