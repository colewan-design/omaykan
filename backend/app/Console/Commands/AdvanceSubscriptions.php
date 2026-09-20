<?php

namespace App\Console\Commands;

use App\Mail\SubscriptionDunningMail;
use App\Models\Organization;
use App\Models\OrganizationMembership;
use App\Models\Subscription;
use App\Services\Billing\SubscriptionBilling;
use Illuminate\Console\Command;
use Illuminate\Support\Facades\Mail;

/**
 * Move lapsed subscriptions on, and send the three notices in §6.4.
 *
 * Scheduled daily in routes/console.php. Safe to miss and safe to repeat: the
 * status transition is idempotent, and a notice is recorded on the row the
 * moment it is queued, so a second run the same morning sends nothing.
 *
 * Why a scheduled command rather than computing this when a request arrives:
 * a merchant's state changing is a thing that happened, at a time, and it
 * wants a log line and a timestamp. Working it out on the fly means nobody can
 * answer "when did this shop go past due?" afterwards — and the answer is the
 * first thing asked when a merchant disputes it.
 *
 * `--dry-run` prints what it would do and writes nothing, which is how this
 * should be run against production the first time. See the backfill command,
 * which takes the same flag for the same reason.
 */
class AdvanceSubscriptions extends Command
{
    protected $signature = 'billing:advance-subscriptions {--dry-run : Print what would change and write nothing}';

    protected $description = 'Move lapsed subscriptions to past_due and send renewal notices';

    public function handle(SubscriptionBilling $billing): int
    {
        $dryRun = (bool) $this->option('dry-run');
        $dunningOn = (bool) config('billing.dunning');

        if (! $dunningOn) {
            $this->line('Dunning mail is off (BILLING_DUNNING). Statuses still advance.');
        }

        $advanced = 0;
        $mailed = 0;
        $skipped = 0;

        Subscription::query()
            ->with('organization')
            ->whereIn('status', [Subscription::STATUS_ACTIVE, Subscription::STATUS_PAST_DUE])
            ->orderBy('created_at')
            ->each(function (Subscription $subscription) use ($billing, $dryRun, $dunningOn, &$advanced, &$mailed, &$skipped) {
                $organization = $subscription->organization;

                if ($organization === null) {
                    return;
                }

                $label = $organization->slug;

                // The transition first: a subscription that lapses today
                // should earn the past-due notice on the same run, not
                // tomorrow's.
                if ($dryRun) {
                    if ($billing->wouldAdvance($subscription)) {
                        $advanced++;
                        $this->line("  would advance {$label} -> past_due");

                        // Applied in memory and never saved, so the notice
                        // preview below sees the status the real run would
                        // have reached — otherwise a shop lapsing today would
                        // be previewed as a renewal reminder and sent a
                        // past-due notice.
                        $subscription->status = Subscription::STATUS_PAST_DUE;
                    }
                } elseif ($billing->advance($subscription) !== null) {
                    $advanced++;
                    $this->line("  {$label} -> past_due");
                }

                $stage = $billing->noticeDue($subscription);

                if ($stage === null) {
                    return;
                }

                if (! $dunningOn) {
                    $skipped++;

                    return;
                }

                if ($dryRun) {
                    $mailed++;
                    $this->line("  would mail {$label}: {$stage}");

                    return;
                }

                if ($this->mail($organization, $subscription, $stage)) {
                    $billing->recordNotice($subscription, $stage);
                    $mailed++;
                    $this->line("  mailed {$label}: {$stage}");
                }
            });

        $this->info($dryRun
            ? "Dry run: {$advanced} would advance, {$mailed} would be mailed."
            : "Advanced {$advanced}, mailed {$mailed}.");

        if ($skipped > 0) {
            $this->line("{$skipped} notice(s) were due but not sent, because dunning is off.");
        }

        return self::SUCCESS;
    }

    /**
     * Mail the organization's owner. Returns whether it was queued, so a
     * failure does not get recorded as a notice the merchant has had.
     *
     * An org with no admin membership is a data problem rather than a billing
     * one — it is reported and skipped, because inventing a recipient is
     * worse than telling nobody.
     */
    private function mail(Organization $organization, Subscription $subscription, string $stage): bool
    {
        $owner = OrganizationMembership::query()
            ->where('organization_id', $organization->id)
            ->where('membership_role', 'admin')
            ->with('user')
            ->orderBy('created_at')
            ->first()?->user;

        if ($owner === null || ! is_string($owner->email) || trim($owner->email) === '') {
            $this->warn("  {$organization->slug}: no owner email, skipped");

            return false;
        }

        try {
            Mail::to($owner->email)->queue(
                new SubscriptionDunningMail($owner, $organization, $subscription, $stage)
            );

            return true;
        } catch (\Throwable $e) {
            // One unreachable mailbox must not stop the rest of the run, and
            // must not be written down as a notice that was delivered.
            report($e);
            $this->warn("  {$organization->slug}: mail failed, will retry tomorrow");

            return false;
        }
    }
}
