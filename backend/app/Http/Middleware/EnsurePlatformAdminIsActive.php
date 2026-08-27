<?php

namespace App\Http\Middleware;

use App\Models\PlatformAdmin;
use Closure;
use Illuminate\Http\Request;
use Symfony\Component\HttpFoundation\Response;

/**
 * Every cross-tenant action requires an operator account that is still active
 * and a token that still carries the platform ability.
 *
 * A middleware rather than a check inside each action, for the reason given in
 * EnsureRiderIsApproved: the per-action version fails silently when the next
 * endpoint is added and simply forgets. Here an endpoint is unguarded only by
 * being deliberately left out of the group.
 *
 * The disabled check happens on every request, not just at sign-in, because a
 * token minted before the account was disabled would otherwise keep working
 * until it was pruned by hand — which is exactly the window that matters when
 * access is being revoked in a hurry.
 */
class EnsurePlatformAdminIsActive
{
    public function handle(Request $request, Closure $next): Response
    {
        $admin = $request->user();

        abort_unless($admin instanceof PlatformAdmin, 403, 'Operator account required.');

        abort_unless(
            $request->user()->tokenCan(PlatformAdmin::ABILITY),
            403,
            'This token cannot act as an operator.',
        );

        if ($admin->isDisabled()) {
            // Revoked here as well as refused: whatever disabled the account
            // may not have had a chance to clean up its tokens.
            $admin->tokens()->delete();

            return response()->json([
                'message' => 'That operator account has been disabled.',
            ], 403);
        }

        return $next($request);
    }
}
