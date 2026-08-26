<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Concerns\HasUuids;
use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\Relations\BelongsTo;

/**
 * How a customer intends to settle up.
 *
 * A preference, not an instrument: both kinds are paid on arrival, so there is
 * nothing here that could be used to move money if the row leaked. `detail`
 * holds an e-wallet number the customer chose to save so they stop retyping
 * it, and nothing else.
 */
class CustomerPaymentMethod extends Model
{
    use HasUuids;

    public const KINDS = ['cash', 'ewallet'];

    protected $fillable = [
        'customer_account_id',
        'kind',
        'detail',
        'is_default',
    ];

    protected function casts(): array
    {
        return [
            'is_default' => 'boolean',
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
            'kind' => $this->kind,
            'detail' => $this->detail ?? '',
            'isDefault' => $this->is_default,
        ];
    }
}
