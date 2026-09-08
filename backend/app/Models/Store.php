<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Concerns\HasUuids;
use Illuminate\Database\Eloquent\Factories\HasFactory;
use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\SoftDeletes;

class Store extends Model
{
    use HasFactory, HasUuids, SoftDeletes;

    /**
     * The register layouts that can put something in a cart.
     *
     * A nail salon runs on this platform and has nothing to sell online, so it
     * is never listed in the shop directory and never accepts a storefront
     * order. This lived on StoreCodeController until store codes were retired,
     * and OnlineOrderController kept a second copy of it; both read this now,
     * because two lists of the same rule drift.
     */
    public const ONLINE_MODES = ['coffee-shop', 'grocery', 'restaurant'];

    protected $fillable = [
        'organization_id',
        'name',
        'code',
        'timezone',
        'currency_code',
        'status',
        'business_mode',
        'business_type_label',
        'address',
        'lat',
        'lng',
        'image_path',
    ];

    protected function casts(): array
    {
        return [
            'lat' => 'float',
            'lng' => 'float',
        ];
    }

    /** Both coordinates are needed before a delivery distance can be quoted. */
    public function hasPin(): bool
    {
        return $this->lat !== null && $this->lng !== null;
    }

    public function organization()
    {
        return $this->belongsTo(Organization::class);
    }

    /**
     * Terminals that paired with this shop before staff sign-in replaced
     * pairing. History: nothing creates a Device any more.
     */
    public function devices()
    {
        return $this->hasMany(Device::class);
    }

    public function overrides()
    {
        return $this->hasMany(ProductStoreOverride::class);
    }

    public function inventoryLevels()
    {
        return $this->hasMany(InventoryLevel::class);
    }
}
