<?php

namespace App\Http\Controllers\Api;

use App\Http\Controllers\Controller;
use App\Models\Order;
use App\Services\OrderProgress;
use App\Services\PlatformInsights;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;
use Illuminate\Support\Carbon;
use Illuminate\Validation\Rule;

/**
 * Every order on the platform, in one table.
 *
 * A shop sees its own orders on its dashboard and a customer sees their own in
 * their account. Nobody could see all of them at once, which made "is anything
 * stuck?" a question only a database client could answer.
 *
 * Read-only, and it stays that way. Advancing an order is the shop's job and
 * cancelling one is a refund conversation; both have owners, and an operator
 * reaching past them from here would be acting as a shop without the shop
 * knowing. Looking is the whole job.
 *
 * Behind `auth:platform` with the rest of the operator tools; see routes/api.php.
 */
class PlatformOrderController extends Controller
{
    /** One screen of orders. */
    private const PER_PAGE = 25;

    /** What the date filter offers, in days. `0` means every order ever. */
    private const RANGES = [7, 30, 90, 0];

    public function index(Request $request, PlatformInsights $insights): JsonResponse
    {
        $validated = $request->validate([
            'q' => ['nullable', 'string', 'max:120'],
            'progress' => ['nullable', Rule::in(OrderProgress::ALL)],
            'days' => ['nullable', Rule::in(self::RANGES)],
            'page' => ['nullable', 'integer', 'min:1'],
        ]);

        $days = (int) ($validated['days'] ?? 30);
        $progress = $validated['progress'] ?? null;
        $search = trim((string) ($validated['q'] ?? ''));
        $since = $days > 0 ? Carbon::now()->subDays($days)->startOfDay() : null;

        // Voided orders are in scope for every tab, because "all orders" that
        // silently omits the cancelled ones is a count an operator will later
        // try to reconcile against the shop's own. The progress filter then
        // narrows to one stage, cancelled included.
        $query = Order::query()
            ->withTrashed()
            ->with(['customerAccount:id,name', 'store:id,name'])
            ->withCount('items');

        if ($since !== null) {
            $query->where('orders.created_at', '>=', $since);
        }

        if ($progress !== null) {
            OrderProgress::scope($query, $progress);
        }

        if ($search !== '') {
            // Grouped so the alternatives cannot escape the filters above — an
            // ungrouped orWhere chain is how a filtered list starts quietly
            // returning the whole table.
            $query->where(function ($builder) use ($search) {
                $like = '%'.addcslashes($search, '%_\\').'%';

                $builder->where('ticket_number', 'like', $like)
                    ->orWhereHas('customerAccount', fn ($q) => $q->where('name', 'like', $like))
                    ->orWhereHas('store', fn ($q) => $q->where('name', 'like', $like));
            });
        }

        $page = $query->latest('orders.created_at')->paginate(self::PER_PAGE, ['*'], 'page', $validated['page'] ?? 1);

        return response()->json([
            'orders' => collect($page->items())->map(fn (Order $order) => [
                'id' => $order->id,
                'ticketNumber' => $order->ticket_number,
                'customerName' => $insights->customerNameFor($order),
                'storeName' => $order->store?->name,
                'items' => (int) $order->items_count,
                'totalCents' => (int) $order->total_cents,
                'progress' => OrderProgress::of($order),
                'paymentStatus' => $order->payment_status ?? 'unpaid',
                'fulfillmentMethod' => $order->fulfillment_method,
                'createdAt' => $order->created_at?->toIso8601String(),
            ])->values(),
            // Scoped to the date range but not to the search: a tab has to
            // promise what clicking it will show, and the range is a scope the
            // operator set for the whole screen — but a tab reading
            // "Cancelled (0)" because of an unrelated search would lie about
            // what it holds. See PlatformInsights::byProgress.
            'counts' => $insights->byProgress($since),
            'pagination' => [
                'page' => $page->currentPage(),
                'perPage' => $page->perPage(),
                'total' => $page->total(),
                'lastPage' => $page->lastPage(),
            ],
        ]);
    }
}
