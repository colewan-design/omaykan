<?php

namespace App\Http\Controllers\Api;

use App\Http\Controllers\Controller;
use App\Models\CustomerAccount;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\Hash;
use Illuminate\Support\Facades\Password;
use Illuminate\Support\Str;
use Illuminate\Validation\Rules\Password as PasswordRule;
use Illuminate\Validation\ValidationException;

/**
 * Sign-up, sign-in and password recovery for shoppers.
 *
 * The storefront's first real identity. Everything before this addressed a
 * customer by an unguessable order UUID and kept their details in the
 * browser's localStorage; an account is what lets a phone and a laptop be the
 * same person, and what makes "your orders" mean something the server can
 * answer.
 *
 * Tokens are Sanctum personal access tokens on the `customer` guard, minted
 * with the 'customer' ability. Guest checkout is untouched: POST
 * /api/online-orders still works with no token at all, and simply records who
 * placed the order when one is present.
 */
class CustomerAuthController extends Controller
{
    /** Named per device so someone can recognise a session they don't want. */
    private const TOKEN_NAME = 'customer-portal';

    public function register(Request $request): JsonResponse
    {
        $data = $request->validate([
            'name' => ['required', 'string', 'max:120'],
            'email' => ['required', 'email', 'max:190'],
            'phone' => ['nullable', 'string', 'max:40'],
            'password' => ['required', 'confirmed', PasswordRule::min(8)],
        ]);

        // Checked by hand rather than with `unique:` so the comparison happens
        // on the same lowercased value the column stores — `unique` would let
        // "Ana@x.com" through when "ana@x.com" is already taken.
        if (CustomerAccount::findByEmail($data['email']) !== null) {
            throw ValidationException::withMessages([
                'email' => 'There is already an account with that email. Try signing in instead.',
            ]);
        }

        $account = CustomerAccount::query()->create([
            'name' => trim($data['name']),
            'email' => $data['email'],
            'phone' => isset($data['phone']) ? trim($data['phone']) : null,
            'password' => $data['password'],
        ]);

        $this->sendVerificationLink($account);

        return response()->json([
            'account' => $account->toStorefrontArray(),
            'verificationRequired' => true,
            'message' => 'Check your email for a verification link before signing in.',
        ], 201);
    }

    public function login(Request $request): JsonResponse
    {
        $data = $request->validate([
            'email' => ['required', 'email'],
            'password' => ['required', 'string'],
        ]);

        $account = CustomerAccount::findByEmail($data['email']);

        // One message for "no such account" and for "wrong password", and the
        // hash is checked against a dummy when there is no account, so the
        // reply neither says nor times differently whether an address is
        // registered here.
        if ($account === null) {
            Hash::check($data['password'], '$2y$12$'.str_repeat('.', 53));

            throw ValidationException::withMessages([
                'email' => 'That email and password don\'t match an account.',
            ]);
        }

        if (! Hash::check($data['password'], $account->password)) {
            throw ValidationException::withMessages([
                'email' => 'That email and password don\'t match an account.',
            ]);
        }

        if (! $account->hasVerifiedEmail()) {
            $this->sendVerificationLink($account);

            throw ValidationException::withMessages([
                'email' => 'Please verify your email first. We just sent you another verification link.',
            ]);
        }

        return $this->sessionResponse($account, $request);
    }

    /** Signs out this device only; other phones and laptops stay signed in. */
    public function logout(Request $request): JsonResponse
    {
        $request->user()->currentAccessToken()->delete();

        return response()->json(['signedOut' => true]);
    }

    public function me(Request $request): JsonResponse
    {
        return response()->json(['account' => $request->user()->toStorefrontArray()]);
    }

    /**
     * Always answers the same way, whether or not the address is registered.
     * A "no such account" here would turn the endpoint into a way to test
     * which of a list of email addresses shops on Omaykan.
     */
    public function forgotPassword(Request $request): JsonResponse
    {
        $data = $request->validate([
            'email' => ['required', 'email'],
        ]);

        Password::broker('customers')->sendResetLink([
            'email' => strtolower(trim($data['email'])),
        ]);

        return response()->json([
            'message' => 'If that email has an account, a reset link is on its way.',
        ]);
    }

    public function resetPassword(Request $request): JsonResponse
    {
        $data = $request->validate([
            'token' => ['required', 'string'],
            'email' => ['required', 'email'],
            'password' => ['required', 'confirmed', PasswordRule::min(8)],
        ]);

        $status = Password::broker('customers')->reset(
            [
                'email' => strtolower(trim($data['email'])),
                'password' => $data['password'],
                'password_confirmation' => $data['password_confirmation'] ?? $data['password'],
                'token' => $data['token'],
            ],
            function (CustomerAccount $account, string $password) {
                $account->forceFill([
                    'password' => $password,
                    'remember_token' => Str::random(60),
                    'email_verified_at' => $account->email_verified_at ?? now(),
                ])->save();

                // Someone resetting a password may be doing it because someone
                // else has it. Every existing session goes, everywhere.
                $account->tokens()->delete();
            },
        );

        if ($status !== Password::PASSWORD_RESET) {
            throw ValidationException::withMessages([
                'token' => 'That reset link has expired or has already been used. Ask for a new one.',
            ]);
        }

        $account = CustomerAccount::findByEmail($data['email']);

        // Signed straight in: the alternative is bouncing someone who has just
        // proved they hold the mailbox back to a login form.
        return $this->sessionResponse($account, $request);
    }

    private function sessionResponse(CustomerAccount $account, Request $request, int $status = 200): JsonResponse
    {
        $token = $account->createToken(self::TOKEN_NAME, ['customer'])->plainTextToken;

        return response()->json([
            'account' => $account->toStorefrontArray(),
            'token' => $token,
        ], $status);
    }

    private function sendVerificationLink(CustomerAccount $account): void
    {
        try {
            $account->sendEmailVerificationNotification();
        } catch (\Throwable $e) {
            report($e);
        }
    }
}
