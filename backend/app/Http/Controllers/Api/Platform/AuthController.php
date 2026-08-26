<?php

namespace App\Http\Controllers\Api\Platform;

use App\Http\Controllers\Controller;
use App\Models\PlatformAdmin;
use App\Support\PlatformAudit;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\Hash;
use Illuminate\Validation\ValidationException;

/**
 * Sign-in for the operator portal.
 *
 * Replaces the shared-secret unlock. The difference that matters is not the
 * form — it is that every action after this one has a name attached to it, and
 * that one person's access can be taken away without changing everybody's.
 */
class AuthController extends Controller
{
    public function login(Request $request): JsonResponse
    {
        $data = $request->validate([
            'email' => ['required', 'email'],
            'password' => ['required', 'string'],
        ]);

        $admin = PlatformAdmin::findByEmail($data['email']);

        // One message for "no such account" and for "wrong password", and the
        // hash is checked against a dummy when there is no account, so the
        // reply neither says nor times differently whether an address belongs
        // to an operator. This endpoint is public and the accounts behind it
        // are cross-tenant — it is worth more here than anywhere else.
        if ($admin === null) {
            Hash::check($data['password'], '$2y$12$'.str_repeat('.', 53));

            throw ValidationException::withMessages([
                'email' => 'That email and password don\'t match an operator account.',
            ]);
        }

        if (! Hash::check($data['password'], $admin->password)) {
            throw ValidationException::withMessages([
                'email' => 'That email and password don\'t match an operator account.',
            ]);
        }

        // A disabled account is refused here rather than being let in to meet
        // the middleware. Unlike a rider — who needs to read why they were
        // rejected — a revoked operator has nothing to do inside the portal,
        // and no session should be minted for one.
        if (! $admin->isActive()) {
            throw ValidationException::withMessages([
                'email' => 'That operator account has been disabled.',
            ]);
        }

        $admin->forceFill(['last_login_at' => now(), 'last_seen_at' => now()])->save();

        $token = $admin->createToken(PlatformAdmin::TOKEN_NAME, ['platform'])->plainTextToken;

        // Logged before the response so a stolen credential shows up in the
        // same place every other operator action does.
        PlatformAudit::record($request->setUserResolver(fn () => $admin), 'session.signed_in', $admin);

        return response()->json([
            'admin' => $admin->toPortalArray(),
            'token' => $token,
        ]);
    }

    /** Signs out this browser only; other sessions stay signed in. */
    public function logout(Request $request): JsonResponse
    {
        $request->user()->currentAccessToken()->delete();

        return response()->json(['signedOut' => true]);
    }

    public function me(Request $request): JsonResponse
    {
        return response()->json(['admin' => $request->user()->toPortalArray()]);
    }
}
