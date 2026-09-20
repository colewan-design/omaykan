<?php

namespace App\Http\Controllers\Api;

use App\Http\Controllers\Controller;
use App\Http\Controllers\Concerns\ActsForAStore;
use App\Models\PosRole;
use Illuminate\Http\Request;

class StaffRoleController extends Controller
{
    use ActsForAStore;

    public function index(Request $request)
    {
        $context = $this->storeContext($request);

        $roles = PosRole::query()
            ->where('organization_id', $context->organizationId())
            ->whereNull('deleted_at')
            ->orderBy('name')
            ->get()
            ->map(fn (PosRole $role) => [
                'id' => $role->role_key,
                'name' => $role->name,
                'permissions' => $role->permissions,
                // Null when the shop has not set one; the till falls back to
                // the same defaults RolePermissions holds.
                'maxDiscountPercent' => $role->max_discount_percent,
            ]);

        return response()->json([
            'roles' => $roles,
        ]);
    }

    public function sync(Request $request)
    {
        $context = $this->writableStoreContext($request);

        // Roles decide what everyone else in the shop may do, so this is not a
        // thing a cashier changes. The device era could not draw this line at
        // all — a paired till had no person behind it to have a role.
        abort_unless($context->isManager(), 403, 'Only an admin or manager can change roles.');

        $validated = $request->validate([
            'roles' => ['required', 'array', 'min:1'],
            'roles.*.id' => ['required', 'string', 'max:80'],
            'roles.*.name' => ['required', 'string', 'max:120'],
            'roles.*.permissions' => ['required', 'array'],
            'roles.*.maxDiscountPercent' => ['sometimes', 'nullable', 'integer', 'min:0', 'max:100'],
        ]);

        $incomingRoleKeys = [];

        foreach ($validated['roles'] as $roleData) {
            $incomingRoleKeys[] = $roleData['id'];

            PosRole::query()->updateOrCreate(
                [
                    'organization_id' => $context->organizationId(),
                    'role_key' => $roleData['id'],
                ],
                [
                    'name' => $roleData['name'],
                    'permissions' => $roleData['permissions'],
                    'deleted_at' => null,
                ] + (array_key_exists('maxDiscountPercent', $roleData)
                    // Only when sent: a till from before discount limits
                    // saving its roles must not wipe one an owner set. Admin
                    // is always 100 and never stored; see RolePermissions.
                    ? ['max_discount_percent' => $roleData['id'] === 'admin' ? null : $roleData['maxDiscountPercent']]
                    : []),
            );
        }

        PosRole::query()
            ->where('organization_id', $context->organizationId())
            ->whereNotIn('role_key', $incomingRoleKeys)
            ->whereNotIn('role_key', ['admin', 'guest'])
            ->whereNull('deleted_at')
            ->update([
                'deleted_at' => now(),
                'updated_at' => now(),
            ]);

        return $this->index($request);
    }
}
