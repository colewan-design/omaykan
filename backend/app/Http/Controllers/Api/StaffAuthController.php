<?php

namespace App\Http\Controllers\Api;

use App\Http\Controllers\Controller;
use App\Models\OrganizationMembership;
use App\Models\Store;
use App\Models\StoreMembership;
use App\Models\User;
use App\Services\Billing\TenantAccess;
use App\Services\Billing\TenantAccessDenied;
use App\Services\GoogleIdentity;
use App\Services\GoogleIdentityException;
use App\Services\GoogleIdentityVerifier;
use App\Services\StoreContextResolver;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\Hash;
use Illuminate\Validation\ValidationException;

/**
 * Staff sign-in: a person, not a device.
 *
 * ## What this replaced
 *
 * Every merchant client used to pair with the shop's code — one string that was
 * both the shop's public identifier and the secret a till proved to join it.
 * That was a genuinely good sign-in for a counter (no password at six in the
 * morning) and it is gone for the reason it was good: a shared secret typed off
 * paperwork identifies a *shop*, so nothing the app did could be attributed to
 * a person, a leaver kept access until the whole shop's code was rotated, and
 * the code had to be stored in the clear to be displayable.
 *
 * ## Two steps, always
 *
 * Sign-in proves who you are and returns the shops you can act for. It does not
 * pick one, because a manager covering three branches is the normal case and
 * guessing wrong means orders advanced at the wrong shop. The token it returns
 * carries `staff` alone and reaches nothing but [stores] and [selectStore].
 *
 * [selectStore] mints the token everything else runs on: `staff` plus
 * `store:{uuid}`, which is where StoreContextResolver reads the shop from. A
 * client holding one shop's token asks again for another rather than re-scoping
 * what it has, so a token that leaks is a token for one shop.
 */
class StaffAuthController extends Controller
{
    /**
     * Username or email, plus a password.
     *
     * The identifier is deliberately either: `staff-register` has always
     * created accounts from a username with no email address, and those people
     * still have to be able to sign in. An account created by the Google button
     * has no password at all, and gets told which button to press rather than
     * "incorrect password", which would be true and useless.
     */
    public function signIn(Request $request): JsonResponse
    {
        $validated = $request->validate([
            'identifier' => ['required', 'string', 'max:190'],
            'password' => ['required', 'string'],
        ]);

        $identifier = strtolower(trim($validated['identifier']));

        $user = User::query()
            ->where('username', $identifier)
            ->orWhere('email', $identifier)
            ->first();

        // One message for "no such account" and for "wrong password": this
        // endpoint is public, and telling them apart turns it into a way to
        // find out who works where.
        $failure = ValidationException::withMessages([
            'identifier' => 'Incorrect username or password.',
        ]);

        if (! $user) {
            throw $failure;
        }

        if (! $user->hasPassword()) {
            throw ValidationException::withMessages([
                'identifier' => 'This account signs in with Google. Use the Google button instead.',
            ]);
        }

        if (! Hash::check($validated['password'], $user->password)) {
            throw $failure;
        }

        if ($user->email && ! $user->hasVerifiedEmail()) {
            $this->sendVerificationLink($user);

            abort(403, 'Please verify your email first. We just sent you another verification link.');
        }

        return $this->sessionResponse($user);
    }

    /**
     * Sign in with Google.
     *
     * Unlike the customer endpoint this one never creates an account. A staff
     * account is a claim on somebody else's shop, so it is made by that shop —
     * through signup, for an owner, or by an admin in Staff — and Google only
     * proves who is holding the phone. Someone with a valid Google account and
     * no membership gets told to ask their manager, not a new empty session.
     */
    public function google(Request $request, GoogleIdentityVerifier $verifier): JsonResponse
    {
        $validated = $request->validate([
            'credential' => ['required', 'string'],
        ]);

        try {
            $identity = $verifier->verify($validated['credential']);
        } catch (GoogleIdentityException $e) {
            // Reported so a misconfigured client id or an unreachable Google
            // shows up in the log as itself rather than as a wave of staff
            // saying sign-in is broken.
            report($e);

            throw ValidationException::withMessages(['credential' => $e->getMessage()]);
        }

        // False for an address on a Workspace domain that never completed
        // verification. Trusting it anyway would make this a way into any shop
        // whose staff email somebody can merely type.
        if (! $identity->emailVerified) {
            throw ValidationException::withMessages([
                'credential' => 'That Google account has an unverified email address. Verify it with Google first.',
            ]);
        }

        $user = $this->staffAccountFor($identity);

        if ($user === null) {
            abort(403, "That Google account isn't set up for any shop yet. Ask an admin to add you in Staff.");
        }

        return $this->sessionResponse($user);
    }

    /**
     * The shops this account can act for.
     *
     * Reachable with the unscoped token from sign-in, so a client that has been
     * signed in for a week can re-ask without making the person type anything —
     * a new branch appears, or a revoked membership disappears.
     */
    public function stores(Request $request): JsonResponse
    {
        $user = $request->user();
        abort_unless($user instanceof User, 403, 'Authenticated staff account required.');

        return response()->json(['stores' => $this->storesFor($user)]);
    }

    /**
     * Choose the shop this session acts for, and get the token that does it.
     *
     * Deliberately mints rather than re-scopes: Sanctum abilities are fixed at
     * creation, and a token that could widen its own scope would make the
     * `store:` ability worth nothing.
     */
    public function selectStore(Request $request): JsonResponse
    {
        $user = $request->user();
        abort_unless($user instanceof User, 403, 'Authenticated staff account required.');

        $validated = $request->validate([
            'storeId' => ['required', 'string'],
        ]);

        $store = Store::query()->with('organization.subscription')->find($validated['storeId']);

        // Same message whether the store does not exist or the account has no
        // membership for it: a signed-in cashier should not be able to probe
        // which store ids are real.
        $role = $store ? $this->roleFor($user, $store) : null;

        abort_if(
            $store === null || $store->status !== 'active' || $role === null,
            403,
            'This account does not have access to that store.',
        );

        // Refused here as well as in StoreContextResolver. The resolver alone
        // would be safe — every request made with the token would 403 — but it
        // would mint a working-looking token first and let the till find out
        // one call later, with nothing on screen to say why. At the door, the
        // refusal is the answer to the question the person just asked.
        //
        // An unpaid shop still gets its token: its staff keep read access, and
        // the verdict rides along in `store.tenantAccess` for the till to show.
        $access = $store->organization?->accessVerdict() ?? TenantAccess::Suspended;

        if (! $access->allowsStaffAccess()) {
            throw TenantAccessDenied::for($access);
        }

        $token = $user->createToken(
            "staff:{$store->id}",
            ['staff', StoreContextResolver::ABILITY_PREFIX.$store->id],
        )->plainTextToken;

        return response()->json([
            'token' => $token,
            'user' => $this->serializeUser($user, $role),
            'store' => [
                'id' => $store->id,
                'name' => $store->name,
                'code' => $store->code,
                'businessMode' => $store->business_mode,
                // Both halves of the tenant: the till echoes the pair back on
                // every `/sync/push`, and the server refuses the batch if they
                // do not match the session's own.
                'organizationId' => $store->organization_id,
                'organizationSlug' => $store->organization?->slug,
                'role' => $role,
                'tenantAccess' => $access->value,
            ],
        ]);
    }

    /**
     * Retire the token this request arrived on.
     *
     * The device era had no such endpoint — nothing revoked a device token, so
     * a lost phone meant rotating the shop's code and re-pairing every till.
     * A per-person token can simply be deleted, which is most of the point.
     */
    public function signOut(Request $request): JsonResponse
    {
        $request->user()?->currentAccessToken()?->delete();

        return response()->json(['signedOut' => true]);
    }

    /**
     * The account behind a Google identity: the one already linked to it, or
     * the one that owns the address.
     *
     * The second case is a takeover if the address has not been proved. It has:
     * [google] refuses anything Google will not call verified before this is
     * reached, so linking here is the same claim as clicking the link in our own
     * verification mail, made by the only other party that can read the mailbox.
     */
    private function staffAccountFor(GoogleIdentity $identity): ?User
    {
        $linked = User::findByGoogleSub($identity->sub);

        if ($linked !== null) {
            $linked->avatar_url = $identity->picture;

            // Google lets someone change the address on their account. Follow
            // it, unless another local account already holds the new one — that
            // would collide on a unique column and 500 the sign-in, and a stale
            // address is much the lesser problem.
            $collision = User::query()->where('email', $identity->email)->first();

            if ($collision === null || $collision->is($linked)) {
                $linked->email = $identity->email;
            }

            $linked->save();

            return $linked;
        }

        $existing = User::query()->where('email', $identity->email)->first();

        if ($existing === null) {
            return null;
        }

        $existing->forceFill([
            'google_sub' => $identity->sub,
            'avatar_url' => $existing->avatar_url ?? $identity->picture,
            // Someone invited by their manager, who never clicked the link and
            // then pressed the Google button, has now proved the address twice
            // as well as clicking it would have.
            'email_verified_at' => $existing->email_verified_at ?? now(),
        ])->save();

        return $existing;
    }

    /**
     * The reply both sign-in doors give: who you are, an unscoped token, and
     * the shops to choose from.
     */
    private function sessionResponse(User $user): JsonResponse
    {
        abort_unless($user->status === 'active', 403, 'This account is no longer active.');

        $stores = $this->storesFor($user);

        if ($stores === []) {
            abort(403, "This account isn't set up for any shop yet. Ask an admin to add you in Staff.");
        }

        return response()->json([
            'token' => $user->createToken('staff:pending-store', ['staff'])->plainTextToken,
            'user' => [
                'id' => $user->id,
                'fullName' => $user->name,
                'username' => $user->username,
                'email' => $user->email,
                'avatarUrl' => $user->avatar_url,
            ],
            'stores' => $stores,
        ]);
    }

    /**
     * Every live store this account reaches, by either kind of membership.
     *
     * An organization membership reaches all of that org's stores — an owner
     * with no per-store row still runs every branch they own — and a store
     * membership adds the single store it names. The per-store role wins where
     * both exist, which is the precedence the till has always used.
     *
     * @return list<array<string, mixed>>
     */
    private function storesFor(User $user): array
    {
        $orgRoles = OrganizationMembership::query()
            ->where('user_id', $user->id)
            ->pluck('membership_role', 'organization_id');

        $storeRoles = StoreMembership::query()
            ->where('user_id', $user->id)
            ->pluck('membership_role', 'store_id');

        $stores = Store::query()
            ->with('organization.subscription')
            ->where('status', 'active')
            ->where(function ($query) use ($orgRoles, $storeRoles) {
                $query->whereIn('organization_id', $orgRoles->keys())
                    ->orWhereIn('id', $storeRoles->keys());
            })
            ->orderBy('name')
            ->get();

        return $stores->map(fn (Store $store) => [
            'id' => $store->id,
            'name' => $store->name,
            'code' => $store->code,
            'businessMode' => $store->business_mode,
            'organizationSlug' => $store->organization?->slug,
            'role' => $storeRoles[$store->id] ?? $orgRoles[$store->organization_id],
            // Marked rather than hidden. A manager whose shop vanished from
            // the picker files a bug; one whose shop says "suspended" rings
            // support, which is the outcome anybody wants.
            'tenantAccess' => ($store->organization?->accessVerdict() ?? TenantAccess::Suspended)->value,
        ])->values()->all();
    }

    private function roleFor(User $user, Store $store): ?string
    {
        $storeMembership = StoreMembership::query()
            ->where('store_id', $store->id)
            ->where('user_id', $user->id)
            ->first();

        if ($storeMembership) {
            return $storeMembership->membership_role;
        }

        return OrganizationMembership::query()
            ->where('organization_id', $store->organization_id)
            ->where('user_id', $user->id)
            ->first()?->membership_role;
    }

    private function serializeUser(User $user, string $roleId): array
    {
        return [
            'id' => $user->id,
            'fullName' => $user->name,
            'username' => $user->username,
            'email' => $user->email,
            'avatarUrl' => $user->avatar_url,
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
