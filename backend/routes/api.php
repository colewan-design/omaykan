<?php

use Illuminate\Http\Request;
use Illuminate\Support\Facades\Broadcast;
use Illuminate\Support\Facades\Route;
use App\Http\Controllers\Api\AppReleaseController;
use App\Http\Controllers\Api\CustomerAccountController;
use App\Http\Controllers\Api\CustomerAddressController;
use App\Http\Controllers\Api\CustomerAuthController;
use App\Http\Controllers\Api\CustomerConversationController;
use App\Http\Controllers\Api\CustomerOrderController;
use App\Http\Controllers\Api\CustomerPaymentMethodController;
use App\Http\Controllers\Api\FoundingSellerController;
use App\Http\Controllers\Api\LoyaltyController;
use App\Http\Controllers\Api\OnlineOrderController;
use App\Http\Controllers\Api\PayMongoWebhookController;
use App\Http\Controllers\Api\PlatformAdminAuthController;
use App\Http\Controllers\Api\ProductImageController;
use App\Http\Controllers\Api\PromoCodeController;
use App\Http\Controllers\Api\PlatformAdminController;
use App\Http\Controllers\Api\PlatformAdminInboxController;
use App\Http\Controllers\Api\PlatformAnalyticsController;
use App\Http\Controllers\Api\PlatformCustomerController;
use App\Http\Controllers\Api\PlatformOrderController;
use App\Http\Controllers\Api\PlatformOverviewController;
use App\Http\Controllers\Api\PlatformProductController;
use App\Http\Controllers\Api\PlatformReportController;
use App\Http\Controllers\Api\PlatformSettingsController;
use App\Http\Controllers\Api\RiderAccountController;
use App\Http\Controllers\Api\RiderAuthController;
use App\Http\Controllers\Api\RiderDeliveryController;
use App\Http\Controllers\Api\RiderEarningsController;
use App\Http\Controllers\Api\RiderPositionController;
use App\Http\Controllers\Api\RiderReviewController;
use App\Http\Controllers\Api\SellerConversationController;
use App\Http\Controllers\Api\SellerOrderController;
use App\Http\Controllers\Api\SellerRiderController;
use App\Http\Controllers\Api\SellerSubscriptionController;
use App\Http\Controllers\Api\SignupController;
use App\Http\Controllers\Api\StaffRoleController;
use App\Http\Controllers\Api\StaffAuthController;
use App\Http\Controllers\Api\StaffUserController;
use App\Http\Controllers\Api\StoreDirectoryController;
use App\Http\Controllers\Api\RiderAvatarController;
use App\Http\Controllers\Api\RiderRatingController;
use App\Http\Controllers\Api\RiderSupportController;
use App\Http\Controllers\Api\StoreImageController;
use App\Http\Controllers\Api\StoreOrderingController;
use App\Http\Controllers\Api\StorefrontCatalogController;
use App\Http\Controllers\Api\ShiftController;
use App\Http\Controllers\Api\RegisterOrderController;
use App\Http\Controllers\Api\SyncController;

Route::get('/user', function (Request $request) {
    return $request->user();
})->middleware(['auth:sanctum', 'merchant.token']);

/*
 * Staff sign-in.
 *
 * Replaces `/device-sessions`, `/staff-sessions` and `/staff-register`, and
 * with them the shop-wide pairing code all three were keyed on. Merchant
 * clients now sign a *person* in — see StaffAuthController for why.
 *
 * Throttled like the customer doors: sign-in is a password oracle, and the
 * Google one is not but shares a door with it.
 */
Route::post('/staff/sign-in', [StaffAuthController::class, 'signIn'])
    ->middleware('throttle:10,1');
Route::post('/staff/auth/google', [StaffAuthController::class, 'google'])
    ->middleware('throttle:10,1');

// Storefront. Unauthenticated by nature — a customer placing an order has no
// account. Replaces api/create-online-order.ts. Rate-limited because it is
// public and it writes: without a throttle it is a free inventory-drain.
Route::post('/online-orders', [OnlineOrderController::class, 'store'])
    ->middleware('throttle:20,1');

// What a basket would cost — subtotal, promo code, VAT, delivery — before it is
// ordered. Same arithmetic as checkout. Read-only, so throttled like the catalog.
Route::post('/online-orders/quote', [OnlineOrderController::class, 'quote'])
    ->middleware('throttle:60,1');

// Order tracking. The UUID in the path is the capability — see the controller.
Route::get('/online-orders/{order}', [OnlineOrderController::class, 'show'])
    ->middleware('throttle:60,1');

// A phone asking to be told when a rider takes this order. Same capability as
// tracking: whoever holds the UUID can already watch the order.
Route::post('/online-orders/{order}/push-token', [OnlineOrderController::class, 'registerPushToken'])
    ->middleware('throttle:20,1');

// The storefront's product list. Replaces the storefront reading Firestore
// directly, which is what forced Firebase credentials into the client.
Route::get('/storefront/catalog', [StorefrontCatalogController::class, 'show'])
    ->middleware('throttle:60,1');

// The shop list the landing page browses. Public and read-only, and it
// publishes nothing a shop does not already put on its own storefront, so it
// is throttled like the catalog rather than like the code lookup above.
Route::get('/stores', [StoreDirectoryController::class, 'index'])
    ->middleware('throttle:60,1');

// The shop's own photo, as its owner uploaded it in Settings. Public because
// it is the picture that shop already puts on its storefront, and throttled
// more loosely than the listing because a page of shop cards is a page of
// these — one request each, all at once.
Route::get('/stores/{store}/image', [StoreImageController::class, 'show'])
    ->middleware('throttle:240,1');

// Product photos. Public for the same reason, and throttled more loosely
// still: a menu page is a dozen of these at once. The file name is pinned to
// `{uuid}.{ext}` here, so the controller never sees a path.
Route::get('/product-images/{file}', [ProductImageController::class, 'show'])
    ->where('file', ProductImageController::FILE_PATTERN)
    ->middleware('throttle:600,1');

/*
 * A rider's photograph.
 *
 * Public in the same sense the shop photo above is: no token, but the URL
 * carries an unguessable UUID and is only ever handed out inside an order
 * payload that the shop and the customer on that delivery already receive.
 * See RiderAvatarController::show.
 */
Route::get('/riders/{rider}/avatar', [RiderAvatarController::class, 'show'])
    ->name('riders.avatar');

// The update check for sideloaded apps. Public because the Android app asks it
// on launch, before a shopper has done anything at all. A 404 means "nothing
// published", which is a real answer and not an error — see the controller.
Route::get('/app-releases/{slug}', [AppReleaseController::class, 'show'])
    ->middleware('throttle:60,1');

// Store discovery by typed code is gone with the pairing code it read. A
// shopper reaches a shop through the directory (`GET /stores`) and its
// org-slug/branch-code URL; staff reach theirs through `POST /staff/sign-in`,
// which lists the shops that account can act for.

// Public self-serve signup. Throttled hard: it creates an organization, a
// store and a user account on every successful call.
/*
 * PayMongo's webhook. Public and unauthenticated by necessity — PayMongo is
 * not signed in — so the signature is the only thing standing in front of it.
 * See PayMongoWebhookController, which refuses anything it cannot verify.
 *
 * Throttled generously rather than tightly: retries are how PayMongo recovers
 * from our downtime, and a limit low enough to bite during a retry storm
 * would turn a blip into lost payments.
 */
Route::post('/webhooks/paymongo', [PayMongoWebhookController::class, 'handle'])
    ->middleware('throttle:120,1');

Route::post('/signup', [SignupController::class, 'store'])
    ->middleware('throttle:5,1');

/*
 * The founding-seller campaign page (`/seller/founding`).
 *
 * Public and unauthenticated, because an applicant has no account yet — that
 * is the whole point of the page. Neither route creates a tenant: `store`
 * writes one `seller_applications` row for an operator to read, which is why
 * it can sit on a far looser throttle than `/signup` above.
 *
 * `status` is read on every page load, so it is throttled for a browser rather
 * than for a form: a visitor opening the page twice and a crawler walking it
 * must not trip a limit on what is effectively a public counter.
 */
Route::get('/founding-sellers/status', [FoundingSellerController::class, 'status'])
    ->middleware('throttle:60,1');

Route::post('/founding-sellers/apply', [FoundingSellerController::class, 'store'])
    ->middleware('throttle:10,1');

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
// Also creates nothing: it signs in a row `platform-admin:create` already made.
// Kept on the same tight throttle as the password door — this is the one
// identity that acts across every tenant.
Route::post('/platform-admin/auth/google', [PlatformAdminAuthController::class, 'google'])
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
     * The marketplace looked at as a whole: what it took, what is moving, what
     * is listed, and what it calls itself.
     *
     * All read-only but the last. An order belongs to the shop that took it
     * and a product to the shop that listed it, so this portal shows both and
     * changes neither — see PlatformOrderController for the argument. Settings
     * is writable because the marketplace is the one record here that has no
     * other owner.
     */
    Route::get('/platform-admin/overview', PlatformOverviewController::class);
    Route::get('/platform-admin/orders', [PlatformOrderController::class, 'index']);
    Route::get('/platform-admin/products', [PlatformProductController::class, 'index']);
    Route::get('/platform-admin/analytics', PlatformAnalyticsController::class);
    Route::get('/platform-admin/reports', PlatformReportController::class);
    Route::get('/platform-admin/settings', [PlatformSettingsController::class, 'show']);
    Route::put('/platform-admin/settings', [PlatformSettingsController::class, 'update']);

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
/*
 * Sign in with Google, from the storefront button and from the Android app.
 * Both post one field, `credential`: a Google ID token.
 *
 * Throttled like login rather than like register, even though it can create an
 * account. It is not a password oracle and it is not a way to send mail at
 * somebody, and the thing an attacker would need to abuse it — a validly signed
 * token addressed to our client id — is the one thing they cannot forge.
 */
Route::post('/customer/auth/google', [CustomerAuthController::class, 'google'])
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

    // One per delivered order, enforced by a unique index as well as by the
    // controller. See RiderRatingController for the four conditions.
    Route::post('/orders/{order}/rider-rating', [RiderRatingController::class, 'store']);

    /*
     * Messages with shops. One conversation per shop — see
     * CustomerConversationController. The two writes are throttled because
     * each one lands in a shop's inbox; the reads are what the portal polls.
     */
    Route::get('/conversations', [CustomerConversationController::class, 'index']);
    Route::get('/conversations/unread', [CustomerConversationController::class, 'unread']);
    Route::post('/conversations', [CustomerConversationController::class, 'start'])
        ->middleware('throttle:20,1');
    Route::get('/conversations/{conversation}', [CustomerConversationController::class, 'show'])
        ->whereUuid('conversation');
    Route::post('/conversations/{conversation}/messages', [CustomerConversationController::class, 'reply'])
        ->whereUuid('conversation')
        ->middleware('throttle:30,1');
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
// Signs in an existing rider and never registers one: approval hangs on a
// licence and a plate that Google knows nothing about. Throttled with the
// password door it shares a card with.
Route::post('/rider/auth/google', [RiderAuthController::class, 'google'])
    ->middleware('throttle:10,1');

/*
 * The way back in.
 *
 * Throttled like the customer pair and for the same two reasons: forgot-password
 * sends mail on request, and reset is a token oracle. Both answer the same thing
 * whether or not the address is a rider's — the platform's riders are a small,
 * knowable set, and an endpoint that distinguished them would be a way to
 * enumerate it.
 */
Route::post('/rider/forgot-password', [RiderAuthController::class, 'forgotPassword'])
    ->middleware('throttle:5,1');
Route::post('/rider/reset-password', [RiderAuthController::class, 'resetPassword'])
    ->middleware('throttle:5,1');

Route::middleware('auth:rider')->prefix('rider')->group(function () {
    // Outside the approval gate on purpose: a pending or rejected rider needs
    // to be able to read their own status and sign out.
    Route::get('/me', [RiderAuthController::class, 'me']);
    Route::post('/logout', [RiderAuthController::class, 'logout']);

    // Outside the gate with them, and deliberately: a rider waiting on review
    // is the one most likely to be fixing a mistyped phone number, and a
    // suspended rider must still be able to change a password they think
    // somebody else has. Neither can see a job either way.
    Route::patch('/me', [RiderAccountController::class, 'update']);
    Route::patch('/password', [RiderAccountController::class, 'updatePassword']);

    // Outside the gate with them: a rider waiting on review filling in their
    // profile is the normal case, and a photo is shown to nobody until they
    // are carrying an order, which the gate itself prevents.
    Route::post('/avatar', [RiderAvatarController::class, 'update']);
    Route::delete('/avatar', [RiderAvatarController::class, 'destroy']);

    // Outside the gate deliberately: a rejected rider who does not understand
    // why is the person most in need of somebody to ask.
    Route::get('/support', [RiderSupportController::class, 'show']);

    Route::middleware('rider.approved')->group(function () {
        Route::get('/board', [RiderDeliveryController::class, 'board']);
        Route::get('/deliveries', [RiderDeliveryController::class, 'mine']);
        Route::post('/deliveries/{order}/accept', [RiderDeliveryController::class, 'accept']);
        Route::post('/deliveries/{order}/stage', [RiderDeliveryController::class, 'advance']);
        Route::post('/deliveries/{order}/release', [RiderDeliveryController::class, 'release']);

        // Behind the gate: earnings are a fact about work, and an account that
        // has never been allowed to work has none to report.
        Route::get('/earnings', [RiderEarningsController::class, 'summary']);

        // Behind the gate for the same reason earnings are: a score is a fact
        // about work, and an account that has never been allowed to work has
        // none.
        Route::get('/ratings', [RiderRatingController::class, 'index']);

        // Where the rider is. Throttled well above the app's ten-second
        // cadence so a brief burst after a tunnel does not lock a rider out of
        // reporting, and well below what a runaway loop could cost.
        Route::post('/position', [RiderPositionController::class, 'store'])
            ->middleware('throttle:60,1');
        Route::delete('/position', [RiderPositionController::class, 'destroy']);
    });
});

// Rider review moved up into the `auth:platform` group with the rest of the
// operator tools when the shared secret was replaced by operator accounts.

// `merchant.token` alongside auth:sanctum: that guard has no configured
// provider, so Sanctum accepts any tokenable — including a shopper's portal
// token. The middleware states, once for the whole group, that everything in
// here is for staff and tills. See EnsureMerchantToken.
Route::middleware(['auth:sanctum', 'merchant.token'])->group(function () {
    /*
     * The rest of sign-in. These three take the unscoped token `/staff/sign-in`
     * returns, which reaches nothing else: everything below needs a token that
     * names a store, and `session-store` is what mints one.
     */
    Route::get('/staff/stores', [StaffAuthController::class, 'stores']);
    Route::post('/staff/session-store', [StaffAuthController::class, 'selectStore']);
    Route::post('/staff/sign-out', [StaffAuthController::class, 'signOut']);

    Route::get('/sync/bootstrap', [SyncController::class, 'bootstrap']);
    Route::post('/sync/push', [SyncController::class, 'push']);
    Route::get('/sync/pull', [SyncController::class, 'pull']);
    // A sale at the till, recorded before the cashier finishes it — or
    // refused, with the reason. See RegisterOrderController.
    Route::post('/register/orders', [RegisterOrderController::class, 'store']);
    Route::post('/register/orders/{order}/void', [RegisterOrderController::class, 'void'])
        ->whereUuid('order');
    Route::get('/shifts/current', [ShiftController::class, 'current']);
    Route::get('/shifts/history', [ShiftController::class, 'history']);
    Route::post('/shifts/open', [ShiftController::class, 'open']);
    Route::post('/shifts/current/movements', [ShiftController::class, 'addMovement']);
    Route::post('/shifts/current/close', [ShiftController::class, 'close']);
    // The seller's side of storefront orders. Scoped to the calling device's
    // store, like the sync endpoints above.
    Route::get('/seller/online-orders', [SellerOrderController::class, 'index']);
    Route::post('/seller/online-orders/{order}/rider', [SellerOrderController::class, 'assignRider']);
    Route::delete('/seller/online-orders/{order}/rider', [SellerOrderController::class, 'unassignRider']);
    // The shop's own riders. Not a directory of the platform's — see
    // SellerRiderController for why there deliberately isn't one.
    Route::get('/seller/riders', [SellerRiderController::class, 'index']);
    Route::post('/seller/riders', [SellerRiderController::class, 'store']);
    Route::patch('/seller/riders/{savedRider}', [SellerRiderController::class, 'update']);
    Route::delete('/seller/riders/{savedRider}', [SellerRiderController::class, 'destroy']);
    Route::post('/seller/online-orders/{order}/delivery-stage', [SellerOrderController::class, 'updateDeliveryStage']);
    Route::post('/seller/online-orders/{order}/status', [SellerOrderController::class, 'updateStatus']);
    Route::post('/seller/online-orders/{order}/settle-payment', [SellerOrderController::class, 'settlePayment']);
    /*
     * The shop's subscription, and the manual transfers it has told us about.
     *
     * Note what these are *not* behind: every other write in this group is
     * refused for an unpaid tenant, but the screen where a merchant pays us
     * cannot be one of them. See SellerSubscriptionController.
     *
     * Throttled because the submission is a free-text claim, not a charge —
     * there is nothing downstream to rate-limit it for us.
     */
    Route::get('/seller/subscription', [SellerSubscriptionController::class, 'show']);
    Route::post('/seller/subscription/payments', [SellerSubscriptionController::class, 'storePayment'])
        ->middleware('throttle:10,1');

    // Pay now, through PayMongo. Opens a hosted GCash checkout and returns
    // the URL; `settle` is the merchant coming back from it. Both are shut
    // unless the install has PayMongo keys.
    Route::post('/seller/subscription/checkout', [SellerSubscriptionController::class, 'startGatewayPayment']);
    Route::post('/seller/subscription/checkout/settle', [SellerSubscriptionController::class, 'settleGatewayPayment']);

    // The till publishing its own shop's photo. Scoped to the calling
    // device's store, like the sync and seller-order endpoints above.
    Route::put('/seller/store-image', [StoreImageController::class, 'update']);
    // The shop's own "not taking orders right now", with an optional resume
    // time. Any role with the Orders page — see StoreOrderingController.
    // A product photo, uploaded before the product event that names it. The
    // till does not need this — sync pulls inline photos out by itself — but
    // the seller app is always online when a photo is picked.
    Route::post('/seller/product-images', [ProductImageController::class, 'store'])
        ->middleware('throttle:60,1');
    // Promo and voucher codes: the shop's list, and the till's check of one
    // typed at the counter. See PromoCodeController.
    Route::get('/seller/promo-codes', [PromoCodeController::class, 'index']);
    Route::post('/seller/promo-codes', [PromoCodeController::class, 'store']);
    Route::patch('/seller/promo-codes/{promoCode}', [PromoCodeController::class, 'update']);
    Route::delete('/seller/promo-codes/{promoCode}', [PromoCodeController::class, 'destroy']);
    Route::post('/seller/promo-codes/check', [PromoCodeController::class, 'check'])
        ->middleware('throttle:60,1');
    // Points. Reading is for anyone at the register; changing the rules or
    // correcting a balance needs the Customers page. See LoyaltyController.
    Route::get('/seller/loyalty', [LoyaltyController::class, 'show']);
    Route::put('/seller/loyalty', [LoyaltyController::class, 'update']);
    Route::get('/seller/loyalty/balances', [LoyaltyController::class, 'balances']);
    Route::post('/seller/loyalty/check', [LoyaltyController::class, 'check'])
        ->middleware('throttle:60,1');
    Route::get('/seller/customers/{posCustomer}/loyalty', [LoyaltyController::class, 'customer']);
    Route::post('/seller/customers/{posCustomer}/loyalty/adjust', [LoyaltyController::class, 'adjust']);
    Route::get('/seller/ordering', [StoreOrderingController::class, 'show']);
    Route::put('/seller/ordering', [StoreOrderingController::class, 'update']);
    // Customer messages, answered by whoever is signed in to the shop. A shop
    // replies but never starts one — see SellerConversationController.
    Route::get('/seller/conversations', [SellerConversationController::class, 'index']);
    Route::get('/seller/conversations/unread', [SellerConversationController::class, 'unread']);
    Route::get('/seller/conversations/{conversation}', [SellerConversationController::class, 'show'])
        ->whereUuid('conversation');
    Route::post('/seller/conversations/{conversation}/messages', [SellerConversationController::class, 'reply'])
        ->whereUuid('conversation');
    Route::get('/staff-roles', [StaffRoleController::class, 'index']);
    Route::put('/staff-roles', [StaffRoleController::class, 'sync']);
    Route::get('/staff-users', [StaffUserController::class, 'index']);
    // Adding a member of staff. This is where `/staff-register` went: that was
    // public because the shop's code was the proof, and with the code gone the
    // proof is an admin who is already signed in to the shop.
    Route::post('/staff-users', [StaffUserController::class, 'store']);
    Route::patch('/staff-users/{user}/role', [StaffUserController::class, 'updateRole']);
});

// Private broadcast-channel authorization for the staff app's Reverb client.
// The staff app authenticates with a Sanctum bearer token, not a web session,
// so it can't use the default web-guarded /broadcasting/auth route. Sanctum
// resolves the staff user here; the channel callbacks in routes/channels.php
// then authorize by store membership.
Route::post('/broadcasting/auth', function (Request $request) {
    return Broadcast::auth($request);
})->middleware('auth:sanctum');
