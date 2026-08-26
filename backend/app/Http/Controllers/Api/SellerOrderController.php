<?php

namespace App\Http\Controllers\Api;

use App\Events\OrderDeliveryUpdated;
use App\Events\OrderStatusChanged;
use App\Http\Controllers\Controller;
use App\Models\Device;
use App\Models\Order;
use App\Models\Payment;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\DB;
use Illuminate\Validation\Rule;

/**
 * The seller's side of a storefront order: seeing it, moving it along, naming
 * the rider who takes it, and marking it paid when the money arrives.
 *
 * This closes a gap the storefront's move to Laravel opened. Online orders are
 * written here by OnlineOrderController, but the POS only ever pulled them from
 * Firestore (packages/data loadOnlineOrders) and /sync/pull returns catalog
 * only — so an order placed on the storefront reached nobody. Everything here
 * is scoped to the calling device's store, the same rule the sync endpoints
 * follow: a device sees its own store's orders and nothing else.
 *
 * In-person sales are not served from here. They ride the offline-first outbox
 * in SyncController, because a register has to keep selling with the network
 * down; a storefront order, by definition, arrived over the network already.
 */
class SellerOrderController extends Controller
{
    /** One busy day's worth — the dashboard shows the live end of this. */
    private const LIMIT = 100;

    public function index(Request $request): JsonResponse
    {
        $device = $this->deviceFromRequest($request);

        $orders = Order::query()
            ->online()
            ->with(['items', 'payments'])
            ->where('organization_id', $device->organization_id)
            ->where('store_id', $device->store_id)
            ->orderByDesc('created_at')
            ->limit(self::LIMIT)
            ->get();

        return response()->json([
            'orders' => $orders->map(fn (Order $order) => $this->asSummary($order))->values(),
        ]);
    }

    /**
     * Name the rider carrying this order.
     *
     * There is no rider app and no rider accounts, so a rider here is a name
     * and a number the seller types in — whoever is actually at the counter.
     * Recording them is what lets the customer's tracking page say who is
     * coming, and gives the seller a number to ring when an order goes quiet.
     */
    public function assignRider(Request $request, Order $order): JsonResponse
    {
        $device = $this->deviceFromRequest($request);
        $this->authorizeOrder($order, $device);

        abort_unless($order->fulfillment_method === 'delivery', 422, 'That order is for pickup, not delivery.');

        $validated = $request->validate([
            'riderName' => ['required', 'string', 'max:120'],
            'riderPhone' => ['nullable', 'string', 'max:40'],
        ]);

        $previousStage = $order->delivery_stage;

        $order->forceFill([
            'rider_name' => trim($validated['riderName']),
            'rider_phone' => isset($validated['riderPhone']) ? trim($validated['riderPhone']) : null,
            // Naming a rider is itself the assignment, but an order already out
            // on the road keeps the stage it has: re-assigning a rider
            // mid-delivery (the first one broke down, say) must not walk the
            // customer's tracking backwards.
            'delivery_stage' => in_array($previousStage, ['picked_up', 'delivered'], true) ? $previousStage : 'assigned',
        ])->save();

        DB::afterCommit(fn () => OrderDeliveryUpdated::dispatch($order, $previousStage));

        return response()->json(['order' => $this->asSummary($order->fresh('items'))]);
    }

    /** Moves the order along the road: assigned → picked_up → delivered. */
    public function updateDeliveryStage(Request $request, Order $order): JsonResponse
    {
        $device = $this->deviceFromRequest($request);
        $this->authorizeOrder($order, $device);

        abort_unless($order->fulfillment_method === 'delivery', 422, 'That order is for pickup, not delivery.');

        $validated = $request->validate([
            'stage' => ['required', Rule::in(['pending', 'assigned', 'picked_up', 'delivered'])],
        ]);

        $previousStage = $order->delivery_stage;

        $order->forceFill(['delivery_stage' => $validated['stage']])->save();

        DB::afterCommit(fn () => OrderDeliveryUpdated::dispatch($order, $previousStage));

        return response()->json(['order' => $this->asSummary($order->fresh('items'))]);
    }

    /** Preparing → ready → served, the kitchen's side of the same order. */
    public function updateStatus(Request $request, Order $order): JsonResponse
    {
        $device = $this->deviceFromRequest($request);
        $this->authorizeOrder($order, $device);

        $validated = $request->validate([
            'status' => ['required', Rule::in(['preparing', 'ready', 'served'])],
        ]);

        $previousStatus = $order->order_status;

        $order->forceFill([
            'order_status' => $validated['status'],
            'completed_at' => $validated['status'] === 'served' ? ($order->completed_at ?? now()) : $order->completed_at,
        ])->save();

        DB::afterCommit(fn () => OrderStatusChanged::dispatch($order, $previousStatus));

        return response()->json(['order' => $this->asSummary($order->fresh('items'))]);
    }

    /**
     * Marks an unpaid online order paid.
     *
     * There is no payment gateway — storefront orders are cash or GCash on
     * arrival — so this records a staff member's confirmation that the money
     * turned up, not a charge. Hence the audit columns.
     */
    public function settlePayment(Request $request, Order $order): JsonResponse
    {
        $device = $this->deviceFromRequest($request);
        $this->authorizeOrder($order, $device);

        $validated = $request->validate([
            'paymentMethod' => ['required', Rule::in(['cash', 'card', 'ewallet'])],
            'tenderedCents' => ['nullable', 'integer', 'min:0'],
            'changeCents' => ['nullable', 'integer', 'min:0'],
            'userId' => ['nullable', 'uuid'],
        ]);

        // The tender itself belongs on the payments table, not the order —
        // that is where SyncController puts a register sale's cash, and an
        // online order settled at the door is the same event arriving late.
        DB::transaction(function () use ($order, $validated) {
            $order->forceFill([
                'payment_status' => 'paid',
                'payment_method' => $validated['paymentMethod'],
                'payment_confirmed_at' => now(),
                'payment_confirmed_by_user_id' => $validated['userId'] ?? null,
            ])->save();

            Payment::query()->create([
                'id' => (string) str()->uuid(),
                'organization_id' => $order->organization_id,
                'store_id' => $order->store_id,
                'order_id' => $order->id,
                'payment_method' => $validated['paymentMethod'],
                'amount_cents' => $order->total_cents,
                'tendered_cents' => $validated['tenderedCents'] ?? $order->total_cents,
                'change_cents' => $validated['changeCents'] ?? 0,
            ]);
        });

        return response()->json(['order' => $this->asSummary($order->fresh(['items', 'payments']))]);
    }

    /**
     * The shape packages/data expects an OrderSummary in — deliberately the
     * same field names mapFsOrder produces from Firestore, so the repository
     * can swap transports without the app noticing.
     *
     * @return array<string, mixed>
     */
    private function asSummary(Order $order): array
    {
        $guest = is_array($order->guest_contact) ? $order->guest_contact : null;
        $payment = $order->relationLoaded('payments') ? $order->payments->last() : $order->payments()->latest()->first();

        return [
            'id' => $order->id,
            'ticketNumber' => $order->ticket_number,
            'businessMode' => $order->business_mode ?? 'coffee-shop',
            'customerId' => null,
            'customerName' => $guest['name'] ?? 'Walk-in',
            'orderType' => $order->order_type ?? 'takeaway',
            'tableNumber' => $order->table_number,
            'status' => $order->order_status ?? 'preparing',
            'paymentMethod' => $order->payment_method ?? 'cash',
            'subtotalCents' => (int) $order->subtotal_cents,
            'taxCents' => (int) $order->tax_cents,
            'totalCents' => (int) $order->total_cents,
            // Read back off the payment, since that is where the tender lives.
            'tenderedCents' => (int) ($payment?->tendered_cents ?? 0),
            'changeCents' => (int) ($payment?->change_cents ?? 0),
            'createdAt' => $order->created_at?->toIso8601String(),
            'items' => $order->items->map(fn ($item) => [
                'productId' => $item->product_id ?? '',
                'name' => $item->product_name,
                'quantity' => (float) $item->quantity,
                'unitPriceCents' => (int) $item->unit_price_cents,
                'lineTotalCents' => (int) $item->line_total_cents,
            ])->values(),
            'channel' => $order->channel ?? 'online',
            'paymentStatus' => $order->payment_status ?? 'unpaid',
            'guestContact' => $guest,
            'fulfillmentMethod' => $order->fulfillment_method,
            'deliveryAddress' => $order->delivery_address,
            'deliveryStage' => $order->delivery_stage,
            'riderName' => $order->rider_name,
            'riderPhone' => $order->rider_phone,
            'deliveryFeeCents' => (int) ($order->delivery_fee_cents ?? 0),
            'voidedAt' => $order->deleted_at?->toIso8601String(),
            'voidedByUserId' => null,
            'voidReason' => null,
            'paymentConfirmedAt' => $order->payment_confirmed_at?->toIso8601String(),
            'paymentConfirmedByUserId' => $order->payment_confirmed_by_user_id,
        ];
    }

    /** A device may only touch orders belonging to the store it is paired to. */
    private function authorizeOrder(Order $order, Device $device): void
    {
        abort_unless($order->isOnline(), 404);
        abort_unless(
            $order->organization_id === $device->organization_id && $order->store_id === $device->store_id,
            403,
            'That order belongs to another store.',
        );
    }

    private function deviceFromRequest(Request $request): Device
    {
        $device = $request->user();
        abort_unless($device instanceof Device, 403, 'Authenticated device required.');

        $device->forceFill(['last_seen_at' => now()])->save();

        return $device;
    }
}
