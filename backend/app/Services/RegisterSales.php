<?php

namespace App\Services;

use App\Events\OrderPlaced;
use App\Models\InventoryAdjustment;
use App\Models\InventoryLevel;
use App\Models\LoyaltyEntry;
use App\Models\Order;
use App\Models\OrderDiscount;
use App\Models\OrderItem;
use App\Models\Payment;
use App\Models\PosCustomer;
use App\Models\Product;
use App\Models\PromoCode;
use App\Models\User;
use App\Services\Loyalty\Loyalty;
use Illuminate\Support\Carbon;
use Illuminate\Support\Facades\DB;
use Illuminate\Validation\ValidationException;

/**
 * A sale rung up at the till, and its void — how both become the server's
 * record. Callers run these inside a transaction.
 *
 * The till is online-only: it sends the sale here before the cashier finishes
 * it, and a refusal comes back to the payment sheet in time to fix it. So a
 * sale whose totals do not add up, whose discount is more than the cashier's
 * role may give, whose promo code no longer applies or whose points the
 * customer does not have is refused with the reason, and nothing is written.
 *
 * Tax itself is not recomputed: it depends on each line's rate at the moment
 * of sale, which is exactly what the till knew.
 */
class RegisterSales
{
    public function __construct(
        private readonly RolePermissions $roles,
        private readonly Loyalty $loyalty,
    ) {}

    /**
     * Record a sale, or throw a ValidationException naming what is wrong
     * with it.
     *
     * A sale the server already has — the till retrying after a lost
     * response, or a stale copy of one since voided — is returned untouched.
     *
     * @param  array<string, mixed>  $payload  `{order, items, discounts, payments}`
     */
    public function record(StoreContext $context, array $payload): Order
    {
        $orderData = $payload['order'];
        $items = $payload['items'] ?? [];
        $payments = $payload['payments'] ?? [];

        $known = Order::withTrashed()->whereKey($orderData['id'])->first();

        if ($known !== null) {
            if ($known->store_id !== $context->storeId()) {
                throw ValidationException::withMessages(['order' => 'That sale belongs to another store.']);
            }

            return $known;
        }

        $order = Order::query()->create([
            'id' => $orderData['id'],
            'organization_id' => $context->organizationId(),
            'store_id' => $context->storeId(),
            // The person signed in on the till that rang it up.
            'user_id' => $context->user->id,
            'ticket_number' => $orderData['ticketNumber'],
            'order_status' => $orderData['orderStatus'] ?? 'completed',
            'order_type' => $orderData['orderType'] ?? 'takeaway',
            'payment_status' => $orderData['paymentStatus'] ?? 'paid',
            'subtotal_cents' => $orderData['subtotalCents'] ?? 0,
            'discount_cents' => max(0, (int) ($orderData['discountCents'] ?? 0)),
            // The named customer, when the cashier chose one — and only one
            // this organization has. The till sends a new customer to the
            // server before the sale that names them.
            'pos_customer_id' => is_string($orderData['customerId'] ?? null)
                && PosCustomer::query()
                    ->where('organization_id', $context->organizationId())
                    ->whereKey($orderData['customerId'])
                    ->exists()
                ? $orderData['customerId']
                : null,
            'tax_cents' => $orderData['taxCents'] ?? 0,
            'total_cents' => $orderData['totalCents'] ?? 0,
            'business_date' => $orderData['businessDate'] ?? now()->toDateString(),
            'completed_at' => $orderData['completedAt'] ?? now(),
        ]);

        foreach ($items as $item) {
            $orderItem = OrderItem::query()->create([
                'id' => $item['id'] ?? (string) str()->uuid(),
                'organization_id' => $context->organizationId(),
                'store_id' => $context->storeId(),
                'order_id' => $order->id,
                'product_id' => $item['productId'] ?? null,
                'product_name' => $item['productName'] ?? $item['name'] ?? 'Unknown product',
                'quantity' => $item['quantity'] ?? 1,
                'unit_price_cents' => $item['unitPriceCents'] ?? 0,
                'line_total_cents' => $item['lineTotalCents'] ?? 0,
                'tax_rate' => array_key_exists('taxRate', $item) && $item['taxRate'] !== null
                    ? self::percentTaxRate($item['taxRate'])
                    : null,
            ]);

            $product = ! empty($item['productId'])
                ? Product::query()->find($item['productId'])
                : null;

            if ($product?->track_inventory) {
                $this->adjustStock(
                    $context,
                    (string) str()->uuid(),
                    $item['productId'],
                    -1 * (float) ($item['quantity'] ?? 1),
                    'sale',
                    "order:{$order->ticket_number}",
                    $order->id,
                    $orderItem->created_at ?? now(),
                );
            }
        }

        $problems = $this->recordDiscounts($context, $order, $payload['discounts'] ?? []);
        $problems += $this->totalsProblems($context, $order->fresh(['items', 'discounts']));

        if ($problems !== []) {
            // Thrown inside the caller's transaction, so none of the above is
            // kept. The first reason is the one the cashier can act on.
            throw ValidationException::withMessages(['order' => array_values($problems)[0]]);
        }

        // After the discounts: points are earned on what was paid for.
        $this->loyalty->earnFor($order->fresh());

        foreach ($payments as $payment) {
            Payment::query()->create([
                'id' => $payment['id'] ?? (string) str()->uuid(),
                'organization_id' => $context->organizationId(),
                'store_id' => $context->storeId(),
                'order_id' => $order->id,
                'payment_method' => $payment['paymentMethod'] ?? 'cash',
                'amount_cents' => $payment['amountCents'] ?? ($orderData['totalCents'] ?? 0),
                'tendered_cents' => $payment['tenderedCents'] ?? null,
                'change_cents' => $payment['changeCents'] ?? null,
            ]);
        }

        // Broadcast only once the surrounding transaction commits — otherwise
        // a listener can race ahead and query a row that is not visible yet,
        // or hear about an order the rollback removed.
        DB::afterCommit(fn () => OrderPlaced::dispatch($order));

        return $order;
    }

    /**
     * Void a sale: soft-deleted, so reports, the promo redemption count and
     * the portal's cancelled tab stop counting it; its stock goes back on the
     * shelf; the points it earned are taken back and those it spent returned.
     * A customer who has already spent what it earned can go below zero,
     * which is what the void means.
     *
     * Voiding twice is a no-op.
     *
     * @param  array{voidedByUserId?: mixed, reason?: mixed, voidedAt?: mixed}  $input
     */
    public function void(StoreContext $context, Order $order, array $input): Order
    {
        if ($order->trashed()) {
            return $order;
        }

        $voidedBy = $input['voidedByUserId'] ?? null;
        if (! is_string($voidedBy) || ! User::query()->whereKey($voidedBy)->exists()) {
            $voidedBy = $context->user->id;
        }

        $reason = isset($input['reason']) ? trim((string) $input['reason']) : '';

        foreach ($order->items as $item) {
            $product = $item->product_id ? Product::query()->find($item->product_id) : null;

            if ($product?->track_inventory) {
                $this->adjustStock(
                    $context,
                    (string) str()->uuid(),
                    $item->product_id,
                    (float) $item->quantity,
                    'manual_correction',
                    "void:{$order->ticket_number}",
                    $order->id,
                    now(),
                );
            }
        }

        LoyaltyEntry::query()
            ->where('order_id', $order->id)
            ->whereIn('reason', [LoyaltyEntry::EARN, LoyaltyEntry::REDEEM])
            ->delete();

        $order->forceFill([
            'voided_by_user_id' => $voidedBy,
            'void_reason' => $reason === '' ? null : mb_substr($reason, 0, 240),
            'deleted_at' => ! empty($input['voidedAt']) ? Carbon::parse($input['voidedAt']) : now(),
        ])->save();

        return $order;
    }

    /**
     * One stock movement, and the level it moves.
     */
    public function adjustStock(
        StoreContext $context,
        string $adjustmentId,
        string $productId,
        float $quantityDelta,
        string $adjustmentType,
        ?string $reason,
        ?string $orderId,
        Carbon|string $createdAt,
    ): void {
        InventoryAdjustment::query()->updateOrCreate(
            ['id' => $adjustmentId],
            [
                'organization_id' => $context->organizationId(),
                'store_id' => $context->storeId(),
                'product_id' => $productId,
                'order_id' => $orderId,
                'adjustment_type' => $adjustmentType,
                'quantity_delta' => $quantityDelta,
                'reason' => $reason,
                'created_at' => $createdAt,
                'synced_at' => now(),
                'deleted_at' => null,
            ],
        );

        $inventoryLevel = InventoryLevel::query()->firstOrNew([
            'organization_id' => $context->organizationId(),
            'store_id' => $context->storeId(),
            'product_id' => $productId,
        ]);

        if (! $inventoryLevel->exists) {
            $inventoryLevel->id = (string) str()->uuid();
            $inventoryLevel->qty_on_hand = 0;
        }

        $inventoryLevel->organization_id = $context->organizationId();
        $inventoryLevel->store_id = $context->storeId();
        $inventoryLevel->product_id = $productId;
        $inventoryLevel->qty_on_hand = max(0, (float) $inventoryLevel->qty_on_hand + $quantityDelta);
        $inventoryLevel->updated_at = now();
        $inventoryLevel->deleted_at = null;
        $inventoryLevel->save();
    }

    /**
     * The order's discounts, one row each.
     *
     * `applied_by` must be a real account: a till signed in with a local-only
     * user has an id the server has never seen. Anything unknown is credited
     * to the person whose token sent the sale.
     *
     * A `promo` discount names the code the till checked
     * (PromoCodeController::check), and is checked once more here — the code
     * could have been used up or expired since. Its row is locked, so two
     * tills cannot both take its last use.
     *
     * @param  array<int, mixed>  $discounts
     * @return array<string, string> problem => the cashier's reason
     */
    private function recordDiscounts(StoreContext $context, Order $order, array $discounts): array
    {
        $problems = [];

        foreach ($discounts as $discount) {
            if (! is_array($discount) || ($discount['amountCents'] ?? 0) <= 0) {
                continue;
            }

            $appliedBy = $discount['appliedByUserId'] ?? null;
            if (! is_string($appliedBy) || ! User::query()->whereKey($appliedBy)->exists()) {
                $appliedBy = $context->user->id;
            }

            $reason = isset($discount['reason']) ? trim((string) $discount['reason']) : '';

            if (($discount['kind'] ?? null) === OrderDiscount::KIND_LOYALTY) {
                $problems += $this->recordLoyaltyDiscount($order, $discount, $appliedBy);

                continue;
            }

            $promo = ($discount['kind'] ?? null) === OrderDiscount::KIND_PROMO && is_string($discount['promoCodeId'] ?? null)
                ? PromoCode::withTrashed()
                    ->where('organization_id', $context->organizationId())
                    ->whereKey($discount['promoCodeId'])
                    ->lockForUpdate()
                    ->first()
                : null;

            if ($promo !== null) {
                $verdict = $promo->evaluate([
                    'subtotalCents' => (int) $order->subtotal_cents,
                    'channel' => PromoCode::CHANNEL_COUNTER,
                ]);

                if (! $verdict['ok']) {
                    $problems['promo_not_valid'] = $verdict['message'];
                }
            }

            OrderDiscount::query()->create([
                'order_id' => $order->id,
                // Only kinds the server can stand behind are recorded as
                // themselves; a promo naming no code of this shop's is manual.
                'kind' => $promo !== null ? OrderDiscount::KIND_PROMO : OrderDiscount::KIND_MANUAL,
                'amount_cents' => (int) $discount['amountCents'],
                'percent' => isset($discount['percent']) ? (float) $discount['percent'] : null,
                'reason' => $promo?->code ?? ($reason === '' ? null : mb_substr($reason, 0, 120)),
                'applied_by' => $appliedBy,
                'promo_code_id' => $promo?->id,
            ]);
        }

        return $problems;
    }

    /**
     * Points spent as a discount: an `order_discounts` row for the money, and
     * a `redeem` entry in the ledger for the points.
     *
     * @param  array<string, mixed>  $discount
     * @return array<string, string>
     */
    private function recordLoyaltyDiscount(Order $order, array $discount, string $appliedBy): array
    {
        $points = max(0, (int) ($discount['points'] ?? 0));
        // Locked, so two tills cannot spend the same points at once.
        $customer = $order->pos_customer_id
            ? PosCustomer::query()->lockForUpdate()->find($order->pos_customer_id)
            : null;
        $problems = [];

        if ($customer === null || $points === 0) {
            $problems['loyalty_without_customer'] = 'Points can only come off a sale with the customer chosen. Choose them, or remove the points.';
        } elseif (! $this->loyalty->recordRedemption($order, $customer, $points, $appliedBy)) {
            $problems['loyalty_overdrawn'] = "{$customer->name} doesn't have {$points} points to spend.";
        }

        OrderDiscount::query()->create([
            'order_id' => $order->id,
            'kind' => $customer !== null && $points > 0 ? OrderDiscount::KIND_LOYALTY : OrderDiscount::KIND_MANUAL,
            'amount_cents' => (int) $discount['amountCents'],
            'reason' => $points > 0 ? "{$points} points" : null,
            'applied_by' => $appliedBy,
        ]);

        return $problems;
    }

    /**
     * Whether the sale's own numbers hold together, and whether its discount
     * is within the sender's role.
     *
     * - `subtotal_mismatch` — the lines do not add up to the subtotal.
     * - `discount_mismatch` — the discount rows do not add up to the total off.
     * - `total_mismatch` — subtotal − discount + tax is not the total.
     * - `discount_over_limit` — more off than the sender's role may give
     *   (RolePermissions::maxDiscountPercent). A promo code or points are not
     *   the cashier's own discretion, so they do not count here.
     *
     * @return array<string, string>
     */
    private function totalsProblems(StoreContext $context, Order $order): array
    {
        $problems = [];
        $totalsWrong = "This sale's totals don't add up, so it wasn't recorded. Reload the till and ring it up again.";

        $lines = (int) $order->items->sum('line_total_cents');
        if ($order->items->isNotEmpty() && $lines !== (int) $order->subtotal_cents) {
            $problems['subtotal_mismatch'] = $totalsWrong;
        }

        $discounted = (int) $order->discounts->sum('amount_cents');
        if ($discounted !== (int) $order->discount_cents) {
            $problems['discount_mismatch'] = $totalsWrong;
        }

        $expected = (int) $order->subtotal_cents - (int) $order->discount_cents + (int) $order->tax_cents;
        if ($expected !== (int) $order->total_cents) {
            $problems['total_mismatch'] = $totalsWrong;
        }

        $discretionary = (int) $order->discounts->where('kind', OrderDiscount::KIND_MANUAL)->sum('amount_cents');

        if ($discretionary > 0 && (int) $order->subtotal_cents > 0) {
            $percent = ($discretionary / (int) $order->subtotal_cents) * 100;
            $limit = $this->roles->maxDiscountPercent($context->organizationId(), $context->role);

            if ($percent > $limit + 1e-9) {
                $problems['discount_over_limit'] = $limit === 0
                    ? 'Your role cannot give discounts. Ask a manager to sign in and apply it.'
                    : "Your role can take up to {$limit}% off a sale. Ask a manager to sign in and apply this discount.";
            }
        }

        return $problems;
    }

    /**
     * A VAT rate, as the percentage `order_items.tax_rate` holds (12.00).
     *
     * The till works in fractions (0.12) and the seller app echoes back the
     * percentage it read, so both arrive here. At or under 1 is a fraction —
     * no real VAT rate is 1% or less.
     */
    public static function percentTaxRate(mixed $value): float
    {
        $rate = is_numeric($value) ? (float) $value : 0.0;

        return $rate > 0 && $rate <= 1 ? round($rate * 100, 2) : round(max(0.0, $rate), 2);
    }
}
