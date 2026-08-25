<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Concerns\HasUuids;
use Illuminate\Database\Eloquent\Factories\HasFactory;
use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\SoftDeletes;

class Organization extends Model
{
    use HasFactory, HasUuids, SoftDeletes;

    protected $fillable = [
        'id',
        'name',
        'slug',
        'status',
        'suspended',
    ];

    protected function casts(): array
    {
        return [
            'suspended' => 'boolean',
        ];
    }

    public function subscription()
    {
        return $this->hasOne(Subscription::class);
    }

    public function memberships()
    {
        return $this->hasMany(OrganizationMembership::class);
    }

    public function stores()
    {
        return $this->hasMany(Store::class);
    }

    public function devices()
    {
        return $this->hasMany(Device::class);
    }

    public function categories()
    {
        return $this->hasMany(Category::class);
    }

    public function products()
    {
        return $this->hasMany(Product::class);
    }
}
