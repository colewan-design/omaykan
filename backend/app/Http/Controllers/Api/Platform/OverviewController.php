<?php

namespace App\Http\Controllers\Api\Platform;

use App\Http\Controllers\Controller;
use App\Models\Order;
use App\Models\Organization;
use App\Models\Rider;
use App\Models\Subscription;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\DB;

/**
 * The screen the portal opens on.
 *
 * The old dashboard opened onto a table of every tenant, which meant an
 * operator had to read rows to find out whether there was anything to do. What
 * this answers instead, in order: what is waiting on me, is anything stuck,
 * and is the platform growing.
 *
 * Every figure here comes from a table that already exists. Nothing is
 * denormalised or cached — at this size the aggregates are cheap, and a stale
 * count on a work queue is worse than a slow one. The `channel, business_date`
 * index added with the platform_admins migration is what keeps the volume
 * queries off a full scan.
 */
class OverviewController extends Controller
{
    private const TREND_DAYS = 30;

    /** How many of the oldest waiting signups to name outright. */
    private const QUEUE_PREVIEW = 5;

    public function show(Request $request): JsonResponse
    {
        return response()->json([
            'queues' => $this->queues(),
            'tenants' => $this->tenants(),
            'revenue' => $this->subscriptionRevenue(),
            'volume' => $this->volume(),
            'topTenants' => $this->topTenants(),
        ]);
    }

    /**
     * What is waiting on a human. The oldest few signups are named, not just
     * counted: "3 pending" and "3 pending, one for eleven days" are different
     * situations, and only the second one is urgent.
     *
     * @return array<string, mixed>
     */
    private function queues(): array
    {
        $pending = Subscription::query()
            ->where('status', Subscription::STATUS_PENDING)
            ->with('organization')
            ->orderBy('submitted_at')
            ->get();

        return [
            'pendingSignups' => $pending->count(),
            'pendingRiders' => Rider::query()->where('status', Rider::STATUS_PENDING)->count(),
            'oldestSignups' => $pending
                ->filter(fn (Subscription $s) => $s->organization !== null)
                ->take(self::QUEUE_PREVIEW)
                ->map(fn (Subscription $s) => [
                    'organizationSlug' => $s->organization->slug,
                    'organizationName' => $s->organization->name,
                    'amountCents' => $s->amount_cents,
                    'gcashReference' => $s->gcash_reference,
                    'submittedAt' => $s->submitted_at?->toIso8601String(),
                    // Whole days, floored: Carbon 3 returns a float here,
                    // and "waiting 0.0000013 days" is not a queue age.
                    'waitingDays' => $s->submitted_at === null
                        ? null
                        : (int) $s->submitted_at->diffInDays(now()),
                ])->values(),
        ];
    }

    /**
     * @return array<string, mixed>
     */
    private function tenants(): array
    {
        return [
            'total' => Organization::query()->count(),
            'suspended' => Organization::query()->where('suspended', true)->count(),
            'newLast7Days' => Organization::query()->where('created_at', '>=', now()->subDays(7))->count(),
            'newLast30Days' => Organization::query()->where('created_at', '>=', now()->subDays(30))->count(),
        ];
    }

    /**
     * Recurring revenue, such as it is. A proxy, not an accounting figure:
     * collection is a manual GCash transfer, so this is the sum of what
     * verified merchants agreed to pay, not what actually arrived this month.
     *
     * @return array<string, mixed>
     */
    private function subscriptionRevenue(): array
    {
        $byStatus = Subscription::query()
            ->selectRaw('status, count(*) as subscription_count, coalesce(sum(amount_cents), 0) as amount_cents')
            ->groupBy('status')
            ->get()
            ->keyBy('status');

        $active = $byStatus->get(Subscription::STATUS_ACTIVE);

        return [
            'activeSubscriptions' => (int) ($active->subscription_count ?? 0),
            'monthlyCents' => (int) ($active->amount_cents ?? 0),
            'rejected' => (int) ($byStatus->get(Subscription::STATUS_REJECTED)->subscription_count ?? 0),
        ];
    }

    /**
     * Platform-wide order volume for the trend window, by day and by channel.
     *
     * Known overcount: a full-order void is a client-side concept
     * (`voidedAt` in packages/core's store) that the Laravel schema does not
     * carry yet — there is no `voided_at` column and SyncController does not
     * write one, so a voided order is still a row here. Deliberately not
     * worked around with a guess; when the void reaches the schema, filter it
     * here and in OrganizationController::show together.
     *
     * @return array<string, mixed>
     */
    private function volume(): array
    {
        $since = now()->subDays(self::TREND_DAYS)->toDateString();

        $rows = Order::query()
            ->where('business_date', '>=', $since)
            ->selectRaw('business_date, channel, count(*) as order_count, coalesce(sum(total_cents), 0) as revenue_cents')
            ->groupBy('business_date', 'channel')
            ->orderBy('business_date')
            ->get();

        // Days are emitted only where there was activity. A sparse series is
        // the honest shape for a platform this young, and the chart fills the
        // gaps rather than the query inventing zero rows.
        $byDay = $rows
            // business_date is a date column, but the driver hands it back
            // with a midnight time attached — the chart axis prints this, so
            // it is trimmed here rather than in the client.
            ->groupBy(fn ($row) => substr((string) $row->business_date, 0, 10))
            ->map(fn ($dayRows, $date) => [
                'date' => $date,
                'orders' => (int) $dayRows->sum('order_count'),
                'revenueCents' => (int) $dayRows->sum('revenue_cents'),
                'pos' => (int) $dayRows->where('channel', 'pos')->sum('order_count'),
                'online' => (int) $dayRows->where('channel', 'online')->sum('order_count'),
            ])
            ->values();

        return [
            'days' => self::TREND_DAYS,
            'orders' => (int) $rows->sum('order_count'),
            'revenueCents' => (int) $rows->sum('revenue_cents'),
            'posOrders' => (int) $rows->where('channel', 'pos')->sum('order_count'),
            'onlineOrders' => (int) $rows->where('channel', 'online')->sum('order_count'),
            'byDay' => $byDay,
        ];
    }

    /**
     * The busiest tenants over the trend window — the accounts whose problems
     * are the platform's problems.
     *
     * @return \Illuminate\Support\Collection<int, array<string, mixed>>
     */
    private function topTenants()
    {
        $since = now()->subDays(self::TREND_DAYS)->toDateString();

        $rows = Order::query()
            ->where('business_date', '>=', $since)
            ->selectRaw('organization_id, count(*) as order_count, coalesce(sum(total_cents), 0) as revenue_cents')
            ->groupBy('organization_id')
            ->orderByDesc(DB::raw('count(*)'))
            ->limit(10)
            ->get();

        $names = Organization::query()
            ->whereIn('id', $rows->pluck('organization_id'))
            ->get()
            ->keyBy('id');

        return $rows
            ->filter(fn ($row) => $names->has($row->organization_id))
            ->map(fn ($row) => [
                'organizationSlug' => $names[$row->organization_id]->slug,
                'organizationName' => $names[$row->organization_id]->name,
                'orders' => (int) $row->order_count,
                'revenueCents' => (int) $row->revenue_cents,
            ])->values();
    }
}
