<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Concerns\HasUuids;
use Illuminate\Database\Eloquent\Factories\HasFactory;
use Illuminate\Database\Eloquent\Model;

class SyncEvent extends Model
{
    use HasFactory, HasUuids;

    public $timestamps = false;

    protected $fillable = [
        'id',
        'organization_id',
        'store_id',
        'device_id',
        'user_id',
        'entity_type',
        'entity_id',
        'operation',
        'payload',
        'idempotency_key',
        'received_at',
        'applied_at',
        'failed_at',
        'error_message',
    ];

    protected function casts(): array
    {
        return [
            'payload' => 'array',
            'received_at' => 'datetime',
            'applied_at' => 'datetime',
            'failed_at' => 'datetime',
        ];
    }
}
