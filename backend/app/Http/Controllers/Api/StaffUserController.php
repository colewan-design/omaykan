<?php

namespace App\Http\Controllers\Api;

use App\Http\Controllers\Controller;
use App\Models\Device;
use App\Models\OrganizationMembership;
use App\Models\PosRole;
use App\Models\StoreMembership;
use App\Models\User;
use Illuminate\Http\Request;

class StaffUserController extends Controller
{
    public function index(Request $request)
    {
        $device = $this->deviceFromRequest($request);

        $storeMembers = StoreMembership::query()
            ->where('store_id', $device->store_id)
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
     * An admin adding a member of staff from the till, rather than that person
     * registering themselves.
     *
     * Replaces the Firestore path via api/staff-create.ts. Unlike self-service
     * registration this asks for no email and sends no verification link: the
     * account is being made by someone already trusted with the store, for
     * somebody standing in front of them, and the role is chosen rather than
     * defaulted. Nothing here can sign in remotely until an email is added,
     * which is exactly what `store()` on the session controller enforces.
     */
    public function store(Request $request)
    {
        $device = $this->deviceFromRequest($request);

        $validated = $request->validate([
            'fullName' => ['required', 'string', 'max:120'],
            'username' => ['required', 'string', 'max:60'],
            'password' => ['required', 'string', 'min:4'],
            'roleId' => ['required', 'string'],
        ]);

        $username = strtolower(trim($validated['username']));

        $roleExists = PosRole::query()
            ->where('organization_id', $device->organization_id)
            ->where('role_key', $validated['roleId'])
            ->whereNull('deleted_at')
            ->exists();

        abort_unless($roleExists, 422, 'Unknown role.');

        if (User::query()->where('username', $username)->exists()) {
            abort(422, 'That username is already in use.');
        }

        $user = User::query()->create([
            'name' => trim($validated['fullName']),
            'username' => $username,
            'password' => $validated['password'],
            'status' => 'active',
        ]);

        OrganizationMembership::query()->create([
            'organization_id' => $device->organization_id,
            'user_id' => $user->id,
            'membership_role' => $validated['roleId'],
        ]);

        StoreMembership::query()->create([
            'store_id' => $device->store_id,
            'user_id' => $user->id,
            'membership_role' => $validated['roleId'],
        ]);

        return response()->json([
            'user' => [
                'id' => $user->id,
                'fullName' => $user->name,
                'username' => $user->username,
                'passwordHash' => '',
                'roleId' => $validated['roleId'],
                'createdAt' => $user->created_at?->toIso8601String() ?? now()->toIso8601String(),
            ],
        ], 201);
    }

    public function updateRole(Request $request, User $user)
    {
        $device = $this->deviceFromRequest($request);
        $validated = $request->validate([
            'roleId' => ['required', 'string'],
        ]);

        $roleExists = PosRole::query()
            ->where('organization_id', $device->organization_id)
            ->where('role_key', $validated['roleId'])
            ->whereNull('deleted_at')
            ->exists();

        abort_unless($roleExists, 422, 'Unknown role.');

        $organizationMembership = OrganizationMembership::query()
            ->where('organization_id', $device->organization_id)
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
                'store_id' => $device->store_id,
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

    private function deviceFromRequest(Request $request): Device
    {
        $device = $request->user();
        abort_unless($device instanceof Device, 403, 'Authenticated device required.');

        return $device;
    }
}
