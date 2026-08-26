<?php

namespace App\Http\Controllers\Api;

use App\Events\OrderPlaced;
use App\Http\Controllers\Controller;
use App\Mail\OnlineOrderConfirmationMail;
use App\Models\InventoryAdjustment;
use App\Models\InventoryLevel;
use App\Models\Order;
use App\Models\OrderItem;
use App\Models\Organization;
use App\Models\Product;
use App\Models\Store;
use App\Services\DeliveryQuoter;
use App\Services\OutsideDeliveryAreaException;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\Mail;
use Illuminate\Validation\Rule;
use Illuminate\Validation\ValidationException;

/**
 * Customer orders placed on the storefront.
 *
 * Ported from api/create-online-order.ts, which this replaces. Deliberately
 * unauthenticated: a storefront customer has no account. The organization slug
 * plus the store code are the addressing scheme, exactly as before.
 *
 * Nothing the client sends about money is trusted — prices, tax and the
 * delivery fee are all recomputed here from the merchant's own records.
 */
class OnlineOrderController extends Controller
{
    /** Only these modes sell online; a salon has nothing to put in a cart. */
    private const ONLINE_MODES = ['coffee-shop', 'grocery', 'restaurant'];

    public function __construct(private readonly DeliveryQuoter $quoter)
    {
    }

    public function store(Request $request): JsonResponse
    {
        // Optional, and read without any auth middleware on the route: a
        // bearer token from the customer portal names who is ordering, and no
        // token at all is still a perfectly good guest order. A *staff* token
        // resolves to null here, because the `customer` guard has its own
        // provider — see config/auth.php.
        $customer = $request->user('customer');

        // A signed-in customer shouldn't have to send their own name back to
        // us, so anything the form left out is filled from the account before
        // the contact rules below are applied.
        if ($customer !== null) {
            $guest = (array) $request->input('guest', []);

            $request->merge([
                'guest' => array_filter([
                    'name' => $guest['name'] ?? $customer->name,
                    'phone' => $guest['phone'] ?? $customer->phone,
                    'email' => $guest['email'] ?? $customer->email,
                ], fn ($value) => $value !== null && $value !== ''),
            ]);
        }

        $data = $request->validate([
            'orgSlug' => ['required', 'string'],
            'storeCode' => ['required', 'string'],
            'businessMode' => ['required', Rule::in(self::ONLINE_MODES)],

            'items' => ['required', 'array', 'min:1'],
            'items.*.productId' => ['required', 'string'],
            'items.*.quantity' => ['required', 'numeric', 'gt:0'],

            'guest.name' => ['required', 'string', 'max:120'],
            'guest.phone' => ['nullable', 'string', 'max:40', 'required_without:guest.email'],
            'guest.email' => ['nullable', 'email', 'max:190', 'required_without:guest.phone'],

            'fulfillment.method' => ['required', Rule::in(['pickup', 'delivery'])],
            'fulfillment.address' => ['nullable', 'string', 'max:500', 'required_if:fulfillment.method,delivery'],
            // A malformed pair must not silently fall through to the flat-fee
            // path — that would let a caller dodge the distance surcharge by
            // sending only one coordinate.
            'fulfillment.lat' => ['nullable', 'numeric', 'between:-90,90', 'required_with:fulfillment.lng'],
            'fulfillment.lng' => ['nullable', 'numeric', 'between:-180,180', 'required_with:fulfillment.lat'],

            'paymentMethod' => ['nullable', Rule::in(['cash', 'ewallet'])],
        ]);

        $organization = Organization::query()
            ->where('slug', $data['orgSlug'])
            ->first();

        abort_if($organization === null, 404, "Organization '{$data['orgSlug']}' not found.");

        $store = Store::query()
            ->where('organization_id', $organization->id)
            ->where('code', $data['storeCode'])
            ->first();

        abort_if($store === null, 404, "Store '{$data['storeCode']}' not found under '{$data['orgSlug']}'.");

        $fulfillment = $data['fulfillment'];
        $isDelivery = $fulfillment['method'] === 'delivery';

        // Quoted before the transaction: it depends only on the store's pin and
        // the drop-off coordinates, neither of which the transaction writes.
        $deliveryFeeCents = 0;
        $deliveryDistanceKm = null;

        if ($isDelivery) {
            try {
                $quote = $this->quoter->quote(
                    $store,
                    isset($fulfillment['lat']) ? (float) $fulfillment['lat'] : null,
                    isset($fulfillment['lng']) ? (float) $fulfillment['lng'] : null,
                );
            } catch (OutsideDeliveryAreaException $e) {
                throw ValidationException::withMessages([
                    'fulfillment.address' => $e->getMessage(),
                ]);
            }

            $deliveryFeeCents = $quote['feeCents'];
            $deliveryDistanceKm = $quote['distanceKm'];
        }

        $result = DB::transaction(function () use (
            $data, $organization, $store, $fulfillment, $isDelivery, $deliveryFeeCents, $deliveryDistanceKm, $customer
        ) {
            $order = $this->recordOrder(
                $data, $organization, $store, $fulfillment, $isDelivery, $deliveryFeeCents, $deliveryDistanceKm,
                $customer?->getKey(),
            );

            // Broadcast only once the transaction commits, or a listener can
            // race ahead and query a row that is not visible yet — or hear
            // about an order a rollback removed. Matches SyncController. The
            // customer's receipt is queued from the same place, for the same
            // reason: the queue is the database.
            DB::afterCommit(function () use ($order, $store) {
                OrderPlaced::dispatch($order);
                $this->emailConfirmation($order, $store);
            });

            return $order;
        });

        return response()->json([
            'orderId' => $result->id,
            'ticketNumber' => $result->ticket_number,
            'totalCents' => $result->total_cents,
            'deliveryFeeCents' => $result->delivery_fee_cents,
        ], 201);
    }

    /**
     * What the customer's order-tracking page renders.
     *
     * Public, keyed on the order's UUID — a storefront customer has no account
     * to authenticate with, so the unguessable id is the capability. That is
     * the same reasoning OrderStatusChanged uses for its public channel, and
     * this endpoint returns the same view of the order: enough to track it,
     * nothing that would matter if the link were forwarded. Notably no guest
     * contact details, which the customer already has and nobody else should.
     */
    public function show(Order $order): JsonResponse
    {
        abort_unless($order->isOnline(), 404);

        // The shape lives on the model, because the signed-in customer's order
        // list returns the same view of an order — see Order::toTrackedArray.
        return response()->json($order->toTrackedArray());
    }

    /**
     * The customer's receipt, when there is somewhere to send it.
     *
     * Checkout takes a phone number or an email, not both (see the validation
     * above), so a good half of orders have no address and simply get nothing
     * — that is not a failure, and it is not worth logging.
     *
     * Failures are swallowed for the same reason they are in SignupController:
     * the order is already recorded and its confirmation is already on its way
     * back to the browser. Losing the receipt must not turn a placed order
     * into a 500 that invites the customer to order again.
     */
    private function emailConfirmation(Order $order, Store $store): void
    {
        $email = $order->guest_contact['email'] ?? null;

        if (! is_string($email) || trim($email) === '') {
            return;
        }

        try {
            Mail::to($email)->queue(new OnlineOrderConfirmationMail($order, $store));
        } catch (\Throwable $e) {
            report($e);
        }
    }

    /**
     * @param  array<string, mixed>  $data
     * @param  array<string, mixed>  $fulfillment
     */
    private function recordOrder(
        array $data,
        Organization $organization,
        Store $store,
        array $fulfillment,
        bool $isDelivery,
        int $deliveryFeeCents,
        ?float $deliveryDistanceKm,
        ?string $customerAccountId = null,
    ): Order {
        $lines = $this->priceLines($data['items'], $organization, $store, $data['businessMode']);

        $subtotalCents = array_sum(array_column($lines, 'lineTotalCents'));
        $taxCents = array_sum(array_column($lines, 'taxCents'));

        $orderId = (string) str()->uuid();
        $guest = $data['guest'];

        $order = Order::query()->create([
            'id' => $orderId,
            'organization_id' => $organization->id,
            'store_id' => $store->id,
            // No till rang this up, and nobody is serving it yet.
            'device_id' => null,
            'user_id' => null,
            // Set when the order was placed from a signed-in portal session,
            // null for guest checkout. It is what makes "your orders" a
            // question the server can answer on a device that has never seen
            // this order before.
            'customer_account_id' => $customerAccountId,
            'ticket_number' => $this->ticketNumberFor($store, $orderId),
            'order_status' => 'preparing',
            'order_type' => 'takeaway',
            'payment_status' => 'unpaid',
            'channel' => 'online',
            'business_mode' => $data['businessMode'],
            'table_number' => null,
            'payment_method' => $data['paymentMethod'] ?? 'cash',
            'subtotal_cents' => $subtotalCents,
            'tax_cents' => $taxCents,
            'total_cents' => $subtotalCents + $taxCents + $deliveryFeeCents,
            'business_date' => now($store->timezone)->toDateString(),
            'completed_at' => null,
            'fulfillment_method' => $fulfillment['method'],
            'delivery_address' => $isDelivery ? trim($fulfillment['address']) : null,
            'delivery_lat' => $isDelivery && isset($fulfillment['lat']) ? $fulfillment['lat'] : null,
            'delivery_lng' => $isDelivery && isset($fulfillment['lng']) ? $fulfillment['lng'] : null,
            'delivery_distance_km' => $deliveryDistanceKm,
            'delivery_fee_cents' => $deliveryFeeCents,
            // The customer-facing rider stage; staff advance order_status
            // separately. Stays null for pickup.
            'delivery_stage' => $isDelivery ? 'pending' : null,
            'rider_name' => null,
            'rider_phone' => null,
            'guest_contact' => array_filter([
                'name' => trim($guest['name']),
                'phone' => isset($guest['phone']) ? trim($guest['phone']) : null,
                'email' => isset($guest['email']) ? trim($guest['email']) : null,
            ], fn ($value) => $value !== null && $value !== ''),
        ]);

        foreach ($lines as $line) {
            OrderItem::query()->create([
                'id' => (string) str()->uuid(),
                'organization_id' => $organization->id,
                'store_id' => $store->id,
                'order_id' => $order->id,
                'product_id' => $line['productId'],
                'product_name' => $line['name'],
                'quantity' => $line['quantity'],
                'unit_price_cents' => $line['unitPriceCents'],
                'line_total_cents' => $line['lineTotalCents'],
            ]);

            if ($line['trackInventory']) {
                $this->decrementStock($organization, $store, $order, $line);
            }
        }

        return $order;
    }

    /**
     * Price and validate every cart line against the merchant's own records.
     *
     * Inventory rows are locked here rather than read: two customers adding the
     * last unit at the same moment must not both succeed.
     *
     * @param  array<int, array<string, mixed>>  $items
     * @return array<int, array<string, mixed>>
     */
    private function priceLines(array $items, Organization $organization, Store $store, string $businessMode): array
    {
        $lines = [];

        foreach ($items as $item) {
            $quantity = (float) $item['quantity'];

            $product = Product::query()
                ->where('organization_id', $organization->id)
                ->whereKey($item['productId'])
                ->first();

            if ($product === null || ! $product->is_active) {
                throw ValidationException::withMessages([
                    'items' => "Product '{$item['productId']}' is not available.",
                ]);
            }

            // An organization can run several business modes; a product only
            // appears in the storefront for the ones it opts into.
            $modes = $product->business_modes ?? [];
            if (! in_array($businessMode, $modes, true)) {
                throw ValidationException::withMessages([
                    'items' => "'{$product->name}' is not available.",
                ]);
            }

            if ($product->track_inventory) {
                $level = InventoryLevel::query()
                    ->where('organization_id', $organization->id)
                    ->where('store_id', $store->id)
                    ->where('product_id', $product->id)
                    ->lockForUpdate()
                    ->first();

                $onHand = (float) ($level->qty_on_hand ?? 0);

                if ($onHand <= 0) {
                    throw ValidationException::withMessages([
                        'items' => "'{$product->name}' is not available.",
                    ]);
                }

                if ($onHand < $quantity) {
                    throw ValidationException::withMessages([
                        'items' => "'{$product->name}' doesn't have enough stock.",
                    ]);
                }
            }

            $lineTotalCents = (int) round($product->price_cents * $quantity);

            $lines[] = [
                'productId' => $product->id,
                'name' => $product->name,
                'quantity' => $quantity,
                'unitPriceCents' => $product->price_cents,
                'lineTotalCents' => $lineTotalCents,
                // tax_rate is stored as a percentage (12.00), unlike the
                // Firestore field it replaces, which held a fraction (0.12).
                'taxCents' => (int) round($lineTotalCents * ((float) $product->tax_rate) / 100),
                'trackInventory' => (bool) $product->track_inventory,
            ];
        }

        return $lines;
    }

    /**
     * @param  array<string, mixed>  $line
     */
    private function decrementStock(Organization $organization, Store $store, Order $order, array $line): void
    {
        $delta = -1 * (float) $line['quantity'];

        InventoryAdjustment::query()->create([
            'id' => (string) str()->uuid(),
            'organization_id' => $organization->id,
            'store_id' => $store->id,
            // No device placed this order.
            'device_id' => null,
            'product_id' => $line['productId'],
            'order_id' => $order->id,
            'adjustment_type' => 'sale',
            'quantity_delta' => $delta,
            'reason' => "online-order:{$order->ticket_number}",
            'created_at' => now(),
            'synced_at' => now(),
        ]);

        $level = InventoryLevel::query()->firstOrNew([
            'organization_id' => $organization->id,
            'store_id' => $store->id,
            'product_id' => $line['productId'],
        ]);

        if (! $level->exists) {
            $level->id = (string) str()->uuid();
            $level->qty_on_hand = 0;
        }

        $level->qty_on_hand = max(0, (float) $level->qty_on_hand + $delta);
        $level->updated_at = now();
        $level->deleted_at = null;
        $level->save();
    }

    /**
     * Short, human-readable, and unique per store — it is what the customer is
     * called by at the counter. Derived from the order's own UUID, retried on
     * the vanishingly rare collision rather than left to hit the unique index.
     */
    private function ticketNumberFor(Store $store, string $orderId): string
    {
        $candidate = strtoupper(substr(str_replace('-', '', $orderId), 0, 8));

        for ($attempt = 0; $attempt < 5; $attempt++) {
            $taken = Order::query()
                ->where('store_id', $store->id)
                ->where('ticket_number', $candidate)
                ->exists();

            if (! $taken) {
                return $candidate;
            }

            $candidate = strtoupper(substr(str_replace('-', '', (string) str()->uuid()), 0, 8));
        }

        return $candidate;
    }
}
