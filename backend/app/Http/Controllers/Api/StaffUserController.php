<?php

namespace App\Http\Controllers\Api;

use App\Http\Controllers\Concerns\ActsForAStore;
use App\Http\Controllers\Controller;
use App\Models\OrganizationMembership;
use App\Models\PosRole;
use App\Models\StoreMembership;
use App\Models\User;
use Illuminate\Http\Request;
use Illuminate\Validation\ValidationException;

class StaffUserController extends Controller
{
    use ActsForAStore;

    public function index(Request $request)
    {
        $context = $this->storeContext($request);

        $storeMembers = StoreMembership::query()
            ->where('store_id', $context->storeId())
            ->pluck('membership_role', 'user_id');

        $users = User::query()
            ->whereIn('id', $storeMembers->keys())
            ->orderBy('name')
            ->get()
            ->map(fn (User $user) => [
                'id' => $user->id,
                'fullName' => $user->name,
                'username' => $user->username,
                'passwordHash' => '',
                'roleId' => $storeMembers[$user->id] ?? 'cashier',
                'createdAt' => $user->created_at?->toIso8601String() ?? now()->toIso8601String(),
            ]);

        return response()->json([
            'users' => $users,
        ]);
    }

    /**
     * Add a member of staff to this shop.
     *
     * This is where `POST /staff-register` went. That endpoint was public
     * because the shop's pairing code was the proof — anyone who could type it
     * could give themselves an account. With the codes retired the proof is an
     * admin or manager already signed in to the shop, which is a better answer
     * to "who decides who works here" than a string on a receipt was.
     *
     * A password is optional. Give an address and no password and the account
     * is Google-only: the person presses the Google button, StaffAuthController
     * links the identity to this row by the verified address, and no starting
     * password is ever spoken aloud across a counter.
     */
    public function store(Request $request)
    {
        $context = $this->storeContext($request);

        abort_unless($context->isManager(), 403, 'Only an admin or manager can add staff.');

        $validated = $request->validate([
            'fullName' => ['required', 'string', 'max:120'],
            'username' => ['nullable', 'string', 'max:60'],
            'email' => ['nullable', 'email', 'max:190'],
            'password' => ['nullable', 'string', 'min:6'],
            'roleId' => ['required', 'string', 'max:80'],
        ]);

        $username = isset($validated['username']) ? strtolower(trim($validated['username'])) : null;
        $email = isset($validated['email']) ? strtolower(trim($validated['email'])) : null;

        // One or the other, or there is no way for this person to sign in: the
        // password door takes a username or an email, and the Google door
        // matches on the address.
        if (($username === null || $username === '') && ($email === null || $email === '')) {
            throw ValidationException::withMessages([
                'username' => 'Give a username, an email address, or both — it is how they sign in.',
            ]);
        }

        if (($email === null || $email === '') && ($validated['password'] ?? '') === '') {
            throw ValidationException::withMessages([
                'password' => 'An account with no email address needs a password to sign in with.',
            ]);
        }

        $roleExists = PosRole::query()
            ->where('organization_id', $context->organizationId())
            ->where('role_key', $validated['roleId'])
            ->whereNull('deleted_at')
            ->exists();

        abort_unless($roleExists, 422, 'Unknown role.');

        if ($username && User::query()->where('username', $username)->exists()) {
            throw ValidationException::withMessages([
                'username' => 'That username is already in use.',
            ]);
        }

        if ($email && User::query()->where('email', $email)->exists()) {
            throw ValidationException::withMessages([
                'email' => 'That email address already has an Omaykan account.',
            ]);
        }

        $user = User::query()->create([
            'name' => trim($validated['fullName']),
            'username' => $username ?: null,
            'email' => $email ?: null,
            // Null rather than a random hash, so `hasPassword()` can tell the
            // sign-in screen the truth: this one is a Google account.
            'password' => ($validated['password'] ?? '') !== '' ? $validated['password'] : null,
            'status' => 'active',
        ]);

        OrganizationMembership::query()->updateOrCreate(
            [
                'organization_id' => $context->organizationId(),
                'user_id' => $user->id,
            ],
            ['membership_role' => $validated['roleId']],
        );

        StoreMembership::query()->updateOrCreate(
            [
                'store_id' => $context->storeId(),
                'user_id' => $user->id,
            ],
            ['membership_role' => $validated['roleId']],
        );

        return response()->json([
            'user' => [
                'id' => $user->id,
                'fullName' => $user->name,
                'username' => $user->username,
                'email' => $user->email,
                'passwordHash' => '',
                'roleId' => $validated['roleId'],
                'createdAt' => $user->created_at?->toIso8601String() ?? now()->toIso8601String(),
            ],
        ], 201);
    }

    public function updateRole(Request $request, User $user)
    {
        $context = $this->storeContext($request);

        abort_unless($context->isManager(), 403, 'Only an admin or manager can change roles.');

        $validated = $request->validate([
            'roleId' => ['required', 'string'],
        ]);

        $roleExists = PosRole::query()
            ->where('organization_id', $context->organizationId())
            ->where('role_key', $validated['roleId'])
            ->whereNull('deleted_at')
            ->exists();

        abort_unless($roleExists, 422, 'Unknown role.');

        $organizationMembership = OrganizationMembership::query()
            ->where('organization_id', $context->organizationId())
            ->where('user_id', $user->id)
            ->first();

        if (! $organizationMembership) {
            abort(404, 'User is not part of this organization.');
        }

        $organizationMembership->forceFill([
            'membership_role' => $validated['roleId'],
        ])->save();

        StoreMembership::query()->updateOrCreate(
            [
                'store_id' => $context->storeId(),
                'user_id' => $user->id,
            ],
            [
                'membership_role' => $validated['roleId'],
            ],
        );

        return response()->json([
            'user' => [
                'id' => $user->id,
                'fullName' => $user->name,
                'username' => $user->username,
                'passwordHash' => '',
                'roleId' => $validated['roleId'],
                'createdAt' => $user->created_at?->toIso8601String() ?? now()->toIso8601String(),
            ],
        ]);
    }
}
