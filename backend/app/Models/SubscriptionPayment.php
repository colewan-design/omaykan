<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Concerns\HasUuids;
use Illuminate\Database\Eloquent\Factories\HasFactory;
use Illuminate\Database\Eloquent\Model;

/**
 * One manual transfer a merchant says they made, and what an operator decided.
 *
 * Collection is deliberately manual and will stay that way — there is no
 * gateway and none planned (documentation/plan.md §4a). This is what "manual"
 * is made of: the merchant's claim, the operator's review, and the period the
 * payment bought.
 *
 * Accepting one is the only thing that calls
 * `SubscriptionBilling::recordPayment()` with a date.
 */
class SubscriptionPayment extends Model
{
    use HasFactory, HasUuids;

    /** Submitted by the merchant, nobody has looked. */
    public const STATUS_SUBMITTED = 'submitted';

    /** An operator matched it to a transfer and said what it covers. */
    public const STATUS_ACCEPTED = 'accepted';

    /** An operator could not match it, with a reason the merchant sees. */
    public const STATUS_REJECTED = 'rejected';

    protected $fillable = [
        'id',
        'subscription_id',
        'organization_id',
        'status',
        'reference',
        'amount_cents',
        'note',
        'submitted_by_user_id',
        'period_start',
        'period_end',
        'reviewed_by_platform_admin_id',
        'reviewed_at',
        'rejection_reason',
    ];

    protected function casts(): array
    {
        return [
            'amount_cents' => 'integer',
            'period_start' => 'datetime',
            'period_end' => 'datetime',
            'reviewed_at' => 'datetime',
        ];
    }

    public function subscription()
    {
        return $this->belongsTo(Subscription::class);
    }

    public function organization()
    {
        return $this->belongsTo(Organization::class);
    }

    public function submittedBy()
    {
        return $this->belongsTo(User::class, 'submitted_by_user_id');
    }

    public function isPending(): bool
    {
        return $this->status === self::STATUS_SUBMITTED;
    }
}
