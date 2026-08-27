<?php

use Illuminate\Http\Request;
use Illuminate\Support\Facades\Route;
use App\Http\Controllers\Api\CustomerAccountController;
use App\Http\Controllers\Api\CustomerAddressController;
use App\Http\Controllers\Api\CustomerAuthController;
use App\Http\Controllers\Api\CustomerOrderController;
use App\Http\Controllers\Api\CustomerPaymentMethodController;
use App\Http\Controllers\Api\DeviceSessionController;
use App\Http\Controllers\Api\OnlineOrderController;
use App\Http\Controllers\Api\PlatformAdminAuthController;
use App\Http\Controllers\Api\PlatformAdminController;
use App\Http\Controllers\Api\PlatformAdminInboxController;
use App\Http\Controllers\Api\PlatformCustomerController;
use App\Http\Controllers\Api\RiderAuthController;
use App\Http\Controllers\Api\RiderDeliveryController;
use App\Http\Controllers\Api\RiderReviewController;
use App\Http\Controllers\Api\SellerOrderController;
use App\Http\Controllers\Api\SignupController;
use App\Http\Controllers\Api\StaffRoleController;
use App\Http\Controllers\Api\StaffSessionController;
use App\Http\Controllers\Api\StaffUserController;
use App\Http\Controllers\Api\StoreCodeController;
use App\Http\Controllers\Api\StorefrontCatalogController;
use App\Http\Controllers\Api\ShiftController;
use App\Http\Controllers\Api\SyncController;

Route::get('/user', function (Request $request) {
    return $request->user();
})->middleware(['auth:sanctum', 'merchant.token']);

Route::post('/device-sessions', [DeviceSessionController::class, 'store']);
Route::post('/staff-sessions', [StaffSessionController::class, 'store']);
Route::post('/staff-register', [StaffSessionController::class, 'register']);

// Storefront. Unauthenticated by nature — a customer placing an order has no
// account. Replaces api/create-online-order.ts. Rate-limited because it is
// public and it writes: without a throttle it is a free inventory-drain.
Route::post('/online-orders', [OnlineOrderController::class, 'store'])
    ->middleware('throttle:20,1');

// Store discovery by code. Public and enumerable by nature, so throttled
// harder than the order endpoint — a short code space is worth guessing at.
// Order tracking. The UUID in the path is the capability — see the controller.
Route::get('/online-orders/{order}', [OnlineOrderController::class, 'show'])
    ->middleware('throttle:60,1');

// The storefront's product list. Replaces the storefront reading Firestore
// directly, which is what forced Firebase credentials into the client.
Route::get('/storefront/catalog', [StorefrontCatalogController::class, 'show'])
    ->middleware('throttle:60,1');

Route::post('/store-codes/resolve', [StoreCodeController::class, 'resolve'])
    ->middleware('throttle:10,1');
Route::post('/store-codes/resolve-staff', [StoreCodeController::class, 'resolveForStaff'])
    ->middleware('throttle:10,1');

// Public self-serve signup. Throttled hard: it creates an organization, a
// store and a user account on every successful call.
Route::post('/signup', [SignupController::class, 'store'])
    ->middleware('throttle:5,1');

/*
 * The platform operator: the one identity that acts across every tenant.
 *
 * `auth:platform` resolves the platform_admins table and nothing else, so a
 * staff, customer or rider token can never reach these however it was minted —
 * and an operator token can never reach a tenant's own routes. See
 * config/auth.php. `platform.active` then rejects a token whose account has
 * since been disabled, on every request rather than only at sign-in.
 *
 * Sign-in is throttled to 5/min: it is the one door in front of cross-tenant
 * access, and there is no legitimate reason to hit it often. Accounts are
 * created from the console (`php artisan platform-admin:create`) — there is
 * deliberately no register or password-reset endpoint here.
 */
Route::post('/platform-admin/login', [PlatformAdminAuthController::class, 'login'])
    ->middleware('throttle:5,1');

Route::middleware(['auth:platform', 'platform.active', 'throttle:60,1'])->group(function () {
    Route::post('/platform-admin/logout', [PlatformAdminAuthController::class, 'logout']);
    Route::get('/platform-admin/me', [PlatformAdminAuthController::class, 'me']);

    Route::post('/platform-admin', [PlatformAdminController::class, 'handle']);
    Route::post('/platform-admin/inbox', [PlatformAdminInboxController::class, 'index']);
    Route::get('/platform-admin/inbox/{messageId}', [PlatformAdminInboxController::class, 'show']);
    Route::post('/platform-admin/inbox/{messageId}/reply', [PlatformAdminInboxController::class, 'reply']);

    /*
     * Shoppers, for support. Read-only: a store sees only its own orders and a
     * customer sees only themselves, so this is the one place a whole account
     * can be looked at when someone writes in about one.
     */
    Route::get('/platform-admin/customers', [PlatformCustomerController::class, 'index']);
    Route::get('/platform-admin/customers/{customer}', [PlatformCustomerController::class, 'show']);

    /*
     * Rider review. Same operator, same guard — and the only way to see a
     * rider's licence photo, since the images live on the private disk and
     * have no URL of their own.
     */
    Route::post('/rider-review', [RiderReviewController::class, 'index']);
    Route::post('/rider-review/{rider}/decision', [RiderReviewController::class, 'decide']);
    Route::post('/rider-review/{rider}/document/{document}', [RiderReviewController::class, 'document']);
});

/*
 * Customer accounts.
 *
 * A separate guard from everything below — `auth:customer` resolves the
 * customer_accounts table, `auth:sanctum` resolves staff. A shopper's token
 * cannot reach a seller route and a staff token cannot reach a portal route,
 * whatever abilities either was minted with. See config/auth.php.
 *
 * The public four are throttled hard: register creates an account, login and
 * reset are password oracles, and forgot-password sends mail on request.
 */
Route::post('/customer/register', [CustomerAuthController::class, 'register'])
    ->middleware('throttle:5,1');
Route::post('/customer/login', [CustomerAuthController::class, 'login'])
    ->middleware('throttle:10,1');
Route::post('/customer/forgot-password', [CustomerAuthController::class, 'forgotPassword'])
    ->middleware('throttle:5,1');
Route::post('/customer/reset-password', [CustomerAuthController::class, 'resetPassword'])
    ->middleware('throttle:5,1');

Route::middleware('auth:customer')->prefix('customer')->group(function () {
    Route::get('/me', [CustomerAuthController::class, 'me']);
    Route::post('/logout', [CustomerAuthController::class, 'logout']);

    Route::patch('/account', [CustomerAccountController::class, 'update']);
    Route::patch('/account/email', [CustomerAccountController::class, 'updateEmail']);
    Route::patch('/account/password', [CustomerAccountController::class, 'updatePassword']);

    Route::post('/addresses', [CustomerAddressController::class, 'store']);
    Route::patch('/addresses/{address}', [CustomerAddressController::class, 'update']);
    Route::delete('/addresses/{address}', [CustomerAddressController::class, 'destroy']);

    Route::post('/payment-methods', [CustomerPaymentMethodController::class, 'store']);
    Route::patch('/payment-methods/{paymentMethod}', [CustomerPaymentMethodController::class, 'update']);
    Route::delete('/payment-methods/{paymentMethod}', [CustomerPaymentMethodController::class, 'destroy']);

    Route::get('/orders', [CustomerOrderController::class, 'index']);
    Route::get('/orders/{order}', [CustomerOrderController::class, 'show']);
});

/*
 * Riders.
 *
 * A third guard alongside `sanctum` (staff and devices) and `customer`. A
 * rider works across every shop, so none of the tenant-scoped seller routes
 * below may ever resolve one — see config/auth.php.
 *
 * Register is throttled hardest of the three: it creates an account *and*
 * accepts two file uploads, so it is the most expensive public write on the
 * API. `rider.approved` gates everything past sign-in on a human having
 * looked at the licence.
 */
Route::post('/rider/register', [RiderAuthController::class, 'register'])
    ->middleware('throttle:4,1');
Route::post('/rider/login', [RiderAuthController::class, 'login'])
    ->middleware('throttle:10,1');

Route::middleware('auth:rider')->prefix('rider')->group(function () {
    // Outside the approval gate on purpose: a pending or rejected rider needs
    // to be able to read their own status and sign out.
    Route::get('/me', [RiderAuthController::class, 'me']);
    Route::post('/logout', [RiderAuthController::class, 'logout']);

    Route::middleware('rider.approved')->group(function () {
        Route::get('/board', [RiderDeliveryController::class, 'board']);
        Route::get('/deliveries', [RiderDeliveryController::class, 'mine']);
        Route::post('/deliveries/{order}/accept', [RiderDeliveryController::class, 'accept']);
        Route::post('/deliveries/{order}/stage', [RiderDeliveryController::class, 'advance']);
        Route::post('/deliveries/{order}/release', [RiderDeliveryController::class, 'release']);
    });
});

// Rider review moved up into the `auth:platform` group with the rest of the
// operator tools when the shared secret was replaced by operator accounts.

// `merchant.token` alongside auth:sanctum: that guard has no configured
// provider, so Sanctum accepts any tokenable — including a shopper's portal
// token. The middleware states, once for the whole group, that everything in
// here is for staff and tills. See EnsureMerchantToken.
Route::middleware(['auth:sanctum', 'merchant.token'])->group(function () {
    Route::get('/sync/bootstrap', [SyncController::class, 'bootstrap']);
    Route::post('/sync/push', [SyncController::class, 'push']);
    Route::get('/sync/pull', [SyncController::class, 'pull']);
    Route::get('/shifts/current', [ShiftController::class, 'current']);
    Route::get('/shifts/history', [ShiftController::class, 'history']);
    Route::post('/shifts/open', [ShiftController::class, 'open']);
    Route::post('/shifts/current/movements', [ShiftController::class, 'addMovement']);
    Route::post('/shifts/current/close', [ShiftController::class, 'close']);
    // The seller's side of storefront orders. Scoped to the calling device's
    // store, like the sync endpoints above.
    Route::get('/seller/online-orders', [SellerOrderController::class, 'index']);
    Route::post('/seller/online-orders/{order}/rider', [SellerOrderController::class, 'assignRider']);
    Route::post('/seller/online-orders/{order}/delivery-stage', [SellerOrderController::class, 'updateDeliveryStage']);
    Route::post('/seller/online-orders/{order}/status', [SellerOrderController::class, 'updateStatus']);
    Route::post('/seller/online-orders/{order}/settle-payment', [SellerOrderController::class, 'settlePayment']);
    Route::get('/staff-roles', [StaffRoleController::class, 'index']);
    Route::put('/staff-roles', [StaffRoleController::class, 'sync']);
    Route::get('/staff-users', [StaffUserController::class, 'index']);
    Route::patch('/staff-users/{user}/role', [StaffUserController::class, 'updateRole']);
});
