<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Concerns\HasUuids;
use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\Relations\BelongsTo;

/**
 * Somewhere a rider can be sent.
 *
 * Exactly one address per account carries `is_default`, which is the one
 * checkout opens on. Keeping that invariant is the controller's job — see
 * CustomerAddressController::makeSoleDefault.
 */
class CustomerAddress extends Model
{
    use HasUuids;

    protected $fillable = [
        'customer_account_id',
        'label',
        'line1',
        'barangay',
        'city',
        'notes',
        'lat',
        'lng',
        'is_default',
    ];

    protected function casts(): array
    {
        return [
            'is_default' => 'boolean',
            'lat' => 'float',
            'lng' => 'float',
        ];
    }

    public function customerAccount(): BelongsTo
    {
        return $this->belongsTo(CustomerAccount::class);
    }

    /** @return array<string, mixed> */
    public function toStorefrontArray(): array
    {
        return [
            'id' => $this->id,
            'label' => $this->label,
            'line1' => $this->line1,
            'barangay' => $this->barangay ?? '',
            'city' => $this->city,
            'notes' => $this->notes ?? '',
            'lat' => $this->lat,
            'lng' => $this->lng,
            'isDefault' => $this->is_default,
        ];
    }
}
