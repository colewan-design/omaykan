<?php

namespace App\Http\Controllers\Api;

use App\Events\OrderPlaced;
use App\Http\Controllers\Controller;
use App\Mail\OnlineOrderConfirmationMail;
use App\Models\InventoryAdjustment;
use App\Models\InventoryLevel;
use App\Models\Order;
use App\Models\OrderDiscount;
use App\Models\OrderItem;
use App\Models\OrderPushToken;
use App\Models\Organization;
use App\Models\Product;
use App\Models\ProductStoreOverride;
use App\Models\PromoCode;
use App\Models\Store;
use App\Services\Billing\TenantAccessDenied;
use App\Services\DeliveryQuoter;
use App\Services\OrderPricing;
use App\Services\OutsideDeliveryAreaException;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;
use Illuminate\Http\Response;
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
    /** Phones per order: a household, not a broadcast list. */
    private const PUSH_TOKENS_PER_ORDER = 5;

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
            'businessMode' => ['required', Rule::in(Store::ONLINE_MODES)],

            'items' => ['required', 'array', 'min:1'],
            // Shape-checked, not existence-checked — priceLines still has to
            // look the product up. What this buys is that a cart line carrying
            // something that is not an id at all is rejected here, before it
            // reaches a uuid column that would raise a driver error rather
            // than simply not match. See priceLines.
            'items.*.productId' => ['required', 'uuid'],
            'items.*.quantity' => ['required', 'numeric', 'gt:0'],

            'guest.name' => ['required', 'string', 'max:120'],
            'guest.phone' => ['nullable', 'string', 'max:40', 'required_without:guest.email'],
            'guest.email' => ['nullable', 'email', 'max:190', 'required_without:guest.phone'],

            'fulfillment.method' => ['required', Rule::in(['pickup', 'delivery'])],
            'fulfillment.address' => ['nullable', 'string', 'max:500', 'required_if:fulfillment.method,delivery'],
            // How the rider finds the door, beside the address that names the
            // street. Optional on purpose — plenty of addresses need no
            // landmark, and an order must never fail for want of one. Absent
            // for pickup, and from any client that predates the field.
            'fulfillment.landmark' => ['nullable', 'string', 'max:200'],
            // A malformed pair must not silently fall through to the flat-fee
            // path — that would let a caller dodge the distance surcharge by
            // sending only one coordinate.
            'fulfillment.lat' => ['nullable', 'numeric', 'between:-90,90', 'required_with:fulfillment.lng'],
            'fulfillment.lng' => ['nullable', 'numeric', 'between:-180,180', 'required_with:fulfillment.lat'],

            'paymentMethod' => ['nullable', Rule::in(['cash', 'ewallet'])],

            // A promo or voucher code. Checked inside the order's transaction,
            // with the code's row locked, so two shoppers cannot both take its
            // last use. See recordOrder.
            'promoCode' => ['nullable', 'string', 'max:40'],
        ], [
            // The storefront mirrors the cart into localStorage, so a line put
            // there by an older catalog outlives the visit that created it and
            // comes back on every checkout. The default message names a field
            // and a format the shopper cannot act on; this one names the one
            // thing that clears it.
            'items.*.productId.uuid' => 'Your cart is out of date. Please empty it and add your items again.',
        ]);

        [$organization, $store] = $this->tradingStore($data['orgSlug'], $data['storeCode']);

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
            //
            // Registered as two callbacks, each swallowing its own failures.
            // Laravel runs after-commit callbacks *outside* the try/catch that
            // wraps the transaction, so anything thrown here escapes as a 500
            // on an order that is already committed and already has its stock
            // decremented — and the customer, told the order failed, places it
            // again. Neither the register's strip nor the receipt is worth
            // that, so both only ever get reported.
            DB::afterCommit(fn () => $this->announce($order));
            DB::afterCommit(fn () => $this->emailConfirmation($order, $store));

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
     * Remember a phone that wants a push when a rider takes this order.
     *
     * Delivery orders only; nobody rides a pickup. Capped per order, because
     * the UUID travels freely as a tracking link and a link should not be a
     * way to fill a table.
     */
    public function registerPushToken(Request $request, Order $order): Response
    {
        abort_unless($order->isOnline(), 404);
        abort_unless($order->fulfillment_method === 'delivery', 422, 'Only delivery orders have a rider to announce.');

        $data = $request->validate([
            'token' => ['required', 'string', 'max:512'],
        ]);

        $tokens = OrderPushToken::query()->where('order_id', $order->id);

        if (! (clone $tokens)->where('token', $data['token'])->exists()) {
            abort_if(
                $tokens->count() >= self::PUSH_TOKENS_PER_ORDER,
                422,
                'This order already has as many phones watching it as it can take.',
            );

            OrderPushToken::query()->create(['order_id' => $order->id, 'token' => $data['token']]);
        }

        return response()->noContent();
    }

    /**
     * Tell the store's register that an order arrived.
     *
     * OrderPlaced is a ShouldBroadcast event, so this only ever writes a job
     * to the queue — a slow or down Reverb cannot reach this far. What it does
     * catch is a queue that cannot be written to at all, which must not cost
     * the customer their already-recorded order.
     */
    private function announce(Order $order): void
    {
        try {
            OrderPlaced::dispatch($order);
        } catch (\Throwable $e) {
            report($e);
        }
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
        $guest = $data['guest'];

        // Locked, so the redemption count this reads cannot change under it
        // before the order that uses the code is written.
        [$promo, $requestedDiscount] = $this->applyPromo(
            $data['promoCode'] ?? null,
            $organization,
            array_sum(array_column($lines, 'lineTotalCents')),
            $customerAccountId,
            isset($guest['phone']) ? trim($guest['phone']) : null,
            lock: true,
        );

        // The same arithmetic the till and the storefront's quote use: VAT per
        // line, on what is left of the line after its share of the discount.
        $priced = OrderPricing::price($lines, $requestedDiscount);
        $subtotalCents = $priced['subtotalCents'];
        $discountCents = $priced['discountCents'];
        $taxCents = $priced['taxCents'];

        $orderId = (string) str()->uuid();

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
            'discount_cents' => $discountCents,
            'tax_cents' => $taxCents,
            'total_cents' => $subtotalCents - $discountCents + $taxCents + $deliveryFeeCents,
            'business_date' => now($store->timezone)->toDateString(),
            'completed_at' => null,
            'fulfillment_method' => $fulfillment['method'],
            'delivery_address' => $isDelivery ? trim($fulfillment['address']) : null,
            // Whitespace alone is not a landmark. Stored as null rather than
            // '' so "has a landmark" is one check everywhere downstream.
            'delivery_landmark' => $isDelivery
                ? (trim($fulfillment['landmark'] ?? '') ?: null)
                : null,
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
                'tax_rate' => $line['taxRatePercent'],
            ]);

            if ($line['trackInventory']) {
                $this->decrementStock($organization, $store, $order, $line);
            }
        }

        if ($promo !== null && $discountCents > 0) {
            OrderDiscount::query()->create([
                'order_id' => $order->id,
                'kind' => OrderDiscount::KIND_PROMO,
                'amount_cents' => $discountCents,
                'percent' => $promo->kind === PromoCode::KIND_PERCENT ? $promo->value / 100 : null,
                'reason' => $promo->code,
                'promo_code_id' => $promo->id,
            ]);
        }

        return $order;
    }

    /**
     * What this basket would cost, before it is ordered: subtotal, any promo
     * code's discount, VAT, and the delivery fee — the same arithmetic the
     * order itself is priced with, so the number the shopper agrees to is the
     * number they are charged.
     *
     * A code that does not apply is not an error here: the basket is priced
     * without it and `promo.message` says why, so the cart can show the reason
     * beside the code field. Stock and availability are checked the way
     * checkout checks them, so a problem surfaces before the shopper has typed
     * an address.
     */
    public function quote(Request $request): JsonResponse
    {
        $data = $request->validate([
            'orgSlug' => ['required', 'string'],
            'storeCode' => ['required', 'string'],
            'businessMode' => ['required', Rule::in(Store::ONLINE_MODES)],
            'items' => ['required', 'array', 'min:1'],
            'items.*.productId' => ['required', 'uuid'],
            'items.*.quantity' => ['required', 'numeric', 'gt:0'],
            'fulfillment.method' => ['nullable', Rule::in(['pickup', 'delivery'])],
            'fulfillment.lat' => ['nullable', 'numeric', 'between:-90,90', 'required_with:fulfillment.lng'],
            'fulfillment.lng' => ['nullable', 'numeric', 'between:-180,180', 'required_with:fulfillment.lat'],
            'guest.phone' => ['nullable', 'string', 'max:40'],
            'promoCode' => ['nullable', 'string', 'max:40'],
        ]);

        [$organization, $store] = $this->tradingStore($data['orgSlug'], $data['storeCode']);
        $customer = $request->user('customer');

        $lines = $this->priceLines($data['items'], $organization, $store, $data['businessMode']);
        $subtotal = array_sum(array_column($lines, 'lineTotalCents'));

        $promo = null;
        $promoResult = null;
        $discount = 0;

        if (($data['promoCode'] ?? '') !== '') {
            try {
                [$promo, $discount] = $this->applyPromo(
                    $data['promoCode'],
                    $organization,
                    $subtotal,
                    $customer?->getKey(),
                    $data['guest']['phone'] ?? $customer?->phone,
                    lock: false,
                );
                $promoResult = ['ok' => true, 'code' => $promo->code, 'description' => $promo->describe()];
            } catch (ValidationException $e) {
                $promoResult = [
                    'ok' => false,
                    'code' => PromoCode::normalise($data['promoCode']),
                    'message' => $e->errors()['promoCode'][0] ?? 'That code does not apply.',
                ];
            }
        }

        $deliveryFeeCents = 0;
        if (($data['fulfillment']['method'] ?? 'pickup') === 'delivery') {
            try {
                $deliveryFeeCents = $this->quoter->quote(
                    $store,
                    isset($data['fulfillment']['lat']) ? (float) $data['fulfillment']['lat'] : null,
                    isset($data['fulfillment']['lng']) ? (float) $data['fulfillment']['lng'] : null,
                )['feeCents'];
            } catch (OutsideDeliveryAreaException $e) {
                throw ValidationException::withMessages(['fulfillment.address' => $e->getMessage()]);
            }
        }

        $priced = OrderPricing::price($lines, $discount);

        return response()->json([
            'subtotalCents' => $priced['subtotalCents'],
            'discountCents' => $priced['discountCents'],
            'taxCents' => $priced['taxCents'],
            'deliveryFeeCents' => $deliveryFeeCents,
            'totalCents' => $priced['totalCents'] + $deliveryFeeCents,
            'promo' => $promoResult,
        ]);
    }

    /**
     * The organization and store a storefront request names, refused unless it
     * may take an order right now. Shared by checkout and its quote, so the
     * two can never disagree about whether a shop is open.
     *
     * @return array{0: Organization, 1: Store}
     */
    private function tradingStore(string $orgSlug, string $storeCode): array
    {
        $organization = Organization::query()
            ->with('subscription')
            ->where('slug', $orgSlug)
            ->first();

        abort_if($organization === null, 404, "Organization '{$orgSlug}' not found.");

        $store = Store::query()
            ->where('organization_id', $organization->id)
            ->where('code', $storeCode)
            ->first();

        abort_if($store === null, 404, "Store '{$storeCode}' not found under '{$orgSlug}'.");

        // Checked here and not only by the catalog. The storefront mirrors its
        // cart into localStorage, so a checkout can arrive long after the page
        // that built it — from before the shop was suspended, or from a tab
        // nobody closed. The catalog refusing is a courtesy; this is the rule.
        //
        // The store's own status too, which this endpoint never checked: the
        // catalog 404s an inactive store, and an order for one used to go
        // straight through regardless.
        if ($store->status !== 'active' || ! $organization->accessVerdict()->allowsStorefront()) {
            throw TenantAccessDenied::forStorefront();
        }

        // The shop's own pause, as against the platform closing it above. A
        // 422 with the resume time, not the 404: the shop is there and
        // trading, and the shopper can come back at four.
        if ($store->isOrderingPaused()) {
            abort(422, $store->orderingPausedMessage());
        }

        return [$organization, $store];
    }

    /**
     * A promo code, checked: the code and how much it takes off, or no code.
     *
     * A code that does not apply is a 422 on `promoCode` with the reason — "has
     * expired", "needs an order of at least ₱500" — and never a silent full
     * price: a shopper who typed a code and was charged in full would be right
     * to feel cheated.
     *
     * @return array{0: ?PromoCode, 1: int}
     */
    private function applyPromo(
        ?string $code,
        Organization $organization,
        int $subtotalCents,
        ?string $customerAccountId,
        ?string $guestPhone,
        bool $lock,
    ): array {
        if ($code === null || trim($code) === '') {
            return [null, 0];
        }

        $promo = PromoCode::query()
            ->where('organization_id', $organization->id)
            ->where('code', PromoCode::normalise($code))
            ->when($lock, fn ($query) => $query->lockForUpdate())
            ->first();

        if ($promo === null) {
            throw ValidationException::withMessages([
                'promoCode' => 'That code isn\'t valid for this shop.',
            ]);
        }

        $verdict = $promo->evaluate([
            'subtotalCents' => $subtotalCents,
            'channel' => PromoCode::CHANNEL_ONLINE,
            'customerAccountId' => $customerAccountId,
            'guestPhone' => $guestPhone,
        ]);

        if (! $verdict['ok']) {
            throw ValidationException::withMessages(['promoCode' => $verdict['message']]);
        }

        return [$promo, $verdict['discountCents']];
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

            // The branch's own price and availability, as the storefront
            // catalog shows them. Without this the shopper saw one price and
            // was charged the organization's.
            $override = ProductStoreOverride::query()
                ->where('store_id', $store->id)
                ->where('product_id', $product->id)
                ->first();

            // is_available is nullable — null means "no opinion, inherit".
            if ($override?->is_available === false) {
                throw ValidationException::withMessages([
                    'items' => "'{$product->name}' is not available.",
                ]);
            }

            $unitPriceCents = (int) ($override?->price_cents ?? $product->price_cents);

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

            $lineTotalCents = (int) round($unitPriceCents * $quantity);

            $lines[] = [
                'productId' => $product->id,
                'name' => $product->name,
                'quantity' => $quantity,
                'unitPriceCents' => $unitPriceCents,
                'lineTotalCents' => $lineTotalCents,
                // tax_rate is stored as a percentage (12.00), unlike the
                // Firestore field it replaces, which held a fraction (0.12).
                // Tax itself is computed by OrderPricing, after any discount.
                'taxRatePercent' => (float) $product->tax_rate,
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
