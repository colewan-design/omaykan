<?php

namespace App\Services\Billing;

use App\Models\SubscriptionPayment;
use Carbon\Carbon;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\Log;

/**
 * Turn a PayMongo checkout into a paid period, exactly once.
 *
 * ## Why this re-reads instead of trusting the notification
 *
 * The webhook proves only that PayMongo sent us something. What it says is
 * then thrown away: this fetches the session back with our own secret key and
 * believes that instead. It costs one HTTP call and it means a change in
 * PayMongo's payload shape can cost us a missed notification, never a wrong
 * one — and a subscription marked paid when it was not is the expensive
 * mistake here, not a late one.
 *
 * It also makes the return-from-GCash path and the webhook path the same
 * code. Whichever arrives first settles; the second finds it done.
 *
 * ## Exactly once
 *
 * PayMongo retries until it gets a 2xx, and a merchant can reload the success
 * page as often as they like. Both funnel through here, and the row is locked
 * and re-checked inside the transaction, so a second arrival extends nothing.
 */
class GatewaySettlement
{
    public function __construct(
        private readonly PayMongoGateway $gateway,
        private readonly SubscriptionBilling $billing,
    ) {}

    /**
     * Settle the checkout behind `$sessionId`, if it has been paid.
     *
     * Returns true when this call is the one that recorded it. False means
     * either nothing to do yet or somebody got there first — both of which
     * are ordinary, and neither of which is an error.
     */
    public function settle(string $sessionId): bool
    {
        $pending = SubscriptionPayment::query()
            ->where('provider_session_id', $sessionId)
            ->first();

        if ($pending === null) {
            // A session we never opened. Not ours to act on, and saying so
            // beats inventing a subscription to attach it to.
            Log::warning('PayMongo settlement for an unknown session.', ['session' => $sessionId]);

            return false;
        }

        if ($pending->status === SubscriptionPayment::STATUS_ACCEPTED) {
            return false;
        }

        $session = $this->gateway->readCheckout($sessionId);

        if ($session === null) {
            Log::warning('PayMongo settlement could not read the session back.', ['session' => $sessionId]);

            return false;
        }

        $payment = $this->gateway->settledPayment($session);

        if ($payment === null) {
            // Opened but not paid — an abandoned checkout, or a notification
            // that arrived before the payment settled. Leave it submitted.
            return false;
        }

        return DB::transaction(function () use ($pending, $payment) {
            $row = SubscriptionPayment::query()
                ->whereKey($pending->getKey())
                ->lockForUpdate()
                ->first();

            if ($row === null || $row->status === SubscriptionPayment::STATUS_ACCEPTED) {
                return false;
            }

            $subscription = $row->subscription;

            if ($subscription === null) {
                return false;
            }

            /*
             * A month from wherever the paid period currently ends, not from
             * today — so paying early adds a month rather than losing the
             * remainder of the one already bought.
             */
            $from = $subscription->current_period_ends_at !== null
                && $subscription->current_period_ends_at->isFuture()
                    ? $subscription->current_period_ends_at
                    : Carbon::now();

            $periodEnd = $from->copy()->addMonth();

            $row->status = SubscriptionPayment::STATUS_ACCEPTED;
            $row->provider_payment_id = (string) ($payment['id'] ?? '');
            $row->period_start = Carbon::now();
            $row->period_end = $periodEnd;
            $row->reviewed_at = Carbon::now();
            $row->save();

            // The seam SubscriptionBilling already documented: the same call
            // an operator's Verify click makes, with a date from the payment.
            $this->billing->recordPayment($subscription, $periodEnd);

            Log::info('PayMongo payment settled a subscription.', [
                'subscription' => $subscription->id,
                'payment' => $row->provider_payment_id,
                'paid_through' => $periodEnd->toDateString(),
            ]);

            return true;
        });
    }
}
