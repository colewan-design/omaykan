<?php

namespace App\Http\Controllers\Api;

use App\Http\Controllers\Controller;
use App\Models\CustomerAccount;
use App\Models\Order;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;

/**
 * "My orders" — the thing an account is worth having for.
 *
 * Before accounts this could not exist: an order was addressable only by its
 * own UUID, so the browser had to keep a list of ids and a new phone knew
 * nothing. Now the question is answerable server-side, and the answer follows
 * the person rather than the device.
 *
 * Only orders placed while signed in appear. An order placed as a guest has no
 * `customer_account_id` and stays reachable the way it always was, through its
 * id — matching it up by email afterwards would mean handing someone every
 * order ever placed by anyone who typed that address at checkout.
 */
class CustomerOrderController extends Controller
{
    /** Enough history to be useful without paginating a portal page. */
    private const LIMIT = 50;

    public function index(Request $request): JsonResponse
    {
        /** @var CustomerAccount $account */
        $account = $request->user();

        $orders = Order::query()
            ->online()
            ->where('customer_account_id', $account->getKey())
            ->with('items')
            ->latest()
            ->limit(self::LIMIT)
            ->get();

        return response()->json([
            'orders' => $orders->map(fn (Order $order) => $order->toTrackedArray())->all(),
        ]);
    }

    /**
     * One of the customer's own orders, by id.
     *
     * The public tracking endpoint would answer this too, but going through
     * the account means a signed-in customer asking for someone else's order
     * gets a 404 rather than a tracking page.
     */
    public function show(Request $request, string $order): JsonResponse
    {
        /** @var CustomerAccount $account */
        $account = $request->user();

        $row = Order::query()
            ->online()
            ->where('customer_account_id', $account->getKey())
            ->with('items')
            ->findOrFail($order);

        return response()->json($row->toTrackedArray());
    }
}
