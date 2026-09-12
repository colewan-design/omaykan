<?php

namespace App\Http\Controllers\Api;

use App\Http\Controllers\Concerns\ActsForAStore;
use App\Http\Controllers\Controller;
use App\Models\Conversation;
use App\Models\Order;
use App\Services\StoreContext;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;

/**
 * The shop's side of customer messages.
 *
 * Scoped to the store the session signed in to, like every seller endpoint:
 * another shop's conversation is a 404, not a 403, so ids cannot be probed.
 *
 * A shop answers; it does not start. There is no endpoint here to open a
 * conversation with a customer, because the only customers a shop could find
 * to message are the ones who have ordered from it, and "we have a sale on"
 * landing unasked in the inbox of everybody who ever bought a coffee is the
 * thing that would teach customers to stop reading it.
 */
class SellerConversationController extends Controller
{
    use ActsForAStore;

    private const LIST_LIMIT = 100;

    private const THREAD_LIMIT = 200;

    /** How much of the customer's history with this shop rides along. */
    private const RECENT_ORDERS = 5;

    public function index(Request $request): JsonResponse
    {
        $context = $this->storeContext($request);

        $rows = $this->scoped($context)
            ->whereNotNull('last_message_at')
            ->with(['customerAccount', 'latestMessage.order', 'latestMessage.author'])
            ->orderByDesc('last_message_at')
            ->limit(self::LIST_LIMIT)
            ->get();

        return response()->json([
            'conversations' => $rows->map(fn (Conversation $c) => $c->toStoreSummary())->values(),
        ]);
    }

    /** What the nav badge polls. */
    public function unread(Request $request): JsonResponse
    {
        $total = $this->scoped($this->storeContext($request))->sum('store_unread');

        return response()->json(['unread' => (int) $total]);
    }

    public function show(Request $request, string $conversation): JsonResponse
    {
        $context = $this->storeContext($request);
        $row = $this->scoped($context)->findOrFail($conversation);

        $row->markReadBy(Conversation::SENDER_STORE);

        return response()->json($this->thread($row));
    }

    public function reply(Request $request, string $conversation): JsonResponse
    {
        $context = $this->storeContext($request);
        $row = $this->scoped($context)->findOrFail($conversation);

        $data = $request->validate([
            'body' => ['required', 'string', 'max:'.Conversation::MAX_BODY],
        ]);

        $row->post(Conversation::SENDER_STORE, $data['body'], author: $context->user);

        return response()->json($this->thread($row), 201);
    }

    private function scoped(StoreContext $context)
    {
        return Conversation::query()
            ->where('organization_id', $context->organizationId())
            ->where('store_id', $context->storeId());
    }

    /**
     * The thread, plus this customer's last few orders *at this shop*.
     *
     * "Where's my order" is most of what a shop will be asked, and the answer
     * is on a different page. Putting the recent tickets beside the thread
     * saves the counter a trip to the dashboard to find out which one. Only
     * this shop's orders: what the customer bought elsewhere is not its
     * business.
     *
     * @return array<string, mixed>
     */
    private function thread(Conversation $conversation): array
    {
        $messages = $conversation->messages()
            ->with(['order', 'author'])
            ->orderByDesc('id')
            ->limit(self::THREAD_LIMIT)
            ->get()
            ->reverse();

        $orders = Order::query()
            ->online()
            ->where('store_id', $conversation->store_id)
            ->where('customer_account_id', $conversation->customer_account_id)
            ->latest()
            ->limit(self::RECENT_ORDERS)
            ->get()
            ->map(fn (Order $order) => [
                'id' => $order->id,
                'ticketNumber' => $order->ticket_number,
                'status' => $order->order_status,
                'deliveryStage' => $order->delivery_stage,
                'fulfillmentMethod' => $order->fulfillment_method,
                'totalCents' => (int) $order->total_cents,
                'createdAt' => $order->created_at?->toIso8601String(),
            ])
            ->values();

        return [
            'conversation' => $conversation->toStoreSummary(),
            'messages' => $messages->map->toStoreArray()->values(),
            'recentOrders' => $orders,
        ];
    }
}
