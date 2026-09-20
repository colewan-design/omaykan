<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Concerns\HasUuids;
use Illuminate\Database\Eloquent\Factories\HasFactory;
use Illuminate\Database\Eloquent\Model;

class Subscription extends Model
{
    use HasFactory, HasUuids;

    /**
     * Submitted, nobody has looked yet.
     *
     * **Not the same as unpaid**, and must not be treated as such while
     * verification is an operator clicking a button by hand: a merchant cannot
     * be penalised for the length of our queue. See `isCurrent`.
     */
    public const STATUS_PENDING = 'pending_verification';

    public const STATUS_ACTIVE = 'active';

    public const STATUS_REJECTED = 'rejected';

    /**
     * The paid period ran out and nothing renewed it.
     *
     * Still usable for `billing.grace_days` afterwards — a lapsed payment is
     * usually an expired card rather than a decision, and the cost of being
     * wrong is a till that will not ring up a queue of customers.
     *
     * Written by `billing:advance-subscriptions` via
     * `SubscriptionBilling::advance()`, daily. See
     * documentation/subscription-and-suspension.md §6.4.
     */
    public const STATUS_PAST_DUE = 'past_due';

    protected $fillable = [
        'id',
        'organization_id',
        'status',
        'plan',
        'amount_cents',
        'gcash_reference',
        'submitted_at',
        'verified_at',
        'rejection_reason',
        'trial_ends_at',
        'current_period_ends_at',
        'dunning_stage',
        'dunned_at',
    ];

    protected function casts(): array
    {
        return [
            'submitted_at' => 'datetime',
            'verified_at' => 'datetime',
            'trial_ends_at' => 'datetime',
            'current_period_ends_at' => 'datetime',
            'dunned_at' => 'datetime',
        ];
    }

    public function organization()
    {
        return $this->belongsTo(Organization::class);
    }

    /**
     * The manual transfers a merchant has told us about, newest first.
     *
     * Replaces `gcash_reference`, which could only ever hold the first one.
     * That column is left in place for the signup rows that have it, but
     * nothing writes it any more — see SubscriptionPayment.
     */
    public function payments()
    {
        return $this->hasMany(SubscriptionPayment::class)->latest();
    }

    /**
     * Whether this subscription entitles its organization to trade.
     *
     * Three ways to be current, and a subscription needs only one:
     *
     * 1. **Inside its trial.** Early access, written down as a date. Holds
     *    whatever the status says, because a merchant we told was free is free.
     * 2. **Verified, and inside its paid period.** A null period end means
     *    nothing has ever billed this org — there is no billing cycle yet — so
     *    `active` on its own is enough.
     * 3. **Past due but inside grace.**
     *
     * `rejected` is the one status that is never current: an operator looked at
     * the payment and said no.
     *
     * `pending_verification` is current on purpose. It is the status every
     * signup gets and only a human moves, so treating it as unpaid would make
     * our own backlog into the merchant's problem. When verification stops
     * being manual, this is the line to revisit.
     */
    public function isCurrent(): bool
    {
        if ($this->status === self::STATUS_REJECTED) {
            return false;
        }

        if ($this->trial_ends_at !== null && $this->trial_ends_at->isFuture()) {
            return true;
        }

        if ($this->status === self::STATUS_PENDING) {
            return true;
        }

        $periodEnd = $this->current_period_ends_at;

        if ($this->status === self::STATUS_ACTIVE) {
            return $periodEnd === null || $periodEnd->isFuture();
        }

        if ($this->status === self::STATUS_PAST_DUE) {
            $graceFrom = $periodEnd ?? $this->updated_at ?? $this->created_at;

            return $graceFrom !== null
                && $graceFrom->copy()->addDays((int) config('billing.grace_days'))->isFuture();
        }

        return false;
    }
}
