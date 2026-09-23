<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Concerns\HasUuids;
use Illuminate\Database\Eloquent\Model;

/**
 * An organization's points rules. One per organization, off until the owner
 * turns it on. See the pos_customers migration.
 */
class LoyaltyProgram extends Model
{
    use HasUuids;

    protected $fillable = [
        'organization_id',
        'enabled',
        'spend_cents_per_point',
        'point_value_cents',
        'min_redeem_points',
        'expiry_months',
    ];

    protected function casts(): array
    {
        return [
            'enabled' => 'boolean',
            'spend_cents_per_point' => 'integer',
            'point_value_cents' => 'integer',
            'min_redeem_points' => 'integer',
            'expiry_months' => 'integer',
        ];
    }

    /** The organization's programme, or the defaults — switched off — if it has never saved one. */
    public static function for(string $organizationId): self
    {
        return self::query()->firstOrNew(['organization_id' => $organizationId], [
            'enabled' => false,
            'spend_cents_per_point' => 10000,
            'point_value_cents' => 100,
            'min_redeem_points' => 10,
            'expiry_months' => null,
        ]);
    }
}
