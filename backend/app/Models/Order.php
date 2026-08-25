<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Concerns\HasUuids;
use Illuminate\Database\Eloquent\Factories\HasFactory;
use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\SoftDeletes;

class Order extends Model
{
    use HasFactory, HasUuids, SoftDeletes;

    protected $fillable = [
        'id',
        'organization_id',
        'store_id',
        'device_id',
        'user_id',
        'ticket_number',
        'order_status',
        'order_type',
        'payment_status',
        'subtotal_cents',
        'tax_cents',
        'total_cents',
        'business_date',
        'completed_at',
        'channel',
        'business_mode',
        'table_number',
        'payment_method',
        'fulfillment_method',
        'delivery_address',
        'delivery_lat',
        'delivery_lng',
        'delivery_distance_km',
        'delivery_fee_cents',
        'delivery_stage',
        'rider_name',
        'rider_phone',
        'guest_contact',
    ];

    protected function casts(): array
    {
        return [
            'business_date' => 'date',
            'completed_at' => 'datetime',
            'guest_contact' => 'array',
            'delivery_lat' => 'float',
            'delivery_lng' => 'float',
            'delivery_distance_km' => 'float',
        ];
    }

    /** Placed by a customer on the storefront rather than rung up on a till. */
    public function isOnline(): bool
    {
        return $this->channel === 'online';
    }

    public function scopeOnline($query)
    {
        return $query->where('channel', 'online');
    }

    public function items()
    {
        return $this->hasMany(OrderItem::class);
    }

    public function payments()
    {
        return $this->hasMany(Payment::class);
    }

    public function device()
    {
        return $this->belongsTo(Device::class);
    }
}
