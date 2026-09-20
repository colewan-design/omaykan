<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Concerns\HasUuids;
use Illuminate\Database\Eloquent\Factories\HasFactory;
use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\SoftDeletes;

class PosRole extends Model
{
    use HasFactory, HasUuids, SoftDeletes;

    protected $table = 'roles';

    protected $fillable = [
        'organization_id',
        'role_key',
        'name',
        'permissions',
        'max_discount_percent',
    ];

    protected function casts(): array
    {
        return [
            'permissions' => 'array',
        ];
    }

    public function organization()
    {
        return $this->belongsTo(Organization::class);
    }
}
