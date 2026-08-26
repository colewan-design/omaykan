<?php

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
