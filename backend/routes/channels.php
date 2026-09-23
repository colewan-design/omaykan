<?php

use App\Models\Order;
use App\Models\Store;
use App\Models\StoreMembership;
use Illuminate\Support\Facades\Broadcast;

Broadcast::channel('App.Models.User.{id}', function ($user, $id) {
    return (int) $user->id === (int) $id;
});

/**
 * Whether this person may watch this store's live feed.
 *
 * Membership, and a tenant staff may still reach. The second half is what
 * StoreContextResolver asks on every API request; without it here, a suspended
 * shop's dashboard keeps streaming new orders into a tab that the rest of the
 * API has already locked — "suspended" with a visible exception on screen.
 *
 * An unpaid shop keeps its feed. Its staff can still read, and progressing the
 * orders already in flight is exactly what this channel is for.
 */
$staffMayWatch = function ($user, ?string $storeId): bool {
    if ($storeId === null) {
        return false;
    }

    $isMember = StoreMembership::query()
        ->where('store_id', $storeId)
        ->where('user_id', $user->id)
        ->exists();

    if (! $isMember) {
        return false;
    }

    $organization = Store::query()->with('organization.subscription')->find($storeId)?->organization;

    return $organization !== null && $organization->accessVerdict()->allowsStaffAccess();
};

/**
 * Everything happening at one store: new orders, status changes, and anything
 * else the registers and back office need to see live.
 */
Broadcast::channel('store.{storeId}', fn ($user, string $storeId) => $staffMayWatch($user, $storeId));

/**
 * A single order, for staff. The customer-facing side of the same order rides a
 * public channel keyed on the order UUID (see OrderStatusChanged) because
 * storefront customers have no account to authenticate with.
 */
Broadcast::channel('orders.{orderId}', fn ($user, string $orderId) => $staffMayWatch(
    $user,
    Order::query()->whereKey($orderId)->value('store_id'),
));
