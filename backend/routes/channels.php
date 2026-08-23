<?php

use App\Models\Order;
use App\Models\StoreMembership;
use Illuminate\Support\Facades\Broadcast;

Broadcast::channel('App.Models.User.{id}', function ($user, $id) {
    return (int) $user->id === (int) $id;
});

/**
 * Everything happening at one store: new orders, status changes, and anything
 * else the registers and back office need to see live. Membership of the store
 * is the whole authorization rule.
 */
Broadcast::channel('store.{storeId}', function ($user, string $storeId) {
    return StoreMembership::query()
        ->where('store_id', $storeId)
        ->where('user_id', $user->id)
        ->exists();
});

/**
 * A single order, for staff. The customer-facing side of the same order rides a
 * public channel keyed on the order UUID (see OrderStatusChanged) because
 * storefront customers have no account to authenticate with.
 */
Broadcast::channel('orders.{orderId}', function ($user, string $orderId) {
    $storeId = Order::query()->whereKey($orderId)->value('store_id');

    if ($storeId === null) {
        return false;
    }

    return StoreMembership::query()
        ->where('store_id', $storeId)
        ->where('user_id', $user->id)
        ->exists();
});
