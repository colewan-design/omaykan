<?php

namespace App\Http\Controllers\Api;

use App\Events\OrderDeliveryUpdated;
use App\Events\OrderStatusChanged;
use App\Http\Controllers\Concerns\ActsForAStore;
use App\Http\Controllers\Controller;
use App\Models\Order;
use App\Models\Payment;
use App\Models\StoreSavedRider;
use App\Services\StoreContext;
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
 * is scoped to the store the caller signed in to, the same rule the sync
 * endpoints follow: a session sees its own store's orders and nothing else.
 *
 * In-person sales are not served from here. They ride the offline-first outbox
 * in SyncController, because a register has to keep selling with the network
 * down; a storefront order, by definition, arrived over the network already.
 */
class SellerOrderController extends Controller
{
    use ActsForAStore;

    /** One busy day's worth — the dashboard shows the live end of this. */
    private const LIMIT = 100;

    public function index(Request $request): JsonResponse
    {
        $context = $this->storeContext($request);

        $orders = Order::query()
            ->online()
            // `rider` for the live position, `store` for the map's pickup pin.
            // Both are read for every row now, so neither may be a lazy load
            // across a hundred orders.
            ->with(['items', 'payments', 'rider', 'store', 'discounts'])
            ->where('organization_id', $context->organizationId())
            ->where('store_id', $context->storeId())
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
     * A shop has three ways to answer "who is taking this", and all three are
     * legitimate — the platform's job is not to force one:
     *
     *   - **Leave it on the board.** Do nothing here. Any approved rider on the
     *     platform can claim it in their app (RiderDeliveryController::accept).
     *     This is the aggregator model and it is the right default for a shop
     *     with no rider of their own.
     *   - **Pick a rider they keep.** `savedRiderId` names a row from
     *     SellerRiderController — a person this shop has deliberately put on
     *     file. If that row is linked to a platform account the order lands in
     *     that rider's app and their position feeds the live map; if it is not,
     *     the assignment is still just a name and a number, and the shop rings
     *     them, exactly as it always has.
     *   - **Type a name.** `riderName` and `riderPhone`, unchanged, for whoever
     *     happens to be at the counter. `saveRider` remembers them for next
     *     time so this is the last time it has to be typed.
     *
     * Assigning takes the order *off* the board — a shop that has chosen its
     * own rider must not have the job snatched from under them by a stranger
     * mid-decision, and `delivery_stage` moving off `pending` is what does it.
     */
    public function assignRider(Request $request, Order $order): JsonResponse
    {
        $context = $this->storeContext($request);
        $this->authorizeOrder($order, $context);

        abort_unless($order->fulfillment_method === 'delivery', 422, 'That order is for pickup, not delivery.');

        $validated = $request->validate([
            'savedRiderId' => ['nullable', 'uuid'],
            // Required only when no saved rider was named — one of the two
            // has to say who is carrying this.
            'riderName' => ['required_without:savedRiderId', 'string', 'max:120'],
            'riderPhone' => ['nullable', 'string', 'max:40'],
            // Remember a typed-in rider for next time. Ignored when the rider
            // came from the picker; they are already saved.
            'saveRider' => ['sometimes', 'boolean'],
            'saveNote' => ['nullable', 'string', 'max:160'],
        ]);

        $saved = null;

        if (! empty($validated['savedRiderId'])) {
            $saved = StoreSavedRider::query()
                ->with('rider')
                ->where('store_id', $context->storeId())
                ->find($validated['savedRiderId']);

            abort_unless($saved !== null, 404, 'That rider is not on this shop\'s list.');

            $rider = $saved->rider;

            // A suspended account still has a phone number the shop can ring,
            // but it must not be handed a live delivery: the rider app would
            // refuse every call they made on it (EnsureRiderIsApproved), so the
            // order would sit at `assigned` going nowhere.
            abort_if(
                $rider !== null && ! $rider->isApproved(),
                422,
                $rider->name.' cannot take deliveries right now. Their rider account is '.$rider->status.'.',
            );
        }

        $name = $saved?->name ?? trim($validated['riderName']);
        $phone = $saved
            ? $saved->phone
            : (isset($validated['riderPhone']) ? (trim($validated['riderPhone']) ?: null) : null);

        $previousStage = $order->delivery_stage;

        $order->forceFill([
            'rider_name' => $name,
            'rider_phone' => $phone,
            // Only a saved rider linked to a real account sets `rider_id`. That
            // column is what makes the order appear in somebody's app and what
            // gates the live position, so a typed-in name must never fill it —
            // and re-assigning from a platform rider to a typed-in one has to
            // clear it, or the previous rider keeps the job on their phone.
            'rider_id' => $saved?->rider_id,
            'rider_accepted_at' => $saved?->rider_id !== null ? now() : null,
            // Naming a rider is itself the assignment, but an order already out
            // on the road keeps the stage it has: re-assigning a rider
            // mid-delivery (the first one broke down, say) must not walk the
            // customer's tracking backwards.
            'delivery_stage' => in_array($previousStage, ['picked_up', 'delivered'], true) ? $previousStage : 'assigned',
        ])->save();

        // Ranking in the picker is by use, and this is the use. Counted only on
        // a real assignment, so a shop that opens the sheet and backs out does
        // not quietly promote somebody.
        $saved?->recordUse();

        if ($saved === null && ($validated['saveRider'] ?? false)) {
            $this->rememberRider($context, $name, $phone, $validated['saveNote'] ?? null);
        }

        DB::afterCommit(fn () => OrderDeliveryUpdated::dispatch($order, $previousStage));

        return response()->json(['order' => $this->asSummary($order->fresh(['items', 'rider']))]);
    }

    /**
     * Take the rider off, and put the order back on the board.
     *
     * The undo for the above, and the seller's counterpart to a rider's own
     * release. A shop that assigned their nephew and then found out he is
     * asleep needs a way back to the open board that is not "type a fake name".
     *
     * Refused once the food is physically with the rider, for the same reason
     * RiderDeliveryController::release refuses: at that point handing the order
     * back is a phone call between two people, not a button on a dashboard.
     */
    public function unassignRider(Request $request, Order $order): JsonResponse
    {
        $context = $this->storeContext($request);
        $this->authorizeOrder($order, $context);

        abort_unless($order->fulfillment_method === 'delivery', 422, 'That order is for pickup, not delivery.');
        abort_unless(
            in_array($order->delivery_stage, ['pending', 'assigned'], true),
            422,
            'That order has already been picked up — call the rider to hand it back.',
        );

        $previousStage = $order->delivery_stage;

        $order->forceFill([
            'rider_id' => null,
            'rider_accepted_at' => null,
            'rider_name' => null,
            'rider_phone' => null,
            'delivery_stage' => 'pending',
        ])->save();

        DB::afterCommit(fn () => OrderDeliveryUpdated::dispatch($order, $previousStage));

        return response()->json(['order' => $this->asSummary($order->fresh(['items', 'rider']))]);
    }

    /**
     * Put a typed-in rider on the shop's list.
     *
     * Best-effort on purpose: the assignment is the thing the seller asked for
     * and it has already happened. A duplicate name or a race against the same
     * rider being saved from the picker must not turn a successful assignment
     * into an error on the dashboard.
     */
    private function rememberRider(StoreContext $context, string $name, ?string $phone, ?string $note): void
    {
        try {
            // Keyed the same way SellerRiderController::keyFor does it: on the
            // number when there is one, on the name when there is not. Saving
            // the same rider twice must edit their row rather than grow a
            // second copy of them in the picker.
            StoreSavedRider::query()->updateOrCreate(
                $phone !== null
                    ? ['store_id' => $context->storeId(), 'rider_id' => null, 'phone' => $phone]
                    : ['store_id' => $context->storeId(), 'rider_id' => null, 'phone' => null, 'name' => $name],
                ['name' => $name, 'note' => $note !== null ? trim($note) : null],
            );
        } catch (\Throwable) {
            // Saving is a convenience. Assigning is the transaction.
        }
    }

    /** Moves the order along the road: assigned → picked_up → delivered. */
    public function updateDeliveryStage(Request $request, Order $order): JsonResponse
    {
        $context = $this->storeContext($request);
        $this->authorizeOrder($order, $context);

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
        $context = $this->storeContext($request);
        $this->authorizeOrder($order, $context);

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
        $context = $this->storeContext($request);
        $this->authorizeOrder($order, $context);

        // Settling twice used to return 200 twice and write a second Payment row
        // for the full total. That is not a stray row: cashSalesForShift() sums
        // this table into the cash a drawer is expected to hold at shift close,
        // so a duplicated cash payment makes an honest till reconcile short by
        // that amount — a false discrepancy aimed at whoever was on the counter.
        // A double tap on a slow connection was enough to cause it.
        //
        // Refusing rather than quietly no-opping is deliberate: a second
        // settlement means something confusing happened at the counter, and the
        // person tapping should see that rather than have it swallowed.
        abort_if(
            $order->payment_status === 'paid',
            422,
            'That order has already been settled.',
        );

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
            'discountCents' => (int) $order->discount_cents,
            'discountLabel' => $order->discountLabel(),
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
            'deliveryLandmark' => $order->delivery_landmark,
            'deliveryStage' => $order->delivery_stage,
            'riderName' => $order->rider_name,
            'riderPhone' => $order->rider_phone,
            // Set only when the rider is a platform account. The dashboard uses
            // it to tell "our nephew on a tricycle" from "someone who took this
            // off the board", which decides whether there is a map to draw.
            'riderId' => $order->rider_id,
            // Ungated, unlike the customer's: the shop is the other party to
            // this delivery for its whole life, and a merchant chasing a late
            // order needs the last known position even after it is marked
            // delivered. The rider only reports while carrying something, so
            // "after delivery" is a stale fix, not a live trace.
            'riderPosition' => $order->rider?->positionArray(),
            // Ungated for the same reason the position above is: the shop is a
            // party to this delivery for its whole life, and the counter staff
            // handing over a bag need to know which of the three people waiting
            // is the one with this order. Null for a rider typed in at the till.
            'riderProfile' => $order->rider?->toPublicArray(),
            'route' => $order->routeEndpointsArray(),
            'deliveryFeeCents' => (int) ($order->delivery_fee_cents ?? 0),
            'voidedAt' => $order->deleted_at?->toIso8601String(),
            'voidedByUserId' => $order->voided_by_user_id,
            'voidReason' => $order->void_reason,
            'paymentConfirmedAt' => $order->payment_confirmed_at?->toIso8601String(),
            'paymentConfirmedByUserId' => $order->payment_confirmed_by_user_id,
        ];
    }

    /** A session may only touch orders belonging to the store it signed in to. */
    private function authorizeOrder(Order $order, StoreContext $context): void
    {
        abort_unless($order->isOnline(), 404);
        abort_unless(
            $order->organization_id === $context->organizationId() && $order->store_id === $context->storeId(),
            403,
            'That order belongs to another store.',
        );
    }

}
