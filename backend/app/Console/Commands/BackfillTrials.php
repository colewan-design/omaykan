<?php

namespace App\Console\Commands;

use App\Models\Organization;
use App\Models\PlatformSetting;
use App\Models\Subscription;
use Illuminate\Console\Command;
use Illuminate\Support\Carbon;

/**
 * Write early access down, for every organization that signed up before it
 * was a column.
 *
 * This is the step that makes `BILLING_ENFORCE=true` safe. Every existing
 * merchant was told Omaykan was free; until they have a `trial_ends_at`, the
 * only thing standing between them and a locked till on the day enforcement is
 * switched on is that the subscription happens to be `pending_verification`.
 * That is an accident, not a promise. After this runs it is a date.
 *
 * What it does, per organization:
 *
 * - **No subscription at all** — the seeded default org, and anything created
 *   outside signup — gets a pending one at the current plan price, with the
 *   trial. With enforcement on, a missing subscription reads as unpaid.
 * - **A trial ending before `--until`**, or none, is extended to it.
 * - **A trial already ending later is left alone.** Never shortens.
 * - **Rejected subscriptions are reported, not touched.** An operator looked at
 *   those and said no; overriding that is a decision for a person, and the
 *   list is printed so one can make it.
 *
 * Idempotent: running it twice with the same date changes nothing the second
 * time. `--dry-run` prints the plan and writes nothing, and is what to run
 * first against production. There is no second chance at a backfill.
 */
class BackfillTrials extends Command
{
    protected $signature = 'billing:backfill-trials
        {--until= : The trial end date to grant (YYYY-MM-DD). Defaults to billing.trial_days from today}
        {--dry-run : Report what would change and write nothing}';

    protected $description = 'Give every existing organization an early-access trial end date';

    public function handle(): int
    {
        $until = $this->untilDate();

        if ($until === null) {
            return self::FAILURE;
        }

        $dryRun = (bool) $this->option('dry-run');
        $plan = PlatformSetting::current()->planSettings();

        $created = [];
        $extended = [];
        $untouched = 0;
        $rejected = [];

        Organization::query()->with('subscription')->orderBy('slug')->each(
            function (Organization $organization) use ($until, $dryRun, $plan, &$created, &$extended, &$untouched, &$rejected) {
                $subscription = $organization->subscription;

                if ($subscription === null) {
                    $created[] = $organization->slug;

                    if (! $dryRun) {
                        Subscription::query()->create([
                            'organization_id' => $organization->id,
                            'status' => Subscription::STATUS_PENDING,
                            'plan' => $plan['id'],
                            'amount_cents' => (int) $plan['amountCents'],
                            'gcash_reference' => '',
                            'submitted_at' => now(),
                            'trial_ends_at' => $until,
                        ]);
                    }

                    return;
                }

                if ($subscription->status === Subscription::STATUS_REJECTED) {
                    $rejected[] = $organization->slug;

                    return;
                }

                if ($subscription->trial_ends_at !== null && $subscription->trial_ends_at->gte($until)) {
                    $untouched++;

                    return;
                }

                $extended[] = $organization->slug;

                if (! $dryRun) {
                    $subscription->forceFill(['trial_ends_at' => $until])->save();
                }
            },
        );

        $this->info(($dryRun ? '[dry run] ' : '').'Trial end: '.$until->toDateString());
        $this->table(['', 'Organizations'], [
            ['Subscription created', count($created)],
            ['Trial set or extended', count($extended)],
            ['Already later — left alone', $untouched],
            ['Rejected — left alone', count($rejected)],
        ]);

        if ($created !== []) {
            $this->line('Created: '.implode(', ', $created));
        }

        if ($rejected !== []) {
            // These are the ones that will read as unpaid the moment
            // enforcement is on. Someone should look before that happens.
            $this->warn('Rejected, and will be unpaid once billing is enforced: '.implode(', ', $rejected));
        }

        return self::SUCCESS;
    }

    private function untilDate(): ?Carbon
    {
        $option = $this->option('until');

        if ($option === null || $option === '') {
            return now()->addDays((int) config('billing.trial_days'))->endOfDay();
        }

        try {
            $until = Carbon::createFromFormat('!Y-m-d', (string) $option)->endOfDay();
        } catch (\Throwable) {
            $until = false;
        }

        if ($until === false || $until->isPast()) {
            $this->error("--until must be a future date as YYYY-MM-DD; got \"{$option}\".");

            return null;
        }

        return $until;
    }
}
