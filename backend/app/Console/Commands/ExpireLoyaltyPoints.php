<?php

namespace App\Console\Commands;

use App\Models\LoyaltyEntry;
use App\Models\PosCustomer;
use App\Services\Loyalty\Loyalty;
use Illuminate\Console\Command;

/**
 * Write off points that have passed their expiry date. Scheduled daily in
 * routes/console.php; safe to run any number of times, because a customer
 * with nothing left to expire gets no new entry. See Loyalty::expire.
 */
class ExpireLoyaltyPoints extends Command
{
    protected $signature = 'loyalty:expire';

    protected $description = 'Write off loyalty points past their expiry date';

    public function handle(Loyalty $loyalty): int
    {
        $expired = 0;
        $customers = 0;

        $ids = LoyaltyEntry::query()
            ->where('reason', LoyaltyEntry::EARN)
            ->whereNotNull('expires_at')
            ->where('expires_at', '<=', now())
            ->distinct()
            ->pluck('pos_customer_id');

        PosCustomer::withTrashed()->whereIn('id', $ids)->each(function (PosCustomer $customer) use ($loyalty, &$expired, &$customers) {
            $points = $loyalty->expire($customer);
            if ($points > 0) {
                $expired += $points;
                $customers++;
            }
        });

        $this->info("Expired {$expired} point(s) across {$customers} customer(s).");

        return self::SUCCESS;
    }
}
