<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Concerns\HasUuids;
use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\Relations\BelongsTo;

/**
 * One customer's score for one delivery.
 *
 * Immutable by convention: nothing in the application updates a rating once it
 * exists. The unique index on `order_id` is what enforces the important half —
 * a customer gets one say per delivery — and the absence of an update route is
 * what keeps that say from being rewritten after a rider has seen it.
 *
 * See the migration for why the rider is denormalised here rather than read
 * through the order.
 */
class RiderRating extends Model
{
    use HasUuids;

    protected $fillable = [
        'rider_id',
        'order_id',
        'customer_account_id',
        'score',
        'comment',
    ];

    protected function casts(): array
    {
        return [
            'score' => 'integer',
        ];
    }

    /** The lowest and highest a customer can give. */
    public const MIN_SCORE = 1;

    public const MAX_SCORE = 5;

    public function rider(): BelongsTo
    {
        return $this->belongsTo(Rider::class);
    }

    public function order(): BelongsTo
    {
        return $this->belongsTo(Order::class);
    }

    /**
     * What the rider sees of a rating left about them.
     *
     * The customer is **not** named. A rider seeing "3 stars — slow" attached
     * to a name and an address they delivered to an hour ago is the setup for
     * exactly the confrontation this feature should not create. The ticket
     * number is enough for the rider to place which delivery it was, which is
     * as much context as acting on the feedback needs.
     *
     * @return array<string, mixed>
     */
    public function toRiderArray(): array
    {
        return [
            'id' => $this->id,
            'score' => $this->score,
            'comment' => $this->comment,
            'ticketNumber' => $this->order?->ticket_number,
            'at' => $this->created_at?->toIso8601String(),
        ];
    }
}
