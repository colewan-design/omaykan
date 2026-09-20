<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Concerns\HasUuids;
use Illuminate\Database\Eloquent\Model;

/**
 * One discount on one order: how much, of what kind, why, and who gave it.
 * See the order_discounts migration.
 */
class OrderDiscount extends Model
{
    use HasUuids;

    public const KIND_MANUAL = 'manual';

    /** A promo or voucher code; `promo_code_id` says which. */
    public const KIND_PROMO = 'promo';

    /** Points spent; the ledger entry for the same order says how many. */
    public const KIND_LOYALTY = 'loyalty';

    protected $fillable = [
        'id',
        'order_id',
        'kind',
        'amount_cents',
        'percent',
        'reason',
        'applied_by',
        'promo_code_id',
    ];

    protected function casts(): array
    {
        return [
            'amount_cents' => 'integer',
            'percent' => 'float',
        ];
    }

    public function order()
    {
        return $this->belongsTo(Order::class);
    }

    public function promoCode()
    {
        return $this->belongsTo(PromoCode::class);
    }
}
