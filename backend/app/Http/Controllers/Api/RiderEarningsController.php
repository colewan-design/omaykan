<?php

namespace App\Http\Controllers\Api;

use App\Http\Controllers\Controller;
use App\Models\Order;
use App\Models\Rider;
use Carbon\CarbonImmutable;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;
use Illuminate\Support\Collection;

/**
 * What the rider has actually earned.
 *
 * The gap this fills: `GET /api/rider/deliveries` returns the last thirty
 * finished jobs, which is a history and not accounting. A rider asking "how
 * much did I make this week" could only get an answer by counting cards, and
 * once past thirty jobs they could not get one at all. Both clients were
 * summing `completed` in the browser — see the `earnedTodayCents` computed the
 * portal used to carry — which is a total of whatever happened to be on screen.
 *
 * Aggregated in the database rather than by loading orders: a rider two years
 * in has thousands of them, and none of the numbers below needs a single row
 * in memory.
 *
 * **The whole fee is the rider's.** There is no platform cut to subtract here
 * and the wording says so, because that is the product's actual position — see
 * documentation/positioning.md.
 */
class RiderEarningsController extends Controller
{
    /**
     * Where the day starts, and it is not where `config('app.timezone')` says.
     *
     * The application runs in UTC, which puts a day boundary at 8am in Baguio —
     * so a rider's "today" would reset in the middle of the breakfast run and a
     * night shift would be split across two days. Earnings are the one figure a
     * person checks against their own memory of a day, so they are bucketed in
     * the timezone the day was worked in.
     */
    private const TIMEZONE = 'Asia/Manila';

    /** Enough to see a shape without turning a summary into a report. */
    private const TREND_DAYS = 14;

    public function summary(Request $request): JsonResponse
    {
        /** @var Rider $rider */
        $rider = $request->user();

        $now = CarbonImmutable::now(self::TIMEZONE);
        $startOfToday = $now->startOfDay();

        return response()->json([
            'currency' => 'PHP',
            'today' => $this->totalsSince($rider, $startOfToday),
            'week' => $this->totalsSince($rider, $now->startOfWeek()),
            'month' => $this->totalsSince($rider, $now->startOfMonth()),
            'allTime' => $this->totalsSince($rider, null),
            'days' => $this->trend($rider, $startOfToday),
        ]);
    }

    /**
     * Finished work only.
     *
     * A job at `assigned` or `picked_up` is money the rider is going to make,
     * not money they have made, and a screen that counted it would go *down*
     * when a job was handed back. `delivered` is the only stage that has been
     * paid for.
     *
     * @return \Illuminate\Database\Eloquent\Builder<Order>
     */
    private function delivered(Rider $rider)
    {
        return Order::query()
            ->where('rider_id', $rider->id)
            ->where('delivery_stage', 'delivered');
    }

    /**
     * Bucketed by when the job was *taken*, not when it was marked delivered.
     *
     * There is no delivered-at column — `mine()` sorts history by `updated_at`,
     * which any later edit to the order moves. `rider_accepted_at` is written
     * once, by the accept, and never again. It is also the timestamp the rider
     * remembers, since it is when they were standing outside the shop.
     *
     * @return array<string, int>
     */
    private function totalsSince(Rider $rider, ?CarbonImmutable $since): array
    {
        $query = $this->delivered($rider);

        if ($since !== null) {
            $query->where('rider_accepted_at', '>=', $since->utc());
        }

        $row = $query->selectRaw('count(*) as jobs, coalesce(sum(delivery_fee_cents), 0) as fees')->first();

        return [
            'jobs' => (int) ($row->jobs ?? 0),
            'feeCents' => (int) ($row->fees ?? 0),
        ];
    }

    /**
     * One row per day for the last fortnight, including the days with nothing
     * on them — a chart with the empty days missing is a chart that lies about
     * how busy a week was.
     *
     * @return array<int, array<string, mixed>>
     */
    private function trend(Rider $rider, CarbonImmutable $startOfToday): array
    {
        $from = $startOfToday->subDays(self::TREND_DAYS - 1);

        /** @var Collection<string, object> $rows */
        $rows = $this->delivered($rider)
            ->where('rider_accepted_at', '>=', $from->utc())
            ->get(['rider_accepted_at', 'delivery_fee_cents'])
            ->groupBy(fn (Order $order) => $order->rider_accepted_at
                ->setTimezone(self::TIMEZONE)
                ->format('Y-m-d'));

        $days = [];

        for ($offset = 0; $offset < self::TREND_DAYS; $offset++) {
            $day = $from->addDays($offset);
            $key = $day->format('Y-m-d');
            $onThatDay = $rows->get($key);

            $days[] = [
                'date' => $key,
                'jobs' => $onThatDay?->count() ?? 0,
                'feeCents' => (int) ($onThatDay?->sum('delivery_fee_cents') ?? 0),
            ];
        }

        return $days;
    }
}
