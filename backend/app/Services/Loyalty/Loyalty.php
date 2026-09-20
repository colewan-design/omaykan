<?php

namespace App\Services\Loyalty;

use App\Models\LoyaltyEntry;
use App\Models\LoyaltyProgram;
use App\Models\Order;
use App\Models\PosCustomer;
use Illuminate\Support\Carbon;

/**
 * Points: earning them on a sale, spending them as a discount, and letting
 * old ones expire. The ledger rules in one place.
 *
 * Points are spent oldest first. That is what makes expiry a single sum
 * rather than a lot-by-lot reckoning: whatever was earned before the cutoff,
 * less everything ever spent or expired, is what is left of the old points —
 * and that is what expires.
 *
 * See documentation/merchant-features.md §9.
 */
class Loyalty
{
    public function balance(PosCustomer $customer): int
    {
        return (int) $customer->loyaltyEntries()->sum('points');
    }

    /**
     * Points for this sale, replacing any it had: re-sending an order never
     * earns twice.
     *
     * Earned on what the customer paid for before VAT — the subtotal less any
     * discount, points spent included. Only for a customer who agreed to be
     * enrolled, and only while the programme is on.
     */
    public function earnFor(Order $order): void
    {
        $customer = $order->pos_customer_id ? PosCustomer::query()->find($order->pos_customer_id) : null;

        LoyaltyEntry::query()
            ->where('order_id', $order->id)
            ->where('reason', LoyaltyEntry::EARN)
            ->delete();

        if ($customer === null || $customer->loyalty_consent_at === null) {
            return;
        }

        $program = LoyaltyProgram::for($order->organization_id);
        if (! $program->enabled || $program->spend_cents_per_point <= 0) {
            return;
        }

        $base = max(0, (int) $order->subtotal_cents - (int) $order->discount_cents);
        $points = intdiv($base, $program->spend_cents_per_point);

        if ($points <= 0) {
            return;
        }

        LoyaltyEntry::query()->create([
            'organization_id' => $order->organization_id,
            'pos_customer_id' => $customer->id,
            'order_id' => $order->id,
            'reason' => LoyaltyEntry::EARN,
            'points' => $points,
            'note' => 'Order '.$order->ticket_number,
            'expires_at' => $program->expiry_months
                ? ($order->completed_at ?? now())->copy()->addMonths($program->expiry_months)
                : null,
        ]);
    }

    /**
     * Whether `$points` can come off a sale of this size, and what they are
     * worth. Worth is capped at the subtotal, and only the points that cap
     * needs are spent.
     *
     * @return array{ok: true, points: int, discountCents: int}|array{ok: false, message: string}
     */
    public function quote(PosCustomer $customer, int $points, int $subtotalCents, ?string $excludingOrderId = null): array
    {
        $program = LoyaltyProgram::for($customer->organization_id);

        if (! $program->enabled) {
            return ['ok' => false, 'message' => 'Points are switched off for this shop.'];
        }

        if ($customer->loyalty_consent_at === null) {
            return ['ok' => false, 'message' => "{$customer->name} isn't enrolled in points."];
        }

        if ($points < $program->min_redeem_points) {
            return ['ok' => false, 'message' => "At least {$program->min_redeem_points} points at a time."];
        }

        $balance = (int) $customer->loyaltyEntries()
            ->when($excludingOrderId, fn ($query) => $query->where(
                fn ($q) => $q->whereNull('order_id')->orWhere('order_id', '!=', $excludingOrderId),
            ))
            ->sum('points');

        if ($points > $balance) {
            return ['ok' => false, 'message' => "{$customer->name} has {$balance} points."];
        }

        $value = max(1, $program->point_value_cents);
        $discount = min($points * $value, max(0, $subtotalCents));
        $spent = (int) ceil($discount / $value);

        return ['ok' => true, 'points' => $spent, 'discountCents' => $discount];
    }

    /**
     * Record the points a synced sale spent, replacing any it had recorded.
     * Returns whether the balance could cover it — a sale that has already
     * happened is recorded either way.
     */
    public function recordRedemption(Order $order, PosCustomer $customer, int $points, ?string $userId): bool
    {
        LoyaltyEntry::query()
            ->where('order_id', $order->id)
            ->where('reason', LoyaltyEntry::REDEEM)
            ->delete();

        // Against the balance without this order's own entries — neither the
        // points it earned (earned after it was paid) nor what it spent on a
        // previous push of the same order.
        $covered = $this->quote($customer, $points, PHP_INT_MAX, $order->id)['ok'];

        LoyaltyEntry::query()->create([
            'organization_id' => $order->organization_id,
            'pos_customer_id' => $customer->id,
            'order_id' => $order->id,
            'reason' => LoyaltyEntry::REDEEM,
            'points' => -$points,
            'note' => 'Order '.$order->ticket_number,
            'created_by' => $userId,
        ]);

        return $covered;
    }

    /**
     * Write off what is left of points earned before their expiry. Idempotent:
     * run twice, the second run finds nothing left to expire.
     */
    public function expire(PosCustomer $customer, ?Carbon $now = null): int
    {
        $now ??= now();

        $expiredEarned = (int) $customer->loyaltyEntries()
            ->where('reason', LoyaltyEntry::EARN)
            ->whereNotNull('expires_at')
            ->where('expires_at', '<=', $now)
            ->sum('points');

        $consumed = -1 * (int) $customer->loyaltyEntries()->where('points', '<', 0)->sum('points');

        $toExpire = $expiredEarned - $consumed;

        if ($toExpire <= 0) {
            return 0;
        }

        LoyaltyEntry::query()->create([
            'organization_id' => $customer->organization_id,
            'pos_customer_id' => $customer->id,
            'reason' => LoyaltyEntry::EXPIRE,
            'points' => -$toExpire,
            'note' => 'Expired',
        ]);

        return $toExpire;
    }
}
