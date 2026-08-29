<?php

namespace App\Http\Controllers\Api;

use App\Http\Controllers\Controller;
use App\Models\AppRelease;
use Illuminate\Http\JsonResponse;

/**
 * The update check for sideloaded apps.
 *
 * Public and unauthenticated: the app asks this before a shopper has done
 * anything at all, including on first launch, and the answer is the same
 * public fact for everyone. Nothing here is tenant-scoped.
 *
 * A 404 is a real answer meaning "nothing published for that app" — the client
 * treats it as "you are up to date" rather than as a failure, which is what
 * lets the Android build ship ahead of the first row ever being written.
 */
class AppReleaseController extends Controller
{
    /** Kept narrow so a junk path segment is a 404 rather than a table scan. */
    private const SLUG_PATTERN = '/^[a-z0-9][a-z0-9-]{0,62}[a-z0-9]$/';

    public function show(string $slug): JsonResponse
    {
        $slug = strtolower(trim($slug));

        if (preg_match(self::SLUG_PATTERN, $slug) !== 1) {
            return response()->json(['message' => 'No release published for this app.'], 404);
        }

        $release = AppRelease::current($slug);

        if ($release === null) {
            return response()->json(['message' => 'No release published for this app.'], 404);
        }

        return response()->json($release->toClientArray());
    }
}
