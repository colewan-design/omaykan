<?php

namespace App\Services;

use App\Models\Order;
use App\Models\OrderItem;
use App\Models\Organization;
use App\Models\Product;
use Illuminate\Database\Eloquent\Builder;
use Illuminate\Support\Carbon;
use Illuminate\Support\Collection;
use Illuminate\Support\Facades\DB;

/**
 * The numbers behind the operator's dashboard and analytics screens.
 *
 * Both screens ask the same questions of the same tables over different
 * windows — what came in, how many orders, which categories sold, where they
 * went. Keeping the queries here rather than in two controllers means the
 * headline on the dashboard and the headline on analytics cannot answer the
 * same question differently, which is the failure that makes an operator stop
 * trusting both screens at once.
 *
 * ## Two rules every number here obeys
 *
 * **Revenue counts paid orders only.** `payment_status = 'paid'`, the same
 * literal PlatformCustomerController uses and SellerOrderController writes. An
 * unpaid order is a promise, and a dashboard that totals promises as income is
 * worse than one that shows nothing.
 *
 * **Voided orders are excluded everywhere except the cancelled count.** They
 * are soft-deleted, so the default Eloquent scope already drops them; the one
 * place that wants them says `withTrashed()` out loud.
 *
 * ## Why the series are built in PHP and not in SQL
 *
 * A `group by date` returns only the days that had orders. A chart needs every
 * day in the window, including the empty ones — a 30-day line with four points
 * in it is a lie about the shape of the business. So the grouped rows are
 * indexed and then walked across a generated date range, which also keeps the
 * query portable rather than reaching for a database-specific series function.
 */
class PlatformInsights
{
    /** The one literal that means money actually arrived. */
    public const PAID = 'paid';

    /** Rows on the dashboard's "recent orders" list. */
    private const RECENT_LIMIT = 5;

    /** Locations and categories worth drawing before the tail is folded up. */
    private const BREAKDOWN_LIMIT = 7;

    /** Trailing address parts that are not the town. */
    private const COUNTRY_WORDS = ['philippines', 'ph', 'pilipinas'];

    /**
     * Orders in scope for money questions: placed, not voided, actually paid.
     *
     * Every revenue figure on both screens starts here, so "what counts as
     * revenue" is one line of code rather than a convention.
     */
    private function paidOrders(): Builder
    {
        return Order::query()->where('payment_status', self::PAID);
    }

    /**
     * The four headline tiles, each with the change against the window of the
     * same length immediately before it.
     *
     * The comparison window is derived from the current one rather than fixed
     * at "last month", so the delta under a 7-day view means "versus the
     * previous 7 days" and not something the label does not say.
     */
    public function headline(Carbon $from, Carbon $to): array
    {
        $length = max(1, $from->diffInDays($to) + 1);
        $previousTo = $from->copy()->subDay()->endOfDay();
        $previousFrom = $previousTo->copy()->subDays($length - 1)->startOfDay();

        $sales = $this->salesCents($from, $to);
        $previousSales = $this->salesCents($previousFrom, $previousTo);

        $orders = $this->orderCount($from, $to);
        $previousOrders = $this->orderCount($previousFrom, $previousTo);

        return [
            'salesCents' => $sales,
            'salesChangePercent' => $this->changePercent($sales, $previousSales),
            'orders' => $orders,
            'ordersChangePercent' => $this->changePercent($orders, $previousOrders),
            // Not windowed: "how many products are for sale" is a fact about
            // now, and a change-versus-last-week on it would be meaningless
            // beside two figures that are windowed.
            'activeProducts' => Product::query()->where('is_active', true)->count(),
            'activeSellers' => Organization::query()->whereHas(
                'stores',
                fn ($q) => $q->where('status', 'active'),
            )->count(),
            'averageOrderValueCents' => $orders > 0 ? (int) round($sales / $orders) : 0,
            'customersServed' => $this->paidOrders()
                ->whereBetween('created_at', [$from, $to])
                ->whereNotNull('customer_account_id')
                ->distinct()
                ->count('customer_account_id'),
        ];
    }

    private function salesCents(Carbon $from, Carbon $to): int
    {
        return (int) $this->paidOrders()->whereBetween('created_at', [$from, $to])->sum('total_cents');
    }

    /** Every order placed in the window, paid or not — this is volume, not money. */
    private function orderCount(Carbon $from, Carbon $to): int
    {
        return Order::query()->whereBetween('created_at', [$from, $to])->count();
    }

    /**
     * Percentage change, or null when there is nothing to compare against.
     *
     * Null rather than 0 or 100: a period with no previous activity has no
     * percentage change, and inventing one ("+100%") is the kind of number
     * that ends up in a pitch deck.
     */
    private function changePercent(int|float $current, int|float $previous): ?float
    {
        if ($previous <= 0) {
            return null;
        }

        return round((($current - $previous) / $previous) * 100, 1);
    }

    /**
     * Daily takings and order counts across the whole window, one point per
     * day whether or not anything happened on it.
     */
    public function dailySeries(Carbon $from, Carbon $to): array
    {
        $paid = $this->paidOrders()
            ->whereBetween('created_at', [$from, $to])
            ->groupBy('day')
            ->get([
                DB::raw('date(created_at) as day'),
                DB::raw('sum(total_cents) as cents'),
            ])
            ->keyBy(fn ($row) => (string) $row->day);

        $counts = Order::query()
            ->whereBetween('created_at', [$from, $to])
            ->groupBy('day')
            ->get([
                DB::raw('date(created_at) as day'),
                DB::raw('count(*) as orders'),
            ])
            ->keyBy(fn ($row) => (string) $row->day);

        $points = [];

        for ($day = $from->copy()->startOfDay(); $day->lte($to); $day->addDay()) {
            $key = $day->toDateString();

            $points[] = [
                'date' => $key,
                'salesCents' => (int) ($paid[$key]->cents ?? 0),
                'orders' => (int) ($counts[$key]->orders ?? 0),
            ];
        }

        return $points;
    }

    /**
     * How many orders sit at each of the four stages.
     *
     * `$since` scopes the counts to the same window the caller is listing. The
     * order table's tabs pass it and the dashboard does not, and the difference
     * matters: a tab has to promise what clicking it will show. Counting all
     * time while the list below is filtered to a month puts "All orders 206"
     * above "Showing 1–25 of 108", which reads as a bug even though both
     * numbers are true. The dashboard's split has no list under it and no date
     * filter beside it, so there it is the whole table on purpose — "what needs
     * attention right now" is not a question about last month.
     *
     * A search term is deliberately *not* passed here by anyone. A tab that
     * reads "Cancelled (0)" because you are searching for something else is a
     * tab that lies about what it contains.
     */
    public function byProgress(?Carbon $since = null): array
    {
        $counts = [];

        foreach (OrderProgress::ALL as $progress) {
            $query = Order::query()->withTrashed();

            if ($since !== null) {
                $query->where('created_at', '>=', $since);
            }

            $counts[$progress] = OrderProgress::scope($query, $progress)->count();
        }

        return $counts;
    }

    /**
     * Takings per product category, largest first, with everything past the
     * drawable few folded into one "Other" row.
     *
     * Folding rather than drawing an eleventh slice is deliberate: past about
     * seven, categories stop being distinguishable and the chart becomes a
     * legend with a decoration attached.
     *
     * Joined through `order_items` rather than read off the order, because an
     * order spans categories — attributing its whole total to one of them
     * would overstate whichever category happened to be first in the basket.
     */
    public function salesByCategory(Carbon $from, Carbon $to): array
    {
        $rows = OrderItem::query()
            ->join('orders', 'orders.id', '=', 'order_items.order_id')
            ->leftJoin('products', 'products.id', '=', 'order_items.product_id')
            ->leftJoin('categories', 'categories.id', '=', 'products.category_id')
            ->whereNull('orders.deleted_at')
            ->where('orders.payment_status', self::PAID)
            ->whereBetween('orders.created_at', [$from, $to])
            ->groupBy('label')
            ->orderByDesc('cents')
            ->get([
                DB::raw("coalesce(categories.name, 'Uncategorised') as label"),
                DB::raw('sum(order_items.line_total_cents) as cents'),
            ]);

        return $this->foldTail($rows->map(fn ($row) => [
            'label' => (string) $row->label,
            'cents' => (int) $row->cents,
        ]));
    }

    /**
     * Where the orders went, by the town on the delivering shop's own record.
     *
     * The shop's town, not the delivery address: `orders.delivery_address` is
     * a free-text line a customer typed, so grouping on it produces one bucket
     * per spelling. The store's `address` is set once by its owner, and every
     * order on this platform is delivered from the shop that took it — near
     * enough for a distribution, and honest about being an approximation.
     */
    public function ordersByLocation(Carbon $from, Carbon $to): array
    {
        $rows = Order::query()
            ->join('stores', 'stores.id', '=', 'orders.store_id')
            ->whereBetween('orders.created_at', [$from, $to])
            ->groupBy('label')
            ->orderByDesc('orders')
            ->get([
                DB::raw("coalesce(nullif(stores.address, ''), stores.name) as label"),
                DB::raw('count(*) as orders'),
            ]);

        return $this->foldTail($rows->map(fn ($row) => [
            // An address is a whole line ("Session Road, Baguio City"); the
            // chart wants the town out of it. See townFrom for which part
            // that is, and why the whole string is the fallback.
            'label' => $this->townFrom((string) $row->label),
            'cents' => (int) $row->orders,
        ]));
    }

    /**
     * The town out of a one-line address.
     *
     * The last comma-separated part, after any trailing country is dropped:
     * both address shapes this platform actually holds put the town last —
     * "12 Session Road, Baguio City" and "SM City Baguio, Luneta Hill, Baguio".
     * An address with no comma is already a place name, or is too unstructured
     * to take apart, and is returned whole rather than guessed at.
     */
    private function townFrom(string $address): string
    {
        $parts = array_values(array_filter(array_map('trim', explode(',', $address)), 'strlen'));

        while (count($parts) > 1 && in_array(strtolower(end($parts)), self::COUNTRY_WORDS, true)) {
            array_pop($parts);
        }

        return $parts === [] ? $address : end($parts);
    }

    /**
     * Keep the largest few rows and sum the rest into "Other".
     *
     * Re-groups after the label rewrite above, so two stores in the same town
     * become one bar rather than two identical ones.
     */
    private function foldTail(Collection $rows): array
    {
        $merged = [];

        foreach ($rows as $row) {
            $merged[$row['label']] = ($merged[$row['label']] ?? 0) + $row['cents'];
        }

        arsort($merged);

        $kept = array_slice($merged, 0, self::BREAKDOWN_LIMIT, true);
        $tail = array_slice($merged, self::BREAKDOWN_LIMIT, null, true);

        $out = [];

        foreach ($kept as $label => $value) {
            $out[] = ['label' => $label, 'value' => $value];
        }

        if ($tail !== []) {
            $out[] = ['label' => 'Other', 'value' => array_sum($tail)];
        }

        return $out;
    }

    /**
     * The last few orders, for the dashboard's tail. Voided ones included and
     * labelled — an order that has just been cancelled is exactly the kind of
     * thing the person looking at this screen wants to see.
     */
    public function recentOrders(): array
    {
        return Order::query()
            ->withTrashed()
            ->with(['customerAccount:id,name', 'items:id,order_id'])
            ->latest('created_at')
            ->limit(self::RECENT_LIMIT)
            ->get()
            ->map(fn (Order $order) => [
                'id' => $order->id,
                'ticketNumber' => $order->ticket_number,
                'customerName' => $this->customerNameFor($order),
                'items' => $order->items->count(),
                'totalCents' => (int) $order->total_cents,
                'progress' => OrderProgress::of($order),
                'createdAt' => $order->created_at?->toIso8601String(),
            ])
            ->all();
    }

    /**
     * Who placed it: the account if there was one, the guest details if not.
     *
     * Guest checkout is the common case on this storefront, so falling back to
     * "Walk-in" the way the till does would label most of the table wrong.
     */
    public function customerNameFor(Order $order): string
    {
        if ($order->customerAccount?->name) {
            return $order->customerAccount->name;
        }

        $guest = is_array($order->guest_contact) ? $order->guest_contact : [];

        return $guest['name'] ?? 'Guest';
    }
}
