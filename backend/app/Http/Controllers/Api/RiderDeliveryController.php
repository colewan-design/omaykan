<?php

namespace App\Http\Controllers\Api;

use App\Events\OrderDeliveryUpdated;
use App\Http\Controllers\Controller;
use App\Models\Order;
use App\Models\Rider;
use App\Models\Store;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\DB;
use Illuminate\Validation\Rule;

/**
 * The rider's side of a delivery: what is waiting, taking one, and moving it
 * along the road.
 *
 * The counterpart to SellerOrderController, and deliberately not part of it.
 * That controller is scoped to one store, because a till may only ever see its
 * own orders; this one is the opposite — a rider works across every shop on
 * the platform, so the scope here is the rider, not the tenant. Sharing a
 * controller would have meant one of the two scoping rules being a special
 * case of the other, which is exactly how a cross-tenant leak gets written.
 *
 * Both write the same three columns and dispatch the same event, so a delivery
 * claimed in the portal shows up on the shop's dashboard and the customer's
 * tracking page with no changes to either. A shop that hands an order to a
 * rider it knows personally still types a name; that order keeps `rider_id`
 * null and never appears on the board.
 */
class RiderDeliveryController extends Controller
{
    /** A board longer than this is not a board, it is a database dump. */
    private const BOARD_LIMIT = 40;

    /** How much finished work a rider sees behind them. */
    private const HISTORY_LIMIT = 30;

    /**
     * Unclaimed delivery orders, newest first.
     *
     * Platform-wide on purpose: riders are not attached to a shop. What limits
     * this is the stage, not the tenant — an order is on the board from the
     * moment it is placed until someone takes it.
     */
    public function board(Request $request): JsonResponse
    {
        $this->touch($request);

        $orders = Order::query()
            ->online()
            ->with('items')
            ->where('fulfillment_method', 'delivery')
            ->where('delivery_stage', 'pending')
            ->whereNull('rider_id')
            // A served order has already left by some other route — a rider
            // the shop rang directly, or a cancelled delivery. Not offerable.
            ->whereNot('order_status', 'served')
            ->orderByDesc('created_at')
            ->limit(self::BOARD_LIMIT)
            ->get();

        return response()->json([
            'orders' => $this->withStores($orders)
                ->map(fn (Order $order) => $this->asOffer($order))
                ->values(),
        ]);
    }

    /**
     * Claim an order.
     *
     * The claim is a conditional UPDATE rather than a read-then-write: two
     * riders tapping the same job a second apart is the normal case, not the
     * exotic one, and a check followed by a save would hand it to both. The
     * one whose UPDATE matches no rows is told, plainly, that it went.
     */
    public function accept(Request $request, Order $order): JsonResponse
    {
        $rider = $this->rider($request);

        abort_unless($order->isOnline(), 404);
        abort_unless($order->fulfillment_method === 'delivery', 422, 'That order is for pickup, not delivery.');

        $previousStage = $order->delivery_stage;

        $claimed = Order::query()
            ->whereKey($order->id)
            ->whereNull('rider_id')
            ->where('delivery_stage', 'pending')
            ->update([
                'rider_id' => $rider->id,
                'rider_accepted_at' => now(),
                // The free-text columns are filled from the account, so the
                // shop's dashboard and the customer's tracking page keep
                // reading exactly the fields they already read.
                'rider_name' => $rider->name,
                'rider_phone' => $rider->phone,
                'delivery_stage' => 'assigned',
                'updated_at' => now(),
            ]);

        if ($claimed === 0) {
            return response()->json([
                'message' => 'Another rider took that one. Pull down to refresh the board.',
            ], 409);
        }

        $order->refresh();

        DB::afterCommit(fn () => OrderDeliveryUpdated::dispatch($order, $previousStage));

        return response()->json(['order' => $this->asAssignment($this->withStore($order))]);
    }

    /**
     * The rider's own work: everything still on the road, then what they have
     * finished. Two lists in one response because the portal shows both at
     * once and a second round trip would only make them disagree.
     */
    public function mine(Request $request): JsonResponse
    {
        $rider = $this->rider($request);

        $active = Order::query()
            ->with('items')
            ->where('rider_id', $rider->id)
            ->whereIn('delivery_stage', ['assigned', 'picked_up'])
            ->orderBy('rider_accepted_at')
            ->get();

        $completed = Order::query()
            ->with('items')
            ->where('rider_id', $rider->id)
            ->where('delivery_stage', 'delivered')
            ->orderByDesc('updated_at')
            ->limit(self::HISTORY_LIMIT)
            ->get();

        return response()->json([
            'active' => $this->withStores($active)->map(fn (Order $o) => $this->asAssignment($o))->values(),
            'completed' => $this->withStores($completed)->map(fn (Order $o) => $this->asAssignment($o))->values(),
        ]);
    }

    /**
     * Move an order one step along: assigned → picked_up → delivered.
     *
     * The target stage is named by the caller rather than inferred, so a
     * double-tap on a slow connection is idempotent-ish instead of skipping a
     * step, but the legal transitions are checked here — a rider cannot mark
     * something delivered that they never picked up.
     */
    public function advance(Request $request, Order $order): JsonResponse
    {
        $rider = $this->rider($request);

        abort_unless(
            $order->rider_id === $rider->id,
            403,
            'That delivery belongs to another rider.',
        );

        $validated = $request->validate([
            'stage' => ['required', Rule::in(['picked_up', 'delivered'])],
        ]);

        $previousStage = $order->delivery_stage;

        $allowed = match ($validated['stage']) {
            'picked_up' => ['assigned'],
            'delivered' => ['picked_up'],
        };

        abort_unless(
            in_array($previousStage, $allowed, true),
            422,
            $validated['stage'] === 'picked_up'
                ? 'That order is not waiting for pickup.'
                : 'Mark the order picked up before delivering it.',
        );

        $order->forceFill(['delivery_stage' => $validated['stage']])->save();

        DB::afterCommit(fn () => OrderDeliveryUpdated::dispatch($order, $previousStage));

        return response()->json(['order' => $this->asAssignment($this->withStore($order))]);
    }

    /**
     * Give an order back to the board.
     *
     * A rider whose bike dies at the shop is better off releasing the job than
     * sitting on it — the alternative is the shop ringing round to find out
     * why nothing moved. Only before pickup: once the food is in the bag, the
     * order is physically with the rider and handing it back is a phone call,
     * not a button.
     */
    public function release(Request $request, Order $order): JsonResponse
    {
        $rider = $this->rider($request);

        abort_unless($order->rider_id === $rider->id, 403, 'That delivery belongs to another rider.');
        abort_unless(
            $order->delivery_stage === 'assigned',
            422,
            'You already picked this order up — call the shop to hand it back.',
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

        return response()->json(['released' => true]);
    }

    /**
     * What an unclaimed job looks like on the board.
     *
     * No customer name and no phone number. A rider deciding whether to take a
     * job needs to know where it is going, what it weighs and what it pays —
     * not who lives there. Those arrive with the claim, in asAssignment().
     *
     * @return array<string, mixed>
     */
    private function asOffer(Order $order): array
    {
        return [
            'id' => $order->id,
            'ticketNumber' => $order->ticket_number,
            'placedAt' => $order->created_at?->toIso8601String(),
            'orderStatus' => $order->order_status,
            'pickup' => $this->pickupOf($order),
            'dropoffArea' => $this->areaOf($order->delivery_address),
            'distanceKm' => $order->delivery_distance_km !== null ? (float) $order->delivery_distance_km : null,
            'deliveryFeeCents' => (int) ($order->delivery_fee_cents ?? 0),
            'itemCount' => (int) $order->items->sum('quantity'),
            // Whether there is money to collect at the door decides whether a
            // rider wants the job at all, so it belongs on the board.
            'paymentStatus' => $order->payment_status ?? 'unpaid',
            'collectCents' => $order->payment_status === 'paid' ? 0 : (int) $order->total_cents,
        ];
    }

    /**
     * What a claimed job looks like: the offer, plus everything the rider now
     * needs to actually complete it.
     *
     * @return array<string, mixed>
     */
    private function asAssignment(Order $order): array
    {
        $guest = is_array($order->guest_contact) ? $order->guest_contact : null;

        return $this->asOffer($order) + [
            'deliveryStage' => $order->delivery_stage,
            'acceptedAt' => $order->rider_accepted_at?->toIso8601String(),
            'deliveryAddress' => $order->delivery_address,
            'deliveryLat' => $order->delivery_lat !== null ? (float) $order->delivery_lat : null,
            'deliveryLng' => $order->delivery_lng !== null ? (float) $order->delivery_lng : null,
            'customerName' => $guest['name'] ?? null,
            'customerPhone' => $guest['phone'] ?? null,
            'items' => $order->items->map(fn ($item) => [
                'name' => $item->product_name,
                'quantity' => (float) $item->quantity,
            ])->values(),
        ];
    }

    /**
     * The shop, as a rider needs it: where to go and who to ask for.
     *
     * @return array<string, mixed>
     */
    private function pickupOf(Order $order): array
    {
        $store = $order->relationLoaded('store') ? $order->getRelation('store') : null;

        return [
            'storeId' => $order->store_id,
            'storeName' => $store?->name ?? 'Shop',
            'address' => $store?->address,
            'lat' => $store?->lat !== null ? (float) $store->lat : null,
            'lng' => $store?->lng !== null ? (float) $store->lng : null,
        ];
    }

    /**
     * The last one or two parts of an address — "Sto. Tomas, Baguio City" out
     * of a full street line. Enough to judge the trip, not enough to knock on
     * the door of an order you have not taken.
     */
    private function areaOf(?string $address): ?string
    {
        if ($address === null || trim($address) === '') {
            return null;
        }

        $parts = array_values(array_filter(array_map('trim', explode(',', $address)), fn ($p) => $p !== ''));

        return match (count($parts)) {
            0 => null,
            1 => $parts[0],
            default => implode(', ', array_slice($parts, -2)),
        };
    }

    /**
     * Orders come from every tenant here, so the stores cannot be eager-loaded
     * through a relation the model does not have a scope for — they are looked
     * up in one query and attached by hand.
     *
     * @param  \Illuminate\Support\Collection<int, Order>  $orders
     * @return \Illuminate\Support\Collection<int, Order>
     */
    private function withStores($orders)
    {
        $stores = Store::query()
            ->whereIn('id', $orders->pluck('store_id')->filter()->unique()->all())
            ->get()
            ->keyBy('id');

        return $orders->each(fn (Order $order) => $order->setRelation('store', $stores->get($order->store_id)));
    }

    private function withStore(Order $order): Order
    {
        $order->setRelation('store', Store::query()->find($order->store_id));

        return $order;
    }

    private function rider(Request $request): Rider
    {
        $rider = $request->user();
        abort_unless($rider instanceof Rider, 403, 'Rider account required.');

        $rider->forceFill(['last_seen_at' => now()])->save();

        return $rider;
    }

    private function touch(Request $request): void
    {
        $this->rider($request);
    }
}
