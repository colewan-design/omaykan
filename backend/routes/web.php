<?php

use App\Http\Controllers\AssetLinksController;
use App\Http\Controllers\Auth\EmailVerificationController;
use Illuminate\Support\Facades\Route;

Route::get('/', function () {
    return view('welcome');
});

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
