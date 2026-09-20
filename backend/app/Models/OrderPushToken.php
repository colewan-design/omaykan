<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Concerns\HasUuids;
use Illuminate\Database\Eloquent\Model;

/** A phone's Firebase token, registered against one order it is tracking. */
class OrderPushToken extends Model
{
    use HasUuids;

    protected $fillable = ['order_id', 'token'];

    public function order()
    {
        return $this->belongsTo(Order::class);
    }
}
