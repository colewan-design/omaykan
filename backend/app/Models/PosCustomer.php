<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Concerns\HasUuids;
use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\SoftDeletes;

/**
 * A named customer at the counter, shared by every till in the organization.
 * Not a storefront CustomerAccount; see the pos_customers migration.
 */
class PosCustomer extends Model
{
    use HasUuids, SoftDeletes;

    protected $fillable = [
        'id',
        'organization_id',
        'name',
        'phone',
        'email',
        'notes',
        'loyalty_consent_at',
    ];

    protected function casts(): array
    {
        return [
            'loyalty_consent_at' => 'datetime',
        ];
    }

    public function loyaltyEntries()
    {
        return $this->hasMany(LoyaltyEntry::class);
    }

    public function orders()
    {
        return $this->hasMany(Order::class);
    }
}
