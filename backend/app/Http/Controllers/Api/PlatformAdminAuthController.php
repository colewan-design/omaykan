<?php

namespace App\Http\Controllers\Api;

use App\Http\Controllers\Controller;
use App\Models\PlatformAdmin;
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

    public function logout(Request $request): JsonResponse
    {
        $request->user()->currentAccessToken()->delete();

        return response()->json(['signedOut' => true]);
    }

    public function me(Request $request): JsonResponse
    {
        return response()->json(['admin' => $request->user()->toSessionArray()]);
    }
}
