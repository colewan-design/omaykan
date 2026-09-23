<?php

namespace App\Services;

use App\Models\OrganizationMembership;
use App\Models\Store;
use App\Models\StoreMembership;
use App\Models\User;
use App\Services\Billing\TenantAccess;
use App\Services\Billing\TenantAccessDenied;
use Illuminate\Http\Request;

/**
 * Turning a staff token into "this person, acting for this shop".
 *
 * ## Where the store comes from
 *
 * A staff token is minted for one store and says so in its abilities:
 * `store:{uuid}`, alongside `staff`. The alternative — trusting a store id sent
 * in the request body — would let any signed-in cashier read another shop's
 * orders by editing one field, which is exactly the hole the old device token
 * could not have, because a device *was* a row in one store.
 *
 * A person who works at two shops signs in once and holds one token per shop,
 * minted by StaffAuthController::selectStore. Switching shops mints a new one
 * rather than re-scoping the old, so a token that leaks is still a token for a
 * single shop.
 *
 * ## Why membership is re-checked on every request
 *
 * Sanctum tokens do not expire. If access were proved only at sign-in, sacking
 * someone would leave their phone working until they chose to sign out. The
 * membership lookup here is the revocation: drop the row and the next request
 * 403s. It costs one indexed read per request, which is the price of that.
 */
class StoreContextResolver
{
    public const ABILITY_PREFIX = 'store:';

    /**
     * @throws \Symfony\Component\HttpKernel\Exception\HttpException
     */
    public function resolve(Request $request): StoreContext
    {
        $user = $request->user();
        abort_unless($user instanceof User, 403, 'Authenticated staff account required.');
        abort_unless($user->status === 'active', 403, 'This account is no longer active.');

        $store = $this->storeFromToken($user);

        $role = $this->roleFor($user, $store);
        abort_unless($role !== null, 403, 'This account does not have access to that store.');

        // After the membership check, not before: someone with no claim on this
        // shop should not learn that it has been suspended.
        $access = $this->accessFor($store);

        return new StoreContext($user, $store, $role, $access);
    }

    /**
     * Whether this store's organization may be acted for at all.
     *
     * Asked here, on every request, for the reason the membership is: tokens
     * do not expire, so a suspension enforced only at sign-in would leave every
     * till already signed in working until someone happened to sign out.
     *
     * Suspended stops here. Unpaid is let through with the verdict attached —
     * its staff can still read their own records — and each action that writes
     * refuses it through StoreContext::abortUnlessWritable.
     *
     * @throws TenantAccessDenied
     */
    private function accessFor(Store $store): TenantAccess
    {
        $access = $store->organization?->accessVerdict() ?? TenantAccess::Suspended;

        if (! $access->allowsStaffAccess()) {
            throw TenantAccessDenied::for($access);
        }

        return $access;
    }

    /**
     * The store this token was minted for.
     *
     * Read from the token's abilities and nowhere else — see the class
     * docblock. A token with no `store:` ability is one minted by
     * StaffAuthController::signIn before a shop was chosen: it can reach the
     * store picker and nothing else, so this is a 403 with a message that says
     * what to do rather than a bare refusal.
     */
    private function storeFromToken(User $user): Store
    {
        $token = $user->currentAccessToken();
        $abilities = $token?->abilities ?? [];

        $storeId = null;
        foreach ($abilities as $ability) {
            if (str_starts_with((string) $ability, self::ABILITY_PREFIX)) {
                $storeId = substr((string) $ability, strlen(self::ABILITY_PREFIX));
                break;
            }
        }

        abort_if($storeId === null, 403, 'Choose a store for this session first.');

        // The organization and its subscription come along because accessFor
        // reads both on every request; loading them here makes that one query
        // rather than three.
        $store = Store::query()->with('organization.subscription')->find($storeId);

        abort_if($store === null || $store->status !== 'active', 403, 'That store is no longer available.');

        return $store;
    }

    /**
     * The store membership if there is one, otherwise the organization-wide
     * one — an owner with no per-store row still runs every store they own.
     *
     * Same precedence the till's sign-in has always used.
     */
    private function roleFor(User $user, Store $store): ?string
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
}
