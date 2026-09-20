<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Concerns\HasUuids;
use Illuminate\Database\Eloquent\Factories\HasFactory;
use Illuminate\Database\Eloquent\Model;

/**
 * One manual transfer a merchant says they made, and what an operator decided.
 *
 * Two provenances, told apart by `source`.
 *
 * `manual` is the original meaning and still the default: the merchant's
 * claim, an operator's review, and the period the payment bought. `paymongo`
 * is a hosted GCash checkout that settled — nobody typed it and nobody needs
 * to believe the merchant for it to be true, so it is written straight to
 * `accepted` and never appears in the operator's queue
 * (documentation/plan.md §4a, changed 2026-09-20).
 *
 * Either way, accepting one is the only thing that calls
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

    /** Written by an operator reviewing a claim, or by a gateway settlement. */
    public const SOURCE_MANUAL = 'manual';

    public const SOURCE_PAYMONGO = 'paymongo';

    protected $fillable = [
        'id',
        'subscription_id',
        'source',
        'provider_session_id',
        'provider_payment_id',
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
