<?php

namespace Tests\Concerns;

use App\Models\Organization;
use App\Models\OrganizationMembership;
use App\Models\Store;
use App\Models\User;

/**
 * A merchant-API token, the way a real client gets one.
 *
 * These tests used to pair: one POST to `/api/device-sessions` with the shop's
 * code and you held a token for that shop. Sign-in is two calls now — prove who
 * you are, then choose the shop — and the helper goes through both rather than
 * minting a token directly, because the store scoping lives in the second call
 * and a test that skipped it would be testing something no client does.
 */
trait SignsInStaff
{
    /**
     * Sign in and pick a store, returning the bearer token everything else
     * needs.
     *
     * Defaults to the seeded admin at the seeded coffee shop, which is what
     * `deviceToken()` used to mean in each of these files.
     */
    protected function staffToken(
        string $identifier = 'admin',
        string $password = 'password',
        string $storeCode = 'main',
    ): string {
        // Whatever token the test was carrying, it is not the one signing in
        // now — `withToken` sets a default header for every later request.
        $this->withoutToken();

        $signIn = $this->postJson('/api/staff/sign-in', [
            'identifier' => $identifier,
            'password' => $password,
        ])->assertOk();

        $stores = collect($signIn->json('stores'));
        $store = $stores->firstWhere('code', $storeCode) ?? $stores->first();

        $token = $this->withToken($signIn->json('token'))
            ->postJson('/api/staff/session-store', ['storeId' => $store['id']])
            ->assertOk()
            ->json('token');

        // The pending token must not linger as a default header, or a call the
        // test means to make as a customer would carry it.
        $this->withoutToken();

        return $token;
    }

    /**
     * A whole second tenant with an owner of its own, for the cross-tenant
     * tests: one shop must never see another's orders, riders or catalog.
     *
     * Returns a token scoped to that shop.
     */
    protected function staffTokenForNewTenant(
        string $slug = 'rival-cafe',
        string $name = 'Rival Cafe',
        string $username = 'rival-owner',
    ): string {
        $organization = Organization::query()->create([
            'id' => (string) str()->uuid(),
            'name' => $name,
            'slug' => $slug,
            'status' => 'active',
        ]);

        $store = new Store([
            'organization_id' => $organization->id,
            'name' => $name,
            'code' => 'main',
            'business_mode' => 'coffee-shop',
            'status' => 'active',
        ]);
        $store->id = (string) str()->uuid();
        $store->save();

        $owner = User::query()->create([
            'name' => $name.' Owner',
            'username' => $username,
            'password' => 'password',
            'status' => 'active',
        ]);

        // An organization membership and no store row, deliberately: it is the
        // other of the two ways a person reaches a shop, and the one an owner
        // actually has.
        OrganizationMembership::query()->create([
            'organization_id' => $organization->id,
            'user_id' => $owner->id,
            'membership_role' => 'admin',
        ]);

        return $this->staffToken($username, 'password');
    }
}
