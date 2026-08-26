<?php

namespace App\Http\Middleware;

use App\Models\Rider;
use Closure;
use Illuminate\Http\Request;
use Symfony\Component\HttpFoundation\Response;

/**
 * Everything a rider can do beyond reading their own status requires that a
 * human has looked at their licence.
 *
 * A middleware rather than a check inside each action, because the failure
 * mode of the per-action version is silent: a new endpoint added later simply
 * forgets, and an unreviewed account gets a stranger's address. Here, an
 * endpoint is unguarded only by being deliberately left out of the group.
 *
 * 403 with a machine-readable status, not a bare 403: the portal shows a
 * pending rider a different screen from a suspended one.
 */
class EnsureRiderIsApproved
{
    public function handle(Request $request, Closure $next): Response
    {
        $rider = $request->user();

        abort_unless($rider instanceof Rider, 403, 'Rider account required.');

        if (! $rider->isApproved()) {
            return response()->json([
                'message' => match ($rider->status) {
                    Rider::STATUS_PENDING => 'Your account is still being reviewed.',
                    Rider::STATUS_REJECTED => 'Your rider application was not approved.',
                    default => 'Your rider account is suspended.',
                },
                'riderStatus' => $rider->status,
                'reviewNote' => $rider->review_note,
            ], 403);
        }

        return $next($request);
    }
}
