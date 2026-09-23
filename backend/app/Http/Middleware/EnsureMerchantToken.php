<?php

namespace App\Http\Middleware;

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
 * is a member of staff. Most of those controllers do check, but that is a check
 * every future route has to remember, and the one that forgets is the one that
 * matters.
 *
 * The provider still cannot simply be pinned in config/auth.php: the guard is
 * shared with the customer and rider tokenables, which is exactly what makes
 * this middleware necessary. What changed when pairing was retired is that
 * there is now only one thing to allow. A `Device` was the other, and nothing
 * mints a device token any more.
 *
 * This says "staff, not a shopper". It does not say which shop — that is
 * StoreContextResolver's job, on the routes that need it.
 */
class EnsureMerchantToken
{
    public function handle(Request $request, Closure $next): Response
    {
        // 403 rather than 401: the token is real and the caller is genuinely
        // signed in — just not to this half of the product.
        abort_unless(
            $request->user() instanceof User,
            403,
            'This endpoint is part of the merchant API.',
        );

        return $next($request);
    }
}
