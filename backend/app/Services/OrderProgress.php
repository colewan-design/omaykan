<?php

namespace App\Services;

use App\Models\Order;
use Illuminate\Database\Eloquent\Builder;

/**
 * The four words the operator portal uses for where an order has got to.
 *
 * ## Why this exists
 *
 * An order's progress is spread across three columns that were each added for
 * a different screen. `order_status` is the kitchen's (`preparing`, `ready`,
 * `served`); `delivery_stage` is the rider's (`pending`, `assigned`,
 * `picked_up`, `delivered`); and a cancelled order is not a status at all, it
 * is a soft delete. A till and a rider app each read the one they care about
 * and ignore the rest, which is fine for them.
 *
 * The operator looks at every order on the platform at once — kitchen orders,
 * delivery orders, voided orders, in one table — and needs one vocabulary
 * across all of them. That vocabulary is defined here, exactly once, in both
 * the SQL that filters a list and the PHP that labels a row, so the tab count
 * and the pill on the row can never disagree.
 *
 * ## The mapping, and what it concedes
 *
 * | Reads as    | When |
 * |-------------|------|
 * | `cancelled` | the order is soft-deleted — a void, whatever else it said |
 * | `completed` | served at the counter, or delivered to the door |
 * | `shipped`   | a rider has it: assigned or picked up |
 * | `processing`| everything else — it exists and nobody has finished it |
 *
 * `processing` is deliberately the fallback rather than a fourth explicit set.
 * A row with a status nothing here recognises is still an order somebody is
 * waiting on, and showing it as "processing" is wrong in a way an operator can
 * see and act on; dropping it from every tab is wrong in a way nobody sees.
 *
 * Cancelled is checked first because a voided order can carry any other
 * column's value — voiding does not rewind `delivery_stage`, so a rider who
 * had already collected a since-cancelled order would otherwise keep it
 * reading as `shipped` forever.
 */
class OrderProgress
{
    public const PROCESSING = 'processing';

    public const SHIPPED = 'shipped';

    public const COMPLETED = 'completed';

    public const CANCELLED = 'cancelled';

    /** Tab order in the portal: the sequence an order moves through, then voids. */
    public const ALL = [self::PROCESSING, self::SHIPPED, self::COMPLETED, self::CANCELLED];

    /** `order_status` values that mean the counter is finished with it. */
    private const SERVED_STATUSES = ['served', 'completed'];

    /** `delivery_stage` values that mean a rider is carrying it. */
    private const RIDER_STAGES = ['assigned', 'picked_up'];

    /**
     * Where one order has got to. Mirrors {@see self::scope()} — change both.
     */
    public static function of(Order $order): string
    {
        if ($order->deleted_at !== null) {
            return self::CANCELLED;
        }

        if (in_array($order->order_status, self::SERVED_STATUSES, true)
            || $order->delivery_stage === 'delivered') {
            return self::COMPLETED;
        }

        if (in_array($order->delivery_stage, self::RIDER_STAGES, true)) {
            return self::SHIPPED;
        }

        return self::PROCESSING;
    }

    /**
     * Narrow a query to one of the four. Mirrors {@see self::of()}.
     *
     * The caller supplies a query that has already decided whether voided
     * orders are in scope at all — `withTrashed()` for the cancelled tab and
     * for "all", the default for the rest. This method only adds the column
     * conditions, so it cannot quietly widen what the caller asked for.
     */
    public static function scope(Builder $query, string $progress): Builder
    {
        return match ($progress) {
            self::CANCELLED => $query->whereNotNull('deleted_at'),

            self::COMPLETED => $query->whereNull('deleted_at')->where(
                fn (Builder $q) => $q
                    ->whereIn('order_status', self::SERVED_STATUSES)
                    ->orWhere('delivery_stage', 'delivered'),
            ),

            self::SHIPPED => $query->whereNull('deleted_at')
                ->whereNotIn('order_status', self::SERVED_STATUSES)
                ->whereIn('delivery_stage', self::RIDER_STAGES),

            // The fallback, expressed as "none of the above" so that the four
            // scopes partition the table rather than overlapping.
            self::PROCESSING => $query->whereNull('deleted_at')
                ->whereNotIn('order_status', self::SERVED_STATUSES)
                ->where(
                    fn (Builder $q) => $q
                        ->whereNull('delivery_stage')
                        ->orWhereNotIn('delivery_stage', [...self::RIDER_STAGES, 'delivered']),
                ),

            default => $query,
        };
    }
}
