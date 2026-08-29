<?php

namespace App\Http\Controllers\Api;

use App\Http\Controllers\Controller;
use App\Models\CustomerAccount;
use App\Services\GoogleIdentity;
use App\Services\GoogleIdentityException;
use App\Services\GoogleIdentityVerifier;
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
 *
 * Two ways in, one kind of session. `login` takes an email and a password;
 * `google` takes a signed assertion from Google and takes the shopper's word
 * for none of it — see GoogleIdentityVerifier. Both end at `sessionResponse`,
 * so nothing downstream can tell how somebody signed in, and both the web
 * storefront and the Android app use the same pair.
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

        // Someone who signed up with the Google button has no password here at
        // all, so there is nothing to compare against and the neutral message
        // would strand them on a form that can never let them in. Told plainly
        // instead: `register` already answers whether an address is taken, so
        // this discloses nothing that endpoint doesn't.
        if (! $account->hasPassword()) {
            throw ValidationException::withMessages([
                'email' => 'This account signs in with Google. Use the Google button instead.',
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

    /**
     * Sign in — or sign up — with Google.
     *
     * One endpoint for both front ends. The storefront's Google Identity
     * Services button hands the browser an ID token directly; the Android app
     * runs an authorization-code exchange with PKCE behind a Custom Tab and
     * posts the `id_token` out of the reply. Same artefact, same checks.
     *
     * No email is sent and nothing is left pending: Google has already proved
     * the shopper holds the mailbox, and asking them to go and click a link in
     * an address Google just vouched for would be ceremony, not security.
     */
    public function google(Request $request, GoogleIdentityVerifier $verifier): JsonResponse
    {
        $data = $request->validate([
            'credential' => ['required', 'string'],
        ]);

        try {
            $identity = $verifier->verify($data['credential']);
        } catch (GoogleIdentityException $e) {
            // Reported so a misconfigured client id or an unreachable Google
            // shows up in the log as itself rather than as a wave of shoppers
            // saying sign-in is broken.
            report($e);

            throw ValidationException::withMessages(['credential' => $e->getMessage()]);
        }

        // Google will say false for an address on a Workspace domain that never
        // completed verification. Trusting it anyway would make this endpoint a
        // way to claim any account whose email somebody can merely type.
        if (! $identity->emailVerified) {
            throw ValidationException::withMessages([
                'credential' => 'That Google account has an unverified email address. Verify it with Google first.',
            ]);
        }

        $account = $this->resolveGoogleAccount($identity);

        return $this->sessionResponse($account, $request, $account->wasRecentlyCreated ? 201 : 200);
    }

    /**
     * The Google identity's account: the one already linked to it, the one
     * that owns the address, or a new one.
     *
     * The middle case is the delicate one — it is an account takeover if the
     * address hasn't been proved. It has been: `google()` refuses anything
     * Google won't call verified before this is reached, so linking here is the
     * same claim as clicking the link in our own verification mail, made by the
     * only other party that can check the mailbox.
     */
    private function resolveGoogleAccount(GoogleIdentity $identity): CustomerAccount
    {
        $linked = CustomerAccount::findByGoogleSub($identity->sub);

        if ($linked !== null) {
            // The picture is Google's to change, so it is refreshed on the way
            // through. The name, the phone and the addresses are the shopper's
            // own and are never overwritten from here.
            $linked->avatar_url = $identity->picture;

            // Google lets someone change the address on their account, so the
            // one on the token can drift from the one stored here. Followed —
            // unless a second local account already holds it, in which case
            // following it would collide on a unique column and 500 the login.
            // The stale address is the lesser problem, and the shopper can
            // still change it from the portal.
            $collision = CustomerAccount::findByEmail($identity->email);

            if ($collision === null || $collision->is($linked)) {
                $linked->email = $identity->email;
            }

            $linked->save();

            return $linked;
        }

        $existing = CustomerAccount::findByEmail($identity->email);

        if ($existing !== null) {
            $existing->forceFill([
                'google_sub' => $identity->sub,
                'avatar_url' => $existing->avatar_url ?? $identity->picture,
                // A shopper who registered, never clicked the link, and then
                // pressed the Google button has now proved the address twice as
                // well as they would have by clicking it.
                'email_verified_at' => $existing->email_verified_at ?? now(),
            ])->save();

            return $existing;
        }

        $account = CustomerAccount::query()->create([
            'name' => $identity->name,
            'email' => $identity->email,
            'google_sub' => $identity->sub,
            'avatar_url' => $identity->picture,
            // Null, not a random hash. There is no password on this account and
            // the column says so, which is what lets `hasPassword()` give the
            // portal an honest answer.
            'password' => null,
        ]);

        $account->forceFill(['email_verified_at' => now()])->save();

        return $account;
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
