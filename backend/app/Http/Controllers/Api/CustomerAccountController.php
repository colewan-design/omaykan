<?php

namespace App\Http\Controllers\Api;

use App\Http\Controllers\Controller;
use App\Models\CustomerAccount;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\Hash;
use Illuminate\Validation\Rule;
use Illuminate\Validation\Rules\Password as PasswordRule;
use Illuminate\Validation\ValidationException;

/**
 * The signed-in shopper's own profile.
 *
 * Name, phone and preferences change freely. Email and password are the
 * credentials, so both require the current password — an unattended laptop is
 * otherwise one click away from becoming somebody else's account.
 */
class CustomerAccountController extends Controller
{
    public function update(Request $request): JsonResponse
    {
        /** @var CustomerAccount $account */
        $account = $request->user();

        $data = $request->validate([
            'name' => ['sometimes', 'required', 'string', 'max:120'],
            'phone' => ['sometimes', 'nullable', 'string', 'max:40'],
            'preferences.emailUpdates' => ['sometimes', 'boolean'],
            'preferences.smsUpdates' => ['sometimes', 'boolean'],
            'preferences.marketingEmails' => ['sometimes', 'boolean'],
            'preferences.substitutions' => ['sometimes', Rule::in(['call', 'best-match', 'refund'])],
        ]);

        if (array_key_exists('name', $data)) {
            $account->name = trim($data['name']);
        }

        if (array_key_exists('phone', $data)) {
            $account->phone = $data['phone'] === null ? null : trim($data['phone']);
        }

        if (array_key_exists('preferences', $data)) {
            // Merged over what is stored, not replaced: the portal saves one
            // toggle at a time, and a whole-object write would blank the rest.
            $account->preferences = array_merge($account->resolvedPreferences(), $data['preferences']);
        }

        $account->save();

        return response()->json(['account' => $account->fresh()->toStorefrontArray()]);
    }

    /**
     * Changing the address a reset link would be sent to is as good as owning
     * the account, so this asks for the password even though the caller is
     * already signed in.
     */
    public function updateEmail(Request $request): JsonResponse
    {
        /** @var CustomerAccount $account */
        $account = $request->user();

        $data = $request->validate([
            'email' => ['required', 'email', 'max:190'],
            'currentPassword' => ['required', 'string'],
        ]);

        $this->assertPassword($account, $data['currentPassword']);

        $existing = CustomerAccount::findByEmail($data['email']);

        if ($existing !== null && $existing->id !== $account->id) {
            throw ValidationException::withMessages([
                'email' => 'There is already an account with that email.',
            ]);
        }

        $account->email = $data['email'];
        $account->save();

        return response()->json(['account' => $account->fresh()->toStorefrontArray()]);
    }

    public function updatePassword(Request $request): JsonResponse
    {
        /** @var CustomerAccount $account */
        $account = $request->user();

        $data = $request->validate([
            'currentPassword' => ['required', 'string'],
            'password' => ['required', 'confirmed', PasswordRule::min(8)],
        ]);

        $this->assertPassword($account, $data['currentPassword']);

        $account->password = $data['password'];
        $account->save();

        // Every other device is signed out — a password change is how someone
        // ejects a session they no longer trust. This one survives.
        $currentTokenId = $request->user()->currentAccessToken()->id;
        $account->tokens()->whereKeyNot($currentTokenId)->delete();

        return response()->json(['account' => $account->fresh()->toStorefrontArray()]);
    }

    private function assertPassword(CustomerAccount $account, string $password): void
    {
        if (! Hash::check($password, $account->password)) {
            throw ValidationException::withMessages([
                'currentPassword' => 'That password is not right.',
            ]);
        }
    }
}
