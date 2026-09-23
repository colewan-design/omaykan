<?php

namespace App\Http\Controllers\Api;

use App\Http\Controllers\Concerns\ActsForAStore;
use App\Http\Controllers\Controller;
use App\Models\Order;
use App\Services\RegisterSales;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\DB;

/**
 * A sale rung up at the till, sent here before the cashier finishes it, and
 * a void of one.
 *
 * The till is online-only. A sale is recorded here or not at all: when this
 * refuses one (see RegisterSales for what it checks), the payment sheet shows
 * the reason and the sale is not completed. Retrying the same sale is safe —
 * the till keeps the order's id across attempts, and a sale the server
 * already has is answered with the one it has.
 */
class RegisterOrderController extends Controller
{
    use ActsForAStore;

    public function __construct(private readonly RegisterSales $sales) {}

    public function store(Request $request): JsonResponse
    {
        $context = $this->writableStoreContext($request);

        $request->validate([
            'order' => ['required', 'array'],
            'order.id' => ['required', 'uuid'],
            'order.ticketNumber' => ['required', 'string', 'max:40'],
            'order.subtotalCents' => ['required', 'integer', 'min:0'],
            'order.discountCents' => ['nullable', 'integer', 'min:0'],
            'order.taxCents' => ['required', 'integer', 'min:0'],
            'order.totalCents' => ['required', 'integer', 'min:0'],
            'items' => ['required', 'array', 'min:1'],
            'items.*.productId' => ['nullable', 'uuid'],
            'items.*.quantity' => ['required', 'numeric', 'gt:0'],
            'items.*.unitPriceCents' => ['required', 'integer', 'min:0'],
            'items.*.lineTotalCents' => ['required', 'integer', 'min:0'],
            'discounts' => ['nullable', 'array'],
            'payments' => ['nullable', 'array'],
        ]);

        // The whole request, not just the validated keys: the order and its
        // lines carry fields (customer, table, tax rate) validation does not
        // need to name.
        $payload = $request->only(['order', 'items', 'discounts', 'payments']);

        $alreadyHad = Order::withTrashed()->whereKey($payload['order']['id'])->exists();

        $order = DB::transaction(fn () => $this->sales->record($context, $payload));

        return response()->json([
            'orderId' => $order->id,
            'ticketNumber' => $order->ticket_number,
        ], $alreadyHad ? 200 : 201);
    }

    /**
     * Void a sale. The owner's alone, as it is on the till's Orders page.
     */
    public function void(Request $request, string $order): JsonResponse
    {
        $context = $this->writableStoreContext($request);

        abort_unless($context->role === 'admin', 403, 'Only the owner can void a sale.');

        $input = $request->validate([
            'voidedByUserId' => ['nullable', 'string'],
            'reason' => ['nullable', 'string', 'max:240'],
            'voidedAt' => ['nullable', 'date'],
        ]);

        $record = Order::withTrashed()
            ->where('store_id', $context->storeId())
            ->whereKey($order)
            ->with('items')
            ->first();

        abort_if($record === null, 404, 'That sale is not on the server.');

        $voided = DB::transaction(fn () => $this->sales->void($context, $record, $input));

        return response()->json([
            'orderId' => $voided->id,
            'voidedAt' => $voided->deleted_at?->toIso8601String(),
        ]);
    }
}
