<?php

use Illuminate\Foundation\Application;
use Illuminate\Foundation\Configuration\Exceptions;
use Illuminate\Foundation\Configuration\Middleware;
use Illuminate\Http\Request;

return Application::configure(basePath: dirname(__DIR__))
    ->withRouting(
        web: __DIR__.'/../routes/web.php',
        api: __DIR__.'/../routes/api.php',
        commands: __DIR__.'/../routes/console.php',
        channels: __DIR__.'/../routes/channels.php',
        health: '/up',
    )
    ->withMiddleware(function (Middleware $middleware): void {
        $middleware->alias([
            'rider.approved' => \App\Http\Middleware\EnsureRiderIsApproved::class,
            'merchant.token' => \App\Http\Middleware\EnsureMerchantToken::class,
            'platform.active' => \App\Http\Middleware\EnsurePlatformAdminIsActive::class,
        ]);
    })
    ->withExceptions(function (Exceptions $exceptions): void {
        /*
         * Everything under /api answers JSON, whatever the caller asked for.
         *
         * Without this, an unauthenticated request that does not send
         * `Accept: application/json` is handled the way a browser would want:
         * Laravel redirects it to `route('login')`. There is no such route in
         * this application — the storefront's sign-in is a client-side page and
         * the API is token-only — so the redirect throws RouteNotFoundException
         * and the caller gets a **500** where it should have had a 401.
         *
         * That is not theoretical. The Android apps' Retrofit clients sent no
         * Accept header, so every rider holding an expired token polled the
         * board every 15 seconds, got a 500, and showed "The server had a
         * problem." forever: a 401 is what clears the token and moves the app
         * to sign-in, and a 500 clears nothing. The production log for
         * 2026-09-08 is 700 lines of `Route [login] not defined`, one every
         * fifteen seconds, from exactly that loop.
         *
         * The clients now send the header too. This is the half that cannot be
         * forgotten by a future one: an API that answers HTML redirects to
         * anybody is an API with a 500 waiting in it.
         */
        $exceptions->shouldRenderJsonWhen(
            fn (Request $request) => $request->is('api/*') || $request->expectsJson(),
        );
    })->create();
