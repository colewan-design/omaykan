<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Concerns\HasUuids;
use Illuminate\Database\Eloquent\Factories\HasFactory;
use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\SoftDeletes;

class Product extends Model
{
    use HasFactory, HasUuids, SoftDeletes;

    protected $fillable = [
        'id',
        'organization_id',
        'category_id',
        'sku',
        'barcode',
        'name',
        'product_type',
        'tax_rate',
        'price_cents',
        'track_inventory',
        'is_active',
        'created_by_device_id',
        'business_modes',
        'compare_at_price_cents',
        'image_url',
        'photo_urls',
        'brand',
        'packaging_type',
        'unit_label',
        'description',
        'low_stock_threshold',
    ];

    protected function casts(): array
    {
        return [
            'tax_rate' => 'decimal:2',
            'track_inventory' => 'boolean',
            'is_active' => 'boolean',
            'business_modes' => 'array',
            'photo_urls' => 'array',
        ];
    }

    public function organization()
    {
        return $this->belongsTo(Organization::class);
    }

    public function category()
    {
        return $this->belongsTo(Category::class);
    }
}
