<?php

namespace App\Http\Controllers\Api;

use App\Http\Controllers\Controller;
use App\Models\OrganizationMembership;
use App\Models\Store;
use App\Models\StoreMembership;
use App\Models\User;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\Hash;

class StaffSessionController extends Controller
{
    public function store(Request $request)
    {
        $validated = $request->validate([
            'organizationSlug' => ['required', 'string'],
            'storeCode' => ['required', 'string'],
            'username' => ['required', 'string'],
            'password' => ['required', 'string'],
        ]);

        $store = $this->resolveStore($validated['organizationSlug'], $validated['storeCode']);
        $user = User::query()->where('username', strtolower(trim($validated['username'])))->first();

        if (! $user || ! Hash::check($validated['password'], $user->password)) {
            abort(422, 'Incorrect username or password.');
        }

        $roleId = $this->resolveRoleForStore($user, $store);
        if (! $roleId) {
            abort(403, 'This account does not have access to the selected store.');
        }

        if ($user->email && ! $user->hasVerifiedEmail()) {
            $this->sendVerificationLink($user);
            abort(403, 'Please verify your email first. We just sent you another verification link.');
        }

        $token = $user->createToken("staff-session:{$store->id}", ['staff'])->plainTextToken;

        return response()->json([
            'user' => $this->serializeUser($user, $roleId),
            'session' => [
                'userId' => $user->id,
                'signedInAt' => now()->toIso8601String(),
                'authToken' => $token,
                'authSource' => 'remote',
            ],
        ]);
    }

    /**
     * Creates a staff account for a store.
     *
     * Email is required, and no session comes back: the account exists but
     * cannot be signed into until the address is verified. That is the whole
     * point of asking for one — an unverified address is a string somebody
     * typed, and it is the only way to reach an account holder who can no
     * longer sign in. `store()` already refuses an unverified user and resends
     * the link, so it is that check this feeds.
     *
     * The username stays the sign-in credential; the email is for proving
     * identity and for recovery, not for logging in.
     */
    public function register(Request $request)
    {
        $validated = $request->validate([
            'organizationSlug' => ['required', 'string'],
            'storeCode' => ['required', 'string'],
            'fullName' => ['required', 'string', 'max:120'],
            'username' => ['required', 'string', 'max:60'],
            'email' => ['required', 'email', 'max:190'],
            'password' => ['required', 'string', 'min:4'],
        ]);

        $store = $this->resolveStore($validated['organizationSlug'], $validated['storeCode']);
        $username = strtolower(trim($validated['username']));
        $email = strtolower(trim($validated['email']));

        if (User::query()->where('username', $username)->exists()) {
            abort(422, 'That username is already in use.');
        }

        // Checked by hand rather than with `unique:` so the comparison happens
        // against the same lowercased value the column stores - `unique` would
        // let "Ana@x.com" through when "ana@x.com" is already taken. Matches
        // SignupController and CustomerAuthController.
        if (User::query()->where('email', $email)->exists()) {
            abort(422, 'That email address already has an Omaykan account.');
        }

        $hasExistingMembers = OrganizationMembership::query()
            ->where('organization_id', $store->organization_id)
            ->exists();

        $roleId = $hasExistingMembers ? 'cashier' : 'admin';

        $user = User::query()->create([
            'name' => trim($validated['fullName']),
            'username' => $username,
            'email' => $email,
            'password' => $validated['password'],
            'status' => 'active',
        ]);

        OrganizationMembership::query()->create([
            'organization_id' => $store->organization_id,
            'user_id' => $user->id,
            'membership_role' => $roleId,
        ]);

        StoreMembership::query()->create([
            'store_id' => $store->id,
            'user_id' => $user->id,
            'membership_role' => $roleId,
        ]);

        $this->sendVerificationLink($user);

        // Deliberately no token. Signing the account straight in would make
        // verification decorative for the whole of that first session, which
        // is exactly the session in which a mistyped address does its damage.
        return response()->json([
            'user' => $this->serializeUser($user, $roleId),
            'verificationRequired' => true,
            'message' => 'Check your email for a verification link before signing in.',
        ], 201);
    }

    private function resolveStore(string $organizationSlug, string $storeCode): Store
    {
        return Store::query()
            ->with('organization')
            ->where('code', $storeCode)
            ->whereHas('organization', fn ($query) => $query->where('slug', $organizationSlug))
            ->firstOrFail();
    }

    private function resolveRoleForStore(User $user, Store $store): ?string
    {
        $storeMembership = StoreMembership::query()
            ->where('store_id', $store->id)
            ->where('user_id', $user->id)
            ->first();

        if ($storeMembership) {
            return $storeMembership->membership_role;
        }

        $organizationMembership = OrganizationMembership::query()
            ->where('organization_id', $store->organization_id)
            ->where('user_id', $user->id)
            ->first();

        return $organizationMembership?->membership_role;
    }

    private function serializeUser(User $user, string $roleId): array
    {
        return [
            'id' => $user->id,
            'fullName' => $user->name,
            'username' => $user->username,
            'passwordHash' => '',
            'roleId' => $roleId,
            'createdAt' => $user->created_at?->toIso8601String() ?? now()->toIso8601String(),
        ];
    }

    private function sendVerificationLink(User $user): void
    {
        try {
            $user->sendEmailVerificationNotification();
        } catch (\Throwable $e) {
            report($e);
        }
    }
}
