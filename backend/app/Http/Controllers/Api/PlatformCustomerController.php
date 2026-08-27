<?php

namespace App\Http\Controllers\Api;

use App\Http\Controllers\Controller;
use App\Models\CustomerAccount;
use App\Models\Order;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;
use Illuminate\Support\Carbon;

/**
 * The operator's view of the people who shop on the storefront.
 *
 * Shoppers are the one population with no owner: a store sees only the orders
 * placed with it, and a customer's own portal sees only themselves. Nobody
 * could answer "who is this person emailing us about a missing order" without
 * opening a database client. That is what this is for.
 *
 * Read-only on purpose. Everything an operator might want to *change* about a
 * shopper — disabling an account, resetting a password — either needs a column
 * that does not exist yet (`customer_accounts` has no status) or sends mail to
 * a real person, and neither should be added on the way past. Listing and
 * searching is the whole job here.
 *
 * Behind `auth:platform` with the rest of the operator tools; see routes/api.php.
 */
class PlatformCustomerController extends Controller
{
    /**
     * `orders.payment_status` is a plain string column with no constants on the
     * model — see SellerOrderController, which writes 'paid' and 'unpaid'
     * literally. Named here so the two spend queries below cannot drift apart.
     */
    private const PAYMENT_PAID = 'paid';

    /** One screen of customers. Deliberately small — this is a support tool. */
    private const PER_PAGE = 25;

    /** Orders shown on a single customer, newest first. */
    private const ORDER_HISTORY_LIMIT = 20;

    /**
     * The customer list, newest signup first, with the aggregates that make a
     * support conversation possible: how many orders, how much spent, when
     * they were last seen ordering.
     *
     * The counts come from `withCount`/`withSum` rather than a loop over the
     * rows, so the page costs a fixed number of queries no matter how many
     * customers are on it. The same argument PlatformAdminController makes
     * about eager-loading organizations applies here, and this list grows far
     * faster than that one does.
     */
    public function index(Request $request): JsonResponse
    {
        $validated = $request->validate([
            'q' => ['nullable', 'string', 'max:120'],
            'page' => ['nullable', 'integer', 'min:1'],
        ]);

        $search = trim((string) ($validated['q'] ?? ''));

        $query = CustomerAccount::query()
            ->withCount(['orders', 'addresses'])
            // Only completed money counts as "spent" — a cancelled or pending
            // order is not revenue, and showing it as such would misinform the
            // one person who most needs an accurate number in front of them.
            ->withSum([
                'orders as total_spent_cents' => fn ($q) => $q->where('payment_status', self::PAYMENT_PAID),
            ], 'total_cents')
            ->withMax('orders as last_order_at', 'created_at');

        if ($search !== '') {
            // Grouped so the search alternatives cannot escape any filter added
            // to this query later — an ungrouped `orWhere` chain is the classic
            // way a scoped list quietly starts returning everything.
            $query->where(function ($builder) use ($search) {
                $like = '%'.addcslashes($search, '%_\\').'%';

                $builder->where('name', 'like', $like)
                    ->orWhere('email', 'like', $like)
                    ->orWhere('phone', 'like', $like);
            });
        }

        $customers = $query
            ->orderByDesc('created_at')
            ->paginate(self::PER_PAGE, ['*'], 'page', $validated['page'] ?? null);

        return response()->json([
            'customers' => $customers->getCollection()->map(fn (CustomerAccount $c) => $this->summary($c))->all(),
            'pagination' => [
                'page' => $customers->currentPage(),
                'perPage' => $customers->perPage(),
                'total' => $customers->total(),
                'lastPage' => $customers->lastPage(),
            ],
        ]);
    }

    /**
     * One customer in full: their addresses, their order history, and how they
     * pay. Enough to resolve a support ticket without a database client.
     */
    public function show(CustomerAccount $customer): JsonResponse
    {
        $customer->loadCount(['orders', 'addresses']);
        $customer->loadMax('orders as last_order_at', 'created_at');

        $orders = $customer->orders()
            ->with('store:id,name')
            ->orderByDesc('created_at')
            ->limit(self::ORDER_HISTORY_LIMIT)
            ->get();

        $totalSpentCents = (int) $customer->orders()
            ->where('payment_status', self::PAYMENT_PAID)
            ->sum('total_cents');

        return response()->json([
            'customer' => $this->summary($customer, $totalSpentCents) + [
                'addresses' => $customer->addresses()
                    ->orderByDesc('is_default')
                    ->get()
                    ->map(fn ($a) => [
                        'id' => $a->id,
                        'label' => $a->label,
                        'line1' => $a->line1,
                        'barangay' => $a->barangay,
                        'city' => $a->city,
                        'notes' => $a->notes,
                        'isDefault' => (bool) $a->is_default,
                    ])->all(),

                /*
                 * Kind and last-four only. A payment method's `detail` is what
                 * the shopper typed, and an operator has no reason to read it
                 * back in full to answer a question about an order.
                 */
                'paymentMethods' => $customer->paymentMethods()
                    ->orderByDesc('is_default')
                    ->get()
                    ->map(fn ($m) => [
                        'id' => $m->id,
                        'kind' => $m->kind,
                        'detail' => $this->maskDetail($m->detail),
                        'isDefault' => (bool) $m->is_default,
                    ])->all(),

                'orders' => $orders->map(fn (Order $o) => [
                    'id' => $o->id,
                    'ticketNumber' => $o->ticket_number,
                    'storeName' => $o->store?->name,
                    'status' => $o->order_status,
                    'paymentStatus' => $o->payment_status,
                    'fulfillmentMethod' => $o->fulfillment_method,
                    'totalCents' => (int) $o->total_cents,
                    'placedAt' => $o->created_at?->toIso8601String(),
                ])->all(),
            ],
        ]);
    }

    /**
     * The shape the list and the detail screen share, so a customer never
     * looks like two different records depending on which one you opened.
     *
     * @return array<string, mixed>
     */
    private function summary(CustomerAccount $customer, ?int $totalSpentCents = null): array
    {
        // `withMax`/`loadMax` hand back the driver's raw datetime string rather
        // than a Carbon instance, so it is parsed here instead of being cast on
        // the model — the attribute only exists on queries that asked for it.
        $lastOrderAt = $customer->last_order_at ?? null;

        return [
            'id' => $customer->id,
            'name' => $customer->name,
            'email' => $customer->email,
            'phone' => $customer->phone,
            // Unverified is the single most useful flag here: it explains why
            // someone cannot sign in, which is most of what support gets asked.
            'emailVerified' => $customer->email_verified_at !== null,
            'ordersCount' => (int) ($customer->orders_count ?? 0),
            'addressesCount' => (int) ($customer->addresses_count ?? 0),
            'totalSpentCents' => $totalSpentCents ?? (int) ($customer->total_spent_cents ?? 0),
            'lastOrderAt' => $lastOrderAt ? Carbon::parse($lastOrderAt)->toIso8601String() : null,
            'createdAt' => $customer->created_at?->toIso8601String(),
        ];
    }

    /** Leaves enough to recognise a card, not enough to use one. */
    private function maskDetail(?string $detail): ?string
    {
        if ($detail === null || $detail === '') {
            return null;
        }

        return strlen($detail) <= 4 ? $detail : str_repeat('•', 4).' '.substr($detail, -4);
    }
}
