<?php

namespace App\Http\Controllers\Api;

use App\Http\Controllers\Controller;
use App\Models\Conversation;
use App\Models\CustomerAccount;
use App\Models\Order;
use App\Models\Store;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;
use Illuminate\Validation\ValidationException;

/**
 * The shopper's side of messaging a shop.
 *
 * Only signed-in customers: a message needs somewhere for the answer to go,
 * and a guest has no inbox. Guest checkout is untouched by any of this.
 *
 * Every thread is found through the caller's own account, so somebody else's
 * conversation id is a 404 exactly like a made-up one.
 */
class CustomerConversationController extends Controller
{
    private const LIST_LIMIT = 50;

    /** The most recent end of a thread. Older lines are still stored. */
    private const THREAD_LIMIT = 200;

    public function index(Request $request): JsonResponse
    {
        $rows = Conversation::query()
            ->where('customer_account_id', $this->account($request)->getKey())
            ->whereNotNull('last_message_at')
            ->with(['store.organization', 'latestMessage.order'])
            ->orderByDesc('last_message_at')
            ->limit(self::LIST_LIMIT)
            ->get();

        return response()->json([
            'conversations' => $rows->map(fn (Conversation $c) => $c->toCustomerSummary())->values(),
        ]);
    }

    /** What the header badge polls. One number, one indexed sum. */
    public function unread(Request $request): JsonResponse
    {
        $total = Conversation::query()
            ->where('customer_account_id', $this->account($request)->getKey())
            ->sum('customer_unread');

        return response()->json(['unread' => (int) $total]);
    }

    /**
     * Message a shop — the first time, or again.
     *
     * The shop is named one of two ways, matching the two places the button
     * lives: by `orgSlug` + `storeCode` from a shop's own page, or by `orderId`
     * from an order. The order's UUID is the same capability the public
     * tracking link runs on, so any online order the customer can name will do
     * — including one they placed as a guest before they had an account.
     *
     * Lands in the existing conversation with that shop when there is one.
     */
    public function start(Request $request): JsonResponse
    {
        $data = $request->validate([
            'body' => ['required', 'string', 'max:'.Conversation::MAX_BODY],
            'orderId' => ['nullable', 'uuid'],
            'orgSlug' => ['required_without:orderId', 'nullable', 'string'],
            'storeCode' => ['required_without:orderId', 'nullable', 'string'],
        ]);

        $order = null;

        if (! empty($data['orderId'])) {
            $order = Order::query()->online()->with('store')->find($data['orderId']);

            if ($order === null) {
                throw ValidationException::withMessages([
                    'orderId' => 'We couldn\'t find that order.',
                ]);
            }

            $store = $order->store;
        } else {
            $store = Store::query()
                ->where('code', $data['storeCode'])
                ->whereHas('organization', fn ($query) => $query->where('slug', $data['orgSlug']))
                ->first();
        }

        abort_if($store === null || $store->status !== 'active', 404, 'That shop isn\'t taking messages.');

        // createOrFirst rather than firstOrCreate: two first messages sent at
        // once both try the insert, one loses on the unique index, and it then
        // reads the winner's row instead of failing.
        $conversation = Conversation::query()->createOrFirst(
            ['store_id' => $store->id, 'customer_account_id' => $this->account($request)->getKey()],
            ['organization_id' => $store->organization_id],
        );

        $conversation->post(Conversation::SENDER_CUSTOMER, $data['body'], order: $order);

        return response()->json($this->thread($conversation), 201);
    }

    /** Read a thread, which is also what marks it read. */
    public function show(Request $request, string $conversation): JsonResponse
    {
        $row = $this->own($request, $conversation);
        $row->markReadBy(Conversation::SENDER_CUSTOMER);

        return response()->json($this->thread($row));
    }

    public function reply(Request $request, string $conversation): JsonResponse
    {
        $row = $this->own($request, $conversation);

        $data = $request->validate([
            'body' => ['required', 'string', 'max:'.Conversation::MAX_BODY],
            'orderId' => ['nullable', 'uuid'],
        ]);

        $order = null;

        if (! empty($data['orderId'])) {
            // Only an order from *this* shop. Naming another shop's order here
            // would put its ticket number in front of a counter that never saw
            // it.
            $order = Order::query()->online()->where('store_id', $row->store_id)->find($data['orderId']);

            if ($order === null) {
                throw ValidationException::withMessages([
                    'orderId' => 'That order isn\'t from this shop.',
                ]);
            }
        }

        $row->post(Conversation::SENDER_CUSTOMER, $data['body'], order: $order);

        return response()->json($this->thread($row), 201);
    }

    private function account(Request $request): CustomerAccount
    {
        return $request->user();
    }

    private function own(Request $request, string $id): Conversation
    {
        return Conversation::query()
            ->where('customer_account_id', $this->account($request)->getKey())
            ->findOrFail($id);
    }

    /** @return array<string, mixed> */
    private function thread(Conversation $conversation): array
    {
        $messages = $conversation->messages()
            ->with('order')
            ->orderByDesc('id')
            ->limit(self::THREAD_LIMIT)
            ->get()
            ->reverse();

        return [
            'conversation' => $conversation->toCustomerSummary(),
            'messages' => $messages->map->toCustomerArray()->values(),
        ];
    }
}
