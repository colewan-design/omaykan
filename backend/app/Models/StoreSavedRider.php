<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Concerns\HasUuids;
use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\Relations\BelongsTo;

/**
 * A rider one shop keeps on file.
 *
 * See the migration for why this exists at all. The short version: a shop that
 * has its own rider should name them once, not once per order.
 *
 * The row is the shop's record, not the rider's. `name` and `phone` are what
 * *this shop* calls them and are never overwritten from the rider account —
 * a rider who changes their display name has not renamed themselves inside
 * somebody else's kitchen.
 */
class StoreSavedRider extends Model
{
    use HasUuids;

    protected $fillable = [
        'store_id',
        'rider_id',
        'name',
        'phone',
        'note',
    ];

    protected function casts(): array
    {
        return [
            'last_used_at' => 'datetime',
            'times_used' => 'integer',
        ];
    }

    public function store(): BelongsTo
    {
        return $this->belongsTo(Store::class);
    }

    /** Null for an off-platform rider — most of them, at the start. */
    public function rider(): BelongsTo
    {
        return $this->belongsTo(Rider::class);
    }

    /** Whether assigning this one reaches an app, or means picking up a phone. */
    public function isOnPlatform(): bool
    {
        return $this->rider_id !== null;
    }

    /** Called every time the shop actually sends work this rider's way. */
    public function recordUse(): void
    {
        $this->forceFill([
            'times_used' => $this->times_used + 1,
            'last_used_at' => now(),
        ])->save();
    }

    /**
     * What the seller's picker shows.
     *
     * `status` and `online` come from the linked account when there is one, so
     * the shop can see that their usual rider is suspended *before* they assign
     * an order to them, rather than after it fails to move for an hour.
     *
     * @return array<string, mixed>
     */
    public function toPickerArray(): array
    {
        $rider = $this->relationLoaded('rider') ? $this->getRelation('rider') : null;

        return [
            'id' => $this->id,
            'riderId' => $this->rider_id,
            'name' => $this->name,
            'phone' => $this->phone,
            'note' => $this->note,
            'onPlatform' => $this->isOnPlatform(),
            'timesUsed' => (int) $this->times_used,
            'lastUsedAt' => $this->last_used_at?->toIso8601String(),
            // Null for an off-platform rider: there is no account to have a
            // status, and the picker says "phone" rather than pretending.
            'status' => $rider?->status,
            'online' => $rider?->isReportingPosition() ?? false,
        ];
    }
}
