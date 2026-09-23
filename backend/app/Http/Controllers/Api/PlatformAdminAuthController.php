<?php

namespace App\Http\Controllers\Api;

use App\Http\Controllers\Controller;
use App\Models\PlatformAdmin;
use App\Services\GoogleIdentity;
use App\Services\GoogleIdentityException;
use App\Services\GoogleIdentityVerifier;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\Hash;
use Illuminate\Support\Facades\Log;
use Illuminate\Validation\ValidationException;

/**
 * Sign-in for the platform operator.
 *
 * There is no register action and no password reset, deliberately: accounts
 * are created from the console (`php artisan platform-admin:create`), because
 * an endpoint that mints cross-tenant access is not something that should
 * exist on the public internet at all. A forgotten password is fixed by
 * running the same command again with --reset.
 *
 * Tokens are Sanctum personal access tokens on the `platform` guard, minted
 * with the `platform-admin` ability. They replace the shared PLATFORM_ADMIN_SECRET
 * that used to gate PlatformAdminController and RiderReviewController.
 */
class PlatformAdminAuthController extends Controller
{
    private const TOKEN_NAME = 'platform-admin';

    public function login(Request $request): JsonResponse
    {
        $data = $request->validate([
            'email' => ['required', 'email'],
            'password' => ['required', 'string'],
        ]);

        $admin = PlatformAdmin::findByEmail($data['email']);

        // One message for "no such account" and for "wrong password", and the
        // hash is checked against a dummy when there is no account, so the
        // reply neither says nor times differently whether an address can sign
        // in here. That matters more on this endpoint than on any other: the
        // set of people who can act across every tenant is small enough to be
        // worth enumerating.
        if ($admin === null) {
            Hash::check($data['password'], '$2y$12$'.str_repeat('.', 53));

            throw ValidationException::withMessages([
                'email' => 'That email and password don\'t match an operator account.',
            ]);
        }

        if (! Hash::check($data['password'], $admin->password)) {
            Log::warning('[platform-admin] failed sign-in', [
                'email' => $admin->email,
                'ip' => $request->ip(),
            ]);

            throw ValidationException::withMessages([
                'email' => 'That email and password don\'t match an operator account.',
            ]);
        }

        // Unlike a rejected rider — who signs in to read why — a disabled
        // operator has nothing to see, so this stops at the door. Said plainly
        // rather than as a wrong-password error: whoever is typing is someone
        // who used to have access, and a silent lie would just look like a bug.
        if ($admin->isDisabled()) {
            Log::warning('[platform-admin] disabled account attempted sign-in', [
                'email' => $admin->email,
                'ip' => $request->ip(),
            ]);

            return response()->json([
                'message' => 'That operator account has been disabled.',
            ], 403);
        }

        // Every existing token is revoked on sign-in. There is no legitimate
        // reason for this account to hold sessions on several devices at once,
        // and it makes "sign in again" a usable answer to a token you think
        // may have leaked.
        $admin->tokens()->delete();

        $admin->forceFill(['last_login_at' => now()])->save();

        Log::info('[platform-admin] signed in', [
            'email' => $admin->email,
            'ip' => $request->ip(),
        ]);

        return response()->json([
            'admin' => $admin->toSessionArray(),
            'token' => $admin->createToken(self::TOKEN_NAME, [PlatformAdmin::ABILITY])->plainTextToken,
        ]);
    }

    /**
     * Sign in with Google.
     *
     * The same no-registration rule the rest of this controller runs on. There
     * is no console command behind this endpoint and no way for it to mint an
     * operator: it signs in a row that `platform-admin:create` already made,
     * and refuses every identity without one. A valid Google account is not a
     * claim on this table.
     *
     * Worth being explicit about what this does change, because it is the
     * account that acts across every tenant: it makes control of that Google
     * mailbox equivalent to the operator's password. That is a trade the two
     * other staff doors already make, and Google's own second factor is
     * generally the stronger half of it — but it does mean an operator's Google
     * account wants 2FA on it, and that removing an operator means disabling
     * the row here, not only revoking their Google access.
     */
    public function google(Request $request, GoogleIdentityVerifier $verifier): JsonResponse
    {
        $validated = $request->validate([
            'credential' => ['required', 'string'],
        ]);

        try {
            $identity = $verifier->verify($validated['credential']);
        } catch (GoogleIdentityException $e) {
            report($e);

            throw ValidationException::withMessages(['credential' => $e->getMessage()]);
        }

        // An unverified address would make this endpoint reachable by anyone
        // who can type an operator's email into a Workspace domain they run.
        if (! $identity->emailVerified) {
            throw ValidationException::withMessages([
                'credential' => 'That Google account has an unverified email address. Verify it with Google first.',
            ]);
        }

        $admin = $this->adminFor($identity);

        if ($admin === null) {
            // Logged, unlike the equivalent on the other doors: an unknown
            // Google identity knocking here is somebody trying the operator
            // console, and that is worth being able to read back.
            Log::warning('[platform-admin] google sign-in for an unknown account', [
                'email' => $identity->email,
                'ip' => $request->ip(),
            ]);

            abort(403, 'That Google account is not an operator account.');
        }

        if ($admin->isDisabled()) {
            Log::warning('[platform-admin] disabled account attempted google sign-in', [
                'email' => $admin->email,
                'ip' => $request->ip(),
            ]);

            return response()->json([
                'message' => 'That operator account has been disabled.',
            ], 403);
        }

        // Same as [login]: one live session at a time, so "sign in again" is a
        // usable answer to a token you think may have leaked.
        $admin->tokens()->delete();

        $admin->forceFill(['last_login_at' => now()])->save();

        Log::info('[platform-admin] signed in with google', [
            'email' => $admin->email,
            'ip' => $request->ip(),
        ]);

        return response()->json([
            'admin' => $admin->toSessionArray(),
            'token' => $admin->createToken(self::TOKEN_NAME, [PlatformAdmin::ABILITY])->plainTextToken,
        ]);
    }

    public function logout(Request $request): JsonResponse
    {
        $request->user()->currentAccessToken()->delete();

        return response()->json(['signedOut' => true]);
    }

    public function me(Request $request): JsonResponse
    {
        return response()->json(['admin' => $request->user()->toSessionArray()]);
    }

    /**
     * The operator behind a Google identity: the one already linked to it, or
     * the one that owns the address.
     *
     * Linking on an address is only safe because [google] has already refused
     * anything Google will not call verified, so reaching here means the holder
     * of the Google account can read mail at that address — and an address that
     * is already in this table was put there from the console by someone who
     * meant it.
     *
     * Never creates. An identity with no row is not an operator.
     */
    private function adminFor(GoogleIdentity $identity): ?PlatformAdmin
    {
        $linked = PlatformAdmin::findByGoogleSub($identity->sub);

        if ($linked !== null) {
            // Follow an address change on the Google account, unless another
            // operator already holds the new address — that would collide on a
            // unique column and 500 the sign-in.
            $collision = PlatformAdmin::findByEmail($identity->email);

            if ($collision === null || $collision->is($linked)) {
                $linked->forceFill(['email' => $identity->email])->save();
            }

            return $linked;
        }

        $existing = PlatformAdmin::findByEmail($identity->email);

        if ($existing === null) {
            return null;
        }

        $existing->forceFill(['google_sub' => $identity->sub])->save();

        return $existing;
    }
}
