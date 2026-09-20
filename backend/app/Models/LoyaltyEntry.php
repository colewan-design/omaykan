<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Concerns\HasUuids;
use Illuminate\Database\Eloquent\Model;

/**
 * One movement of points. The balance is the sum; nothing is ever edited.
 */
class LoyaltyEntry extends Model
{
    use HasUuids;

    public const EARN = 'earn';

    public const REDEEM = 'redeem';

    public const EXPIRE = 'expire';

    public const ADJUST = 'adjust';

    protected $fillable = [
        'organization_id',
        'pos_customer_id',
        'order_id',
        'reason',
        'points',
        'note',
        'created_by',
        'expires_at',
    ];

    protected function casts(): array
    {
        return [
            'points' => 'integer',
            'expires_at' => 'datetime',
        ];
    }

    public function customer()
    {
        return $this->belongsTo(PosCustomer::class, 'pos_customer_id');
    }
}
