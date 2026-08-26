<?php

namespace App\Http\Controllers\Api\Platform;

use App\Http\Controllers\Controller;
use App\Models\PlatformAdmin;
use App\Support\PlatformAudit;
use App\Support\ReadablePassword;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;
use Illuminate\Validation\Rule;
use Illuminate\Validation\ValidationException;

/**
 * Managing the operators themselves. Owner-only, all of it.
 *
 * There is no self-serve signup and there must not be one — an open
 * registration form on a cross-tenant tool is the shared secret's problem
 * again with better UX. Accounts come from here, or from
 * `php artisan platform:admin-create` on the box.
 */
class AdminController extends Controller
{
    public function index(Request $request): JsonResponse
    {
        $this->requireOwner($request);

        return response()->json([
            'admins' => PlatformAdmin::query()
                ->orderBy('name')
                ->get()
                ->map(fn (PlatformAdmin $admin) => $admin->toPortalArray())
                ->values(),
        ]);
    }

    /**
     * Creates an operator and returns their password once, in the clear — the
     * owner relays it, the same way an owner password reset already works.
     * Nothing stores the plaintext.
     */
    public function store(Request $request): JsonResponse
    {
        $this->requireOwner($request);

        $data = $request->validate([
            'name' => ['required', 'string', 'max:255'],
            'email' => ['required', 'email', 'max:255', 'unique:platform_admins,email'],
            'role' => ['required', Rule::in(PlatformAdmin::ROLES)],
        ]);

        $password = ReadablePassword::generate();

        $admin = PlatformAdmin::create($data + [
            'password' => $password,
            'status' => PlatformAdmin::STATUS_ACTIVE,
        ]);

        PlatformAudit::record($request, 'operator.created', $admin, [
            'email' => $admin->email,
            'role' => $admin->role,
        ]);

        return response()->json([
            'admin' => $admin->toPortalArray(),
            'password' => $password,
        ], 201);
    }

    /**
     * Enable or disable an operator.
     *
     * Disabling revokes the tokens immediately rather than waiting for the
     * middleware to catch the next request — losing access has to mean losing
     * it now. The account itself stays, so the audit rows it wrote keep
     * pointing at a real name.
     */
    public function setStatus(Request $request, PlatformAdmin $admin): JsonResponse
    {
        $this->requireOwner($request);

        $data = $request->validate([
            'status' => ['required', Rule::in(PlatformAdmin::STATUSES)],
        ]);

        // Locking yourself out of the tool you are holding is never the
        // intent, and recovering from it needs SSH.
        if ($admin->is($request->user()) && $data['status'] === PlatformAdmin::STATUS_DISABLED) {
            throw ValidationException::withMessages([
                'status' => 'You cannot disable your own account.',
            ]);
        }

        // The last active owner must stay: with none, nobody can delete a
        // tenant, create an operator, or re-enable anyone.
        if ($admin->isOwner() && $data['status'] === PlatformAdmin::STATUS_DISABLED) {
            $remainingOwners = PlatformAdmin::query()
                ->where('role', PlatformAdmin::ROLE_OWNER)
                ->where('status', PlatformAdmin::STATUS_ACTIVE)
                ->whereKeyNot($admin->getKey())
                ->count();

            if ($remainingOwners === 0) {
                throw ValidationException::withMessages([
                    'status' => 'This is the last active owner. Promote someone else first.',
                ]);
            }
        }

        $admin->status = $data['status'];
        $admin->save();

        if ($data['status'] === PlatformAdmin::STATUS_DISABLED) {
            $admin->tokens()->delete();
        }

        PlatformAudit::record(
            $request,
            $data['status'] === PlatformAdmin::STATUS_DISABLED ? 'operator.disabled' : 'operator.enabled',
            $admin,
            ['email' => $admin->email],
        );

        return response()->json(['admin' => $admin->toPortalArray()]);
    }

    /**
     * The role gate for this whole controller.
     *
     * Repeated per action rather than expressed as a middleware because it is
     * the only owner-only group besides tenant deletion — two callers is not
     * yet a middleware, and the check reads better where the damage happens.
     */
    private function requireOwner(Request $request): void
    {
        abort_unless($request->user()->isOwner(), 403, 'Only an owner can manage operators.');
    }
}
