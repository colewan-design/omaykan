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
use App\Http\Controllers\Api\Platform;
use App\Http\Controllers\Api\RiderAuthController;
use App\Http\Controllers\Api\RiderDeliveryController;
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

// Placing an order now needs an account. Browsing and filling a cart stay
// completely open — the gate is at checkout and nowhere earlier.
//
// `auth:customer` rather than a check inside the controller, so there is no
// path to an order without one. A token is only ever minted by login or by a
// completed password reset, and login refuses an unverified address, so
// holding one already implies a verified email; the controller re-checks
// anyway, because "the only way to get a token implies X" is the kind of
// invariant that quietly stops being true.
//
// Still throttled: an authenticated endpoint that decrements stock is worth
// rate-limiting per account, not just per anonymous caller.
Route::post('/online-orders', [OnlineOrderController::class, 'store'])
    ->middleware(['auth:customer', 'throttle:20,1']);

// Store discovery by code. Public and enumerable by nature, so throttled
// harder than the order endpoint — a short code space is worth guessing at.
// Order tracking. The UUID in the path is the capability — see the controller.
Route::get('/online-orders/{order}', [OnlineOrderController::class, 'show'])
    ->middleware('throttle:60,1');

// The customer's own confirmation that they handed the cash over. Public by
// the same unguessable id as the tracking route above, and throttled like a
// write rather than a read.
Route::post('/online-orders/{order}/confirm-payment', [OnlineOrderController::class, 'confirmPayment'])
    ->middleware('throttle:20,1');

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
 * The super admin portal.
 *
 * A fourth guard, and the widest-reaching one: an operator acts across every
 * tenant on the platform. It replaces the shared secret that used to gate
 * /platform-admin and /rider-review — one credential standing in for every
 * operator, unrevokable per person, and unable to say who did anything.
 *
 * `auth:platform` resolves the platform_admins table and nothing else, so a
 * staff, shopper or rider token cannot reach any of this, and an operator
 * token cannot reach a seller route. `platform.active` is paired with it
 * everywhere so a revoked account's live token stops working on its next
 * request. See config/auth.php.
 *
 * Login is throttled hard: it is public, and the accounts behind it are the
 * most valuable on the platform.
 */
Route::prefix('platform')->group(function () {
    Route::post('/login', [Platform\AuthController::class, 'login'])
        ->middleware('throttle:10,1');

    Route::middleware(['auth:platform', 'platform.active'])->group(function () {
        Route::post('/logout', [Platform\AuthController::class, 'logout']);
        Route::get('/me', [Platform\AuthController::class, 'me']);

        Route::get('/overview', [Platform\OverviewController::class, 'show']);

        Route::get('/organizations', [Platform\OrganizationController::class, 'index']);
        Route::get('/organizations/{slug}', [Platform\OrganizationController::class, 'show']);
        Route::post('/organizations/{slug}/subscription', [Platform\OrganizationController::class, 'setSubscriptionStatus']);
        Route::post('/organizations/{slug}/suspension', [Platform\OrganizationController::class, 'setSuspended']);
        Route::post('/organizations/{slug}/owners/{user}/password-reset', [Platform\OrganizationController::class, 'resetOwnerPassword']);
        Route::post('/organizations/{slug}/owners/{user}/status', [Platform\OrganizationController::class, 'setOwnerDisabled']);
        // Owner-only, and the one irreversible action in the portal.
        Route::delete('/organizations/{slug}', [Platform\OrganizationController::class, 'destroy']);

        // The only way to see a rider's licence photo: the images live on the
        // private disk and have no URL of their own.
        Route::get('/riders', [Platform\RiderReviewController::class, 'index']);
        Route::post('/riders/{rider}/decision', [Platform\RiderReviewController::class, 'decide']);
        Route::get('/riders/{rider}/document/{document}', [Platform\RiderReviewController::class, 'document']);

        Route::get('/audit-logs', [Platform\AuditLogController::class, 'index']);

        // Owner-only.
        Route::get('/admins', [Platform\AdminController::class, 'index']);
        Route::post('/admins', [Platform\AdminController::class, 'store']);
        Route::post('/admins/{admin}/status', [Platform\AdminController::class, 'setStatus']);
    });
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
    // An admin adding staff from the till. Replaces api/staff-create.ts.
    Route::post('/staff-users', [StaffUserController::class, 'store']);
    Route::patch('/staff-users/{user}/role', [StaffUserController::class, 'updateRole']);
});
