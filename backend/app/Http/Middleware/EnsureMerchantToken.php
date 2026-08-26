<?php

namespace App\Http\Middleware;

use App\Models\Device;
use App\Models\User;
use Closure;
use Illuminate\Http\Request;
use Symfony\Component\HttpFoundation\Response;

/**
 * Keeps non-merchant identities out of the merchant API.
 *
 * `auth:sanctum` has no provider configured, and Sanctum reads that as "any
 * tokenable will do" — so a customer's portal token, or a rider's, satisfies it
 * and lands in a controller written on the assumption that `$request->user()`
 * is a User or a Device. Most of those controllers do check (SellerOrderController
 * aborts unless it holds a Device), but that is a check every future route has
 * to remember, and the one that forgets is the one that matters.
 *
 * The provider cannot simply be pinned in config/auth.php, because this API
 * genuinely serves two tokenables: staff sign in as User, tills pair as Device.
 * So the rule is stated here instead, once, on the group.
 */
class EnsureMerchantToken
{
    public function handle(Request $request, Closure $next): Response
    {
        $identity = $request->user();

        // 403 rather than 401: the token is real and the caller is genuinely
        // signed in — just not to this half of the product.
        abort_unless(
            $identity instanceof User || $identity instanceof Device,
            403,
            'This endpoint is part of the merchant API.',
        );

        return $next($request);
    }
}
