<?php

namespace App\Services;

use App\Models\Order;
use App\Models\OrderItem;
use App\Models\Organization;
use App\Models\Payment;
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

    /** Rows on the audit-friendly reports transaction list. */
    private const REPORT_RECENT_LIMIT = 6;

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
        $averageOrderValue = $orders > 0 ? (int) round($sales / $orders) : 0;
        $previousAverageOrderValue = $previousOrders > 0 ? (int) round($previousSales / $previousOrders) : 0;
        $customersServed = $this->paidOrders()
            ->whereBetween('created_at', [$from, $to])
            ->whereNotNull('customer_account_id')
            ->distinct()
            ->count('customer_account_id');
        $previousCustomersServed = $this->paidOrders()
            ->whereBetween('created_at', [$previousFrom, $previousTo])
            ->whereNotNull('customer_account_id')
            ->distinct()
            ->count('customer_account_id');

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
            'averageOrderValueCents' => $averageOrderValue,
            'averageOrderValueChangePercent' => $this->changePercent($averageOrderValue, $previousAverageOrderValue),
            'customersServed' => $customersServed,
            'customersServedChangePercent' => $this->changePercent($customersServed, $previousCustomersServed),
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

    /** Repeat shoppers among paid customer accounts active in this window. */
    public function returningCustomers(Carbon $from, Carbon $to): array
    {
        $current = $this->paidOrders()
            ->whereBetween('created_at', [$from, $to])
            ->whereNotNull('customer_account_id')
            ->distinct()
            ->pluck('customer_account_id');

        $total = $current->count();
        $returning = $total === 0 ? 0 : $this->paidOrders()
            ->whereIn('customer_account_id', $current)
            ->where('created_at', '<', $from)
            ->distinct()
            ->count('customer_account_id');

        return [
            'customers' => $returning,
            'total' => $total,
            'percent' => $total > 0 ? round(($returning / $total) * 100, 1) : 0,
        ];
    }

    /** The products that contributed the most settled revenue in the window. */
    public function topProducts(Carbon $from, Carbon $to): array
    {
        return OrderItem::query()
            ->join('orders', 'orders.id', '=', 'order_items.order_id')
            ->leftJoin('products', 'products.id', '=', 'order_items.product_id')
            ->whereNull('orders.deleted_at')
            ->where('orders.payment_status', self::PAID)
            ->whereBetween('orders.created_at', [$from, $to])
            ->groupBy('order_items.product_id', 'order_items.product_name')
            ->orderByDesc('revenue_cents')
            ->limit(5)
            ->get([
                'order_items.product_id',
                DB::raw('coalesce(max(products.name), order_items.product_name) as label'),
                DB::raw("max(nullif(products.image_url, '')) as image_url"),
                DB::raw('sum(order_items.line_total_cents) as revenue_cents'),
                DB::raw('sum(order_items.quantity) as quantity'),
            ])
            ->map(fn ($row) => [
                'id' => $row->product_id,
                'label' => (string) $row->label,
                'imageUrl' => $row->image_url,
                'revenueCents' => (int) $row->revenue_cents,
                'quantity' => (float) $row->quantity,
            ])
            ->all();
    }

    /** Order volume by local hour, including zeroes so the bar axis is stable. */
    public function ordersByHour(Carbon $from, Carbon $to): array
    {
        $counts = array_fill(0, 24, 0);

        Order::query()
            ->whereBetween('created_at', [$from, $to])
            ->get(['created_at'])
            ->each(function (Order $order) use (&$counts) {
                $hour = (int) $order->created_at->copy()->setTimezone(config('app.timezone', 'UTC'))->format('G');
                $counts[$hour]++;
            });

        return collect($counts)->map(fn ($value, $hour) => [
            'hour' => (int) $hour,
            'orders' => $value,
        ])->values()->all();
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
     * Accounting-oriented headline figures for the reports screen.
     *
     * Gross is the merchandise subtotal before discounts; net removes those
     * discounts and excludes tax. Collected is intentionally not presented as
     * net sales because an order total can also contain tax or delivery fees.
     */
    public function reportHeadline(Carbon $from, Carbon $to): array
    {
        $length = max(1, $from->diffInDays($to) + 1);
        $previousTo = $from->copy()->subDay()->endOfDay();
        $previousFrom = $previousTo->copy()->subDays($length - 1)->startOfDay();
        $current = $this->reportTotals($from, $to);
        $previous = $this->reportTotals($previousFrom, $previousTo);

        return [
            ...$current,
            'grossSalesChangePercent' => $this->changePercent($current['grossSalesCents'], $previous['grossSalesCents']),
            'netSalesChangePercent' => $this->changePercent($current['netSalesCents'], $previous['netSalesCents']),
            'taxChangePercent' => $this->changePercent($current['taxCents'], $previous['taxCents']),
            'discountChangePercent' => $this->changePercent($current['discountCents'], $previous['discountCents']),
            'ordersChangePercent' => $this->changePercent($current['orders'], $previous['orders']),
            'averageOrderValueChangePercent' => $this->changePercent($current['averageOrderValueCents'], $previous['averageOrderValueCents']),
        ];
    }

    /** @return array{grossSalesCents:int,netSalesCents:int,collectedCents:int,taxCents:int,discountCents:int,orders:int,averageOrderValueCents:int} */
    private function reportTotals(Carbon $from, Carbon $to): array
    {
        $row = $this->paidOrders()
            ->whereBetween('created_at', [$from, $to])
            ->selectRaw('coalesce(sum(subtotal_cents), 0) as gross_cents')
            ->selectRaw('coalesce(sum(discount_cents), 0) as discount_cents')
            ->selectRaw('coalesce(sum(tax_cents), 0) as tax_cents')
            ->selectRaw('coalesce(sum(total_cents), 0) as collected_cents')
            ->selectRaw('count(*) as orders')
            ->first();

        $gross = (int) ($row?->gross_cents ?? 0);
        $discounts = (int) ($row?->discount_cents ?? 0);
        $orders = (int) ($row?->orders ?? 0);
        $net = max(0, $gross - $discounts);

        return [
            'grossSalesCents' => $gross,
            'netSalesCents' => $net,
            'collectedCents' => (int) ($row?->collected_cents ?? 0),
            'taxCents' => (int) ($row?->tax_cents ?? 0),
            'discountCents' => $discounts,
            'orders' => $orders,
            'averageOrderValueCents' => $orders > 0 ? (int) round($net / $orders) : 0,
        ];
    }

    /** Net sales after discounts, with a zero for every day in the window. */
    public function reportSeries(Carbon $from, Carbon $to): array
    {
        $rows = $this->paidOrders()
            ->whereBetween('created_at', [$from, $to])
            ->groupBy('day')
            ->get([
                DB::raw('date(created_at) as day'),
                DB::raw('sum(subtotal_cents - discount_cents) as cents'),
            ])
            ->keyBy(fn ($row) => (string) $row->day);

        $points = [];
        for ($day = $from->copy()->startOfDay(); $day->lte($to); $day->addDay()) {
            $key = $day->toDateString();
            $points[] = ['date' => $key, 'salesCents' => (int) ($rows[$key]->cents ?? 0)];
        }

        return $points;
    }

    /** Collected totals grouped by the tender actually recorded for each order. */
    public function paymentsByMethod(Carbon $from, Carbon $to): array
    {
        $totals = [];

        Payment::query()
            ->join('orders', 'orders.id', '=', 'payments.order_id')
            ->whereNull('orders.deleted_at')
            ->where('orders.payment_status', self::PAID)
            ->whereBetween('orders.created_at', [$from, $to])
            ->groupBy('payments.payment_method')
            ->get(['payments.payment_method', DB::raw('sum(payments.amount_cents) as cents')])
            ->each(function ($row) use (&$totals) {
                $key = $this->paymentLabel((string) $row->payment_method);
                $totals[$key] = ($totals[$key] ?? 0) + (int) $row->cents;
            });

        $this->paidOrders()
            ->whereBetween('created_at', [$from, $to])
            ->whereDoesntHave('payments')
            ->groupBy('payment_method')
            ->get(['payment_method', DB::raw('sum(total_cents) as cents')])
            ->each(function ($row) use (&$totals) {
                $key = $this->paymentLabel((string) ($row->payment_method ?? ''));
                $totals[$key] = ($totals[$key] ?? 0) + (int) $row->cents;
            });

        arsort($totals);

        return collect($totals)->map(fn ($cents, $label) => [
            'label' => $label,
            'value' => $cents,
        ])->values()->all();
    }

    /** Net sales grouped by how the order was fulfilled. */
    public function salesByBusinessMode(Carbon $from, Carbon $to): array
    {
        return $this->paidOrders()
            ->whereBetween('created_at', [$from, $to])
            ->groupBy('mode')
            ->orderByDesc('cents')
            ->get([
                DB::raw("coalesce(nullif(business_mode, ''), nullif(fulfillment_method, ''), nullif(order_type, ''), 'unspecified') as mode"),
                DB::raw('sum(subtotal_cents - discount_cents) as cents'),
                DB::raw('count(*) as orders'),
            ])
            ->map(fn ($row) => [
                'label' => str((string) $row->mode)->replace('_', ' ')->headline()->toString(),
                'value' => (int) $row->cents,
                'orders' => (int) $row->orders,
            ])
            ->all();
    }

    /** Paid order volume by local hour, including zeroes for a stable axis. */
    public function paidOrdersByHour(Carbon $from, Carbon $to): array
    {
        $counts = array_fill(0, 24, 0);
        $this->paidOrders()->whereBetween('created_at', [$from, $to])->get(['created_at'])
            ->each(function (Order $order) use (&$counts) {
                $hour = (int) $order->created_at->copy()->setTimezone(config('app.timezone', 'UTC'))->format('G');
                $counts[$hour]++;
            });

        return collect($counts)->map(fn ($orders, $hour) => ['hour' => (int) $hour, 'orders' => $orders])->values()->all();
    }

    /** The latest paid records inside the selected report window. */
    public function recentTransactions(Carbon $from, Carbon $to): array
    {
        return $this->paidOrders()
            ->with(['store:id,name', 'payments:id,order_id,payment_method'])
            ->withCount('items')
            ->whereBetween('created_at', [$from, $to])
            ->latest('created_at')
            ->limit(self::REPORT_RECENT_LIMIT)
            ->get()
            ->map(fn (Order $order) => [
                'id' => $order->id,
                'ticketNumber' => $order->ticket_number,
                'storeName' => $order->store?->name,
                'items' => (int) $order->items_count,
                'totalCents' => (int) $order->total_cents,
                'paymentMethod' => $this->paymentLabel((string) ($order->payment_method ?: $order->payments->first()?->payment_method)),
                'createdAt' => $order->created_at?->toIso8601String(),
            ])
            ->all();
    }

    private function paymentLabel(string $method): string
    {
        return match (strtolower(trim($method))) {
            'cash', 'cod', 'cash_on_delivery' => 'Cash',
            'gcash' => 'GCash',
            'maya', 'paymaya' => 'Maya',
            'card', 'credit_card', 'debit_card' => 'Card',
            'bank', 'bank_transfer' => 'Bank transfer',
            'ewallet', 'e-wallet' => 'E-wallet',
            default => $method !== '' ? str($method)->replace('_', ' ')->headline()->toString() : 'Unspecified',
        };
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
