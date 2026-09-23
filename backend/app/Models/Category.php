<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Concerns\HasUuids;
use Illuminate\Database\Eloquent\Factories\HasFactory;
use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\SoftDeletes;

class Category extends Model
{
    use HasFactory, HasUuids, SoftDeletes;

    protected $fillable = [
        'id',
        'organization_id',
        'name',
        'sort_order',
        'created_by_device_id',
    ];

    public function organization()
    {
        return $this->belongsTo(Organization::class);
    }

    /**
     * Added for the operator portal's catalog tabs, which count products per
     * category. The storefront reaches the other way (a product names its
     * category), so nothing needed this until something wanted the totals.
     */
    public function products()
    {
        return $this->hasMany(Product::class);
    }
}
