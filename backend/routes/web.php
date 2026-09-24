<?php

use App\Http\Controllers\AssetLinksController;
use App\Http\Controllers\Auth\EmailVerificationController;
use App\Http\Controllers\ShopShellController;
use Illuminate\Foundation\Http\Middleware\ValidateCsrfToken;
use Illuminate\Session\Middleware\StartSession;
use Illuminate\Support\Facades\Route;
use Illuminate\View\Middleware\ShareErrorsFromSession;

/*
 * A shop's own page, `<slug>.omaykan.com`, served with that shop's name and
 * photo in the head so a pasted link draws a real preview. Any other host gets
 * the framework's welcome page, which is what this route was before — nginx
 * does not route the main site's `/` here.
 *
 * **Without the session.** This is a public page with no form and no signed-in
 * state, and SESSION_DRIVER is `database`: left in the web group it would
 * insert a row for every visitor and for every crawler that ever looks at a
 * shop, and put a Set-Cookie on a response meant to be cached. The three
 * excluded here are the ones that touch the session — ShareErrorsFromSession
 * fails outright without it, and CSRF has nothing to check on a GET.
 */
Route::get('/', ShopShellController::class)
    ->withoutMiddleware([StartSession::class, ShareErrorsFromSession::class, ValidateCsrfToken::class]);

Route::get('/email/verify/customer/{id}/{hash}', [EmailVerificationController::class, 'verifyCustomer'])
    ->middleware(['signed', 'throttle:6,1'])
    ->name('verification.customer.verify');

Route::get('/email/verify/seller/{id}/{hash}', [EmailVerificationController::class, 'verifySeller'])
    ->middleware(['signed', 'throttle:6,1'])
    ->name('verification.seller.verify');

/*
 * Digital Asset Links: what lets the password-reset email open the Android app
 * directly instead of asking which app should handle the link.
 *
 * A web route rather than an API one because Android's verifier fetches this
 * exact path on the bare domain, follows no redirect, and does not know or care
 * that the rest of this application lives under /api.
 */
Route::get('/.well-known/assetlinks.json', [AssetLinksController::class, 'show']);
