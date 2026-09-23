<?php

namespace App\Http\Controllers;

use Illuminate\Http\JsonResponse;

/**
 * Digital Asset Links — the domain vouching for the Android app.
 *
 * What it buys: the password-reset link in a customer's email opens the app
 * directly instead of asking "which app should open this?". Android fetches
 * this file over HTTPS at install time and again periodically, and only counts
 * the link as verified if the package name and the signing certificate's
 * SHA-256 fingerprint both appear here.
 *
 * Served by the application rather than dropped in the web root as a static
 * file for one reason: the fingerprints are deployment configuration — debug
 * and release are two different certificates and therefore two different apps
 * to Android — and configuration belongs in env, not in a JSON file somebody
 * has to remember to edit on the server.
 *
 * With no fingerprints configured this answers an empty list, which is exactly
 * what an unverified domain looks like. Nothing breaks: the reset link still
 * works, Android just asks which app should handle it. See mobile-plan.md §9.
 */
class AssetLinksController extends Controller
{
    public function show(): JsonResponse
    {
        $package = (string) config('services.android.package');
        $fingerprints = array_values(array_filter(
            array_map(
                static fn (string $value) => strtoupper(trim($value)),
                (array) config('services.android.sha256_fingerprints', []),
            ),
            static fn (string $value) => $value !== '',
        ));

        $statements = ($package === '' || $fingerprints === []) ? [] : [[
            // handle_all_urls covers every link the app declares a filter for,
            // so adding one to the manifest later needs no change here.
            'relation' => ['delegate_permission/common.handle_all_urls'],
            'target' => [
                'namespace' => 'android_app',
                'package_name' => $package,
                'sha256_cert_fingerprints' => $fingerprints,
            ],
        ]];

        // Android's verifier is strict about the content type and will not
        // follow a redirect, so this is served as-is at the exact path.
        return response()
            ->json($statements, 200, [], JSON_UNESCAPED_SLASHES)
            ->header('Content-Type', 'application/json');
    }
}
