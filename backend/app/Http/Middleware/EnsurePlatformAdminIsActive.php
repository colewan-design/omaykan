<?php

namespace App\Http\Middleware;

use App\Models\PlatformAdmin;
use Closure;
use Illuminate\Http\Request;
use Symfony\Component\HttpFoundation\Response;

/**
 * Everything in the operator portal requires an account that is still allowed
 * to be there.
 *
 * A middleware rather than a check inside each action, for the reason
 * EnsureRiderIsApproved gives: the failure mode of the per-action version is
 * silent — a route added later simply forgets, and a revoked operator keeps
 * cross-tenant access. Here, an endpoint is unguarded only by being
 * deliberately left outside the group.
 *
 * Disabling does not delete the token, so a disabled operator's existing
 * session hits this on its next request. It also stamps `last_seen_at`, which
 * is the cheapest place to do it: one write on a request that was already
 * touching the row.
 */
class EnsurePlatformAdminIsActive
{
    public function handle(Request $request, Closure $next): Response
    {
        $admin = $request->user();

        abort_unless($admin instanceof PlatformAdmin, 403, 'Operator account required.');

        if (! $admin->isActive()) {
            // Machine-readable, not a bare 403: the portal shows a disabled
            // operator a screen that explains it rather than a dead request.
            return response()->json([
                'message' => 'This operator account has been disabled.',
                'adminStatus' => $admin->status,
            ], 403);
        }

        $admin->forceFill(['last_seen_at' => now()])->saveQuietly();

        return $next($request);
    }
}
