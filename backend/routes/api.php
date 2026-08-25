<?php

use Illuminate\Http\Request;
use Illuminate\Support\Facades\Route;
use App\Http\Controllers\Api\DeviceSessionController;
use App\Http\Controllers\Api\OnlineOrderController;
use App\Http\Controllers\Api\PlatformAdminController;
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
})->middleware('auth:sanctum');

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

// Cross-tenant operator tool, gated by a shared secret rather than a session.
// Throttled because that secret is the only thing standing in front of it.
Route::post('/platform-admin', [PlatformAdminController::class, 'handle'])
    ->middleware('throttle:30,1');

Route::middleware('auth:sanctum')->group(function () {
    Route::get('/sync/bootstrap', [SyncController::class, 'bootstrap']);
    Route::post('/sync/push', [SyncController::class, 'push']);
    Route::get('/sync/pull', [SyncController::class, 'pull']);
    Route::get('/shifts/current', [ShiftController::class, 'current']);
    Route::get('/shifts/history', [ShiftController::class, 'history']);
    Route::post('/shifts/open', [ShiftController::class, 'open']);
    Route::post('/shifts/current/movements', [ShiftController::class, 'addMovement']);
    Route::post('/shifts/current/close', [ShiftController::class, 'close']);
    Route::get('/staff-roles', [StaffRoleController::class, 'index']);
    Route::put('/staff-roles', [StaffRoleController::class, 'sync']);
    Route::get('/staff-users', [StaffUserController::class, 'index']);
    Route::patch('/staff-users/{user}/role', [StaffUserController::class, 'updateRole']);
});
