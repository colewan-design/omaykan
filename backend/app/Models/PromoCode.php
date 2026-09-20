<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Concerns\HasUuids;
use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\SoftDeletes;

/**
 * A promo or voucher code. See the promo_codes migration.
 *
 * `evaluate()` is the whole rulebook, in one place, for the three callers
 * that need it: online checkout, the checkout quote, and the till's counter
 * check. A refusal comes back as a sentence the shopper or cashier can read.
 */
class PromoCode extends Model
{
    use HasUuids, SoftDeletes;

    public const KIND_PERCENT = 'percent';

    public const KIND_AMOUNT = 'amount';

    public const CHANNEL_ONLINE = 'online';

    public const CHANNEL_COUNTER = 'counter';

    public const CHANNEL_BOTH = 'both';

    protected $fillable = [
        'id',
        'organization_id',
        'code',
        'kind',
        'value',
        'min_subtotal_cents',
        'max_discount_cents',
        'channel',
        'starts_at',
        'ends_at',
        'max_redemptions',
        'per_customer_limit',
        'is_active',
        'created_by',
    ];

    protected function casts(): array
    {
        return [
            'value' => 'integer',
            'min_subtotal_cents' => 'integer',
            'max_discount_cents' => 'integer',
            'max_redemptions' => 'integer',
            'per_customer_limit' => 'integer',
            'is_active' => 'boolean',
            'starts_at' => 'datetime',
            'ends_at' => 'datetime',
        ];
    }

    /** Codes are matched case-insensitively and stored upper-case. */
    public static function normalise(string $code): string
    {
        return strtoupper(trim($code));
    }

    public static function findForOrganization(string $organizationId, string $code): ?self
    {
        return self::query()
            ->where('organization_id', $organizationId)
            ->where('code', self::normalise($code))
            ->first();
    }

    public function redemptions()
    {
        return $this->hasMany(OrderDiscount::class);
    }

    /**
     * How many orders have used this code — counted from the orders, not kept
     * as a number that re-syncs would make drift. A deleted order no longer
     * counts; `whereHas` skips soft-deleted rows.
     */
    public function redemptionCount(): int
    {
        return $this->redemptions()
            ->whereHas('order')
            ->distinct('order_id')
            ->count('order_id');
    }

    /**
     * Whether this code applies, and for how much.
     *
     * @param  array{subtotalCents: int, channel: string, customerAccountId?: ?string, guestPhone?: ?string}  $order
     * @return array{ok: true, discountCents: int}|array{ok: false, message: string}
     */
    public function evaluate(array $order): array
    {
        $refuse = fn (string $message) => ['ok' => false, 'message' => $message];

        if (! $this->is_active || $this->trashed()) {
            return $refuse("The code {$this->code} isn't active.");
        }

        if ($this->channel !== self::CHANNEL_BOTH && $this->channel !== $order['channel']) {
            return $refuse($this->channel === self::CHANNEL_ONLINE
                ? "{$this->code} can only be used when ordering online."
                : "{$this->code} can only be used at the counter.");
        }

        if ($this->starts_at !== null && $this->starts_at->isFuture()) {
            return $refuse("{$this->code} starts on {$this->starts_at->format('M j')}.");
        }

        if ($this->ends_at !== null && $this->ends_at->isPast()) {
            return $refuse("{$this->code} has expired.");
        }

        if ($order['subtotalCents'] < $this->min_subtotal_cents) {
            return $refuse(sprintf(
                '%s needs an order of at least ₱%s.',
                $this->code,
                number_format($this->min_subtotal_cents / 100, 2),
            ));
        }

        if ($this->max_redemptions !== null && $this->redemptionCount() >= $this->max_redemptions) {
            return $refuse("{$this->code} has been used up.");
        }

        if ($this->per_customer_limit !== null && $this->usesBy($order) >= $this->per_customer_limit) {
            return $refuse("You've already used {$this->code}.");
        }

        $discount = $this->kind === self::KIND_PERCENT
            ? (int) round($order['subtotalCents'] * $this->value / 10000)
            : $this->value;

        if ($this->max_discount_cents !== null) {
            $discount = min($discount, $this->max_discount_cents);
        }

        return ['ok' => true, 'discountCents' => max(0, min($discount, $order['subtotalCents']))];
    }

    /**
     * Uses by this customer: a signed-in shopper by account, a guest by phone.
     * A shopper with neither cannot be told apart from anyone else, so the
     * per-customer limit does not count them — the redemption cap still does.
     *
     * @param  array<string, mixed>  $order
     */
    private function usesBy(array $order): int
    {
        $account = $order['customerAccountId'] ?? null;
        $phone = $order['guestPhone'] ?? null;

        if ($account === null && ($phone === null || $phone === '')) {
            return 0;
        }

        return $this->redemptions()
            ->whereHas('order', function ($query) use ($account, $phone) {
                $query->where(function ($who) use ($account, $phone) {
                    if ($account !== null) {
                        $who->orWhere('customer_account_id', $account);
                    }
                    if ($phone !== null && $phone !== '') {
                        $who->orWhere('guest_contact->phone', $phone);
                    }
                });
            })
            ->distinct('order_id')
            ->count('order_id');
    }

    /** "10% off", "₱50 off", "20% off, up to ₱100". */
    public function describe(): string
    {
        $what = $this->kind === self::KIND_PERCENT
            ? rtrim(rtrim(number_format($this->value / 100, 2), '0'), '.').'% off'
            : '₱'.number_format($this->value / 100, 2).' off';

        return $this->max_discount_cents !== null && $this->kind === self::KIND_PERCENT
            ? $what.', up to ₱'.number_format($this->max_discount_cents / 100, 2)
            : $what;
    }
}
