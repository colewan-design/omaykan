<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Concerns\HasUuids;
use Illuminate\Database\Eloquent\Factories\HasFactory;
use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\SoftDeletes;
use Illuminate\Support\Facades\Hash;

class Store extends Model
{
    use HasFactory, HasUuids, SoftDeletes;

    protected $fillable = [
        'organization_id',
        'name',
        'code',
        'timezone',
        'currency_code',
        'status',
        'pairing_code_hash',
        'public_store_code',
        'business_mode',
        'address',
        'lat',
        'lng',
    ];

    protected $hidden = [
        'pairing_code_hash',
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

    /**
     * Codes are typed by hand off a receipt or a screen, so they are compared
     * case-insensitively and with surrounding whitespace ignored.
     */
    public static function normalizePairingCode(string $code): string
    {
        return strtoupper(trim($code));
    }

    /**
     * Set the public identifier and the pairing secret from one value, which
     * is how signup issues them and how every store behaves today.
     *
     * They are stored separately on purpose — see the migration. To rotate the
     * pairing secret alone, call rotatePairingCode() and leave the public code
     * customers already have untouched.
     */
    public function setPairingCode(string $code): void
    {
        $this->public_store_code = self::normalizePairingCode($code);
        $this->rotatePairingCode($code);
    }

    /** Replace only the secret a till pairs with. */
    public function rotatePairingCode(string $code): void
    {
        $this->pairing_code_hash = Hash::make(self::normalizePairingCode($code));
    }

    public function scopeWithPairingCode($query, string $code)
    {
        return $query->where('public_store_code', self::normalizePairingCode($code));
    }

    public function organization()
    {
        return $this->belongsTo(Organization::class);
    }

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
