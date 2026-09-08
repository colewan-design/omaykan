<?php

namespace Tests\Feature\Api;

use App\Models\CustomerAccount;
use App\Models\Organization;
use App\Models\Store;
use App\Models\StoreMembership;
use App\Models\User;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Tests\Concerns\SignsInStaff;
use Tests\TestCase;

/**
 * Staff sign-in, after pairing was retired.
 *
 * The shape that matters here is the two steps: proving who you are does not
 * yet say which shop you are acting for, and the token from the first step must
 * not reach anything that needs the second.
 */
class StaffAuthApiTest extends TestCase
{
    use SignsInStaff, RefreshDatabase;

    public function test_seeded_admin_signs_in_and_is_offered_their_store(): void
    {
        $this->seed();

        $this->postJson('/api/staff/sign-in', [
            'identifier' => 'admin',
            'password' => 'password',
        ])
            ->assertOk()
            ->assertJsonPath('user.username', 'admin')
            ->assertJsonPath('stores.0.code', 'main')
            ->assertJsonPath('stores.0.role', 'admin');
    }

    /**
     * `staff-register` created accounts from a username and no email, and those
     * people still have to get in.
     */
    public function test_an_account_with_no_email_signs_in_by_username(): void
    {
        $this->seed();
        $store = Store::query()->firstOrFail();

        $user = User::query()->create([
            'name' => 'Cashier One',
            'username' => 'cashier1',
            'password' => 'secret1',
            'status' => 'active',
        ]);
        StoreMembership::query()->create([
            'store_id' => $store->id,
            'user_id' => $user->id,
            'membership_role' => 'cashier',
        ]);

        $this->postJson('/api/staff/sign-in', [
            'identifier' => 'cashier1',
            'password' => 'secret1',
        ])
            ->assertOk()
            ->assertJsonPath('stores.0.id', $store->id);
    }

    public function test_an_account_can_sign_in_by_email_as_well_as_username(): void
    {
        $this->seed();

        $this->postJson('/api/staff/sign-in', [
            'identifier' => 'admin@example.com',
            'password' => 'password',
        ])->assertOk();
    }

    public function test_a_wrong_password_and_an_unknown_account_are_told_apart_by_nobody(): void
    {
        $this->seed();

        $wrong = $this->postJson('/api/staff/sign-in', [
            'identifier' => 'admin',
            'password' => 'not-the-password',
        ])->assertStatus(422);

        $missing = $this->postJson('/api/staff/sign-in', [
            'identifier' => 'nobody-at-all',
            'password' => 'not-the-password',
        ])->assertStatus(422);

        $this->assertSame(
            $wrong->json('errors.identifier'),
            $missing->json('errors.identifier'),
        );
    }

    public function test_an_account_with_no_membership_anywhere_is_refused(): void
    {
        $this->seed();

        User::query()->create([
            'name' => 'Nobody',
            'username' => 'nobody',
            'password' => 'secret1',
            'status' => 'active',
        ]);

        $this->postJson('/api/staff/sign-in', [
            'identifier' => 'nobody',
            'password' => 'secret1',
        ])->assertForbidden();
    }

    public function test_the_sign_in_token_alone_cannot_reach_the_merchant_api(): void
    {
        $this->seed();

        $token = $this->postJson('/api/staff/sign-in', [
            'identifier' => 'admin',
            'password' => 'password',
        ])->assertOk()->json('token');

        // It names no store, so there is nothing for the seller endpoints to be
        // scoped to — and they say so rather than guessing.
        $this->withToken($token)
            ->getJson('/api/seller/online-orders')
            ->assertForbidden();

        // The store picker is the one thing it does reach.
        $this->withToken($token)
            ->getJson('/api/staff/stores')
            ->assertOk()
            ->assertJsonPath('stores.0.code', 'main');
    }

    public function test_choosing_a_store_mints_a_token_that_works(): void
    {
        $this->seed();

        $this->withToken($this->staffToken())
            ->getJson('/api/seller/online-orders')
            ->assertOk();
    }

    public function test_a_store_the_account_has_no_membership_for_is_refused(): void
    {
        $this->seed();

        $otherOrg = Organization::query()->create([
            'id' => (string) str()->uuid(),
            'name' => 'Rival Cafe',
            'slug' => 'rival-cafe',
            'status' => 'active',
        ]);
        $otherStore = new Store([
            'organization_id' => $otherOrg->id,
            'name' => 'Rival Cafe',
            'code' => 'main',
            'business_mode' => 'coffee-shop',
            'status' => 'active',
        ]);
        $otherStore->id = (string) str()->uuid();
        $otherStore->save();

        $token = $this->postJson('/api/staff/sign-in', [
            'identifier' => 'admin',
            'password' => 'password',
        ])->assertOk()->json('token');

        $this->withToken($token)
            ->postJson('/api/staff/session-store', ['storeId' => $otherStore->id])
            ->assertForbidden();
    }

    /**
     * The revocation the device era did not have: a lost phone meant rotating
     * the shop's code and re-pairing every till. Dropping the membership is
     * enough now, and it takes effect on the next request rather than at the
     * next sign-in.
     */
    public function test_revoking_a_membership_kills_a_live_session(): void
    {
        $this->seed();
        $token = $this->staffToken();

        $this->withToken($token)->getJson('/api/seller/online-orders')->assertOk();

        $admin = User::query()->where('username', 'admin')->firstOrFail();
        StoreMembership::query()->where('user_id', $admin->id)->delete();
        $admin->organizationMemberships()->delete();

        $this->withToken($token)->getJson('/api/seller/online-orders')->assertForbidden();
    }

    public function test_signing_out_retires_the_token_it_arrived_on(): void
    {
        $this->seed();
        $token = $this->staffToken();

        $this->withToken($token)->postJson('/api/staff/sign-out')->assertOk();

        $this->withToken($token)->getJson('/api/seller/online-orders')->assertUnauthorized();
    }

    public function test_a_shoppers_token_cannot_reach_the_merchant_api(): void
    {
        $this->seed();

        // Minted directly: registering leaves the shopper unverified and
        // deliberately hands back no token, and what is under test here is what
        // a portal token can reach, not how one is obtained.
        $shopper = CustomerAccount::query()->create([
            'name' => 'Maria Santos',
            'email' => 'maria@example.com',
            'password' => 'secret123',
        ]);
        $customerToken = $shopper->createToken('portal', ['customer'])->plainTextToken;

        $this->withToken($customerToken)
            ->getJson('/api/seller/online-orders')
            ->assertForbidden();
    }
}
