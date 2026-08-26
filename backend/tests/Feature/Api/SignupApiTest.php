<?php

namespace Tests\Feature\Api;

use App\Models\Organization;
use App\Models\OrganizationMembership;
use App\Models\Store;
use App\Models\Subscription;
use App\Models\User;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Tests\TestCase;

/**
 * Covers the replacement for api/signup.ts.
 */
class SignupApiTest extends TestCase
{
    use RefreshDatabase;

    private function payload(array $overrides = []): array
    {
        $merged = array_merge([
            'businessName' => 'Hill Station Cafe',
            'ownerFullName' => 'Ana Reyes',
            'username' => 'AnaReyes',
            'password' => 'secret123',
            'businessMode' => 'coffee-shop',
            'gcashReference' => 'GC-99881',
        ], $overrides);

        // Derived from the username unless a case explicitly sets one, so that
        // the tests signing up a *second* merchant don't collide on the unique
        // email index and fail for a reason they aren't about.
        return $merged + ['email' => strtolower($merged['username']).'@example.test'];
    }

    public function test_signup_creates_a_working_store_in_one_shot(): void
    {
        $response = $this->postJson('/api/signup', $this->payload())->assertCreated();

        $response->assertJsonStructure(['organizationSlug', 'storeCode', 'pairingCode']);
        $this->assertSame('hill-station-cafe', $response->json('organizationSlug'));
        $this->assertSame('main', $response->json('storeCode'));
        $this->assertSame(6, strlen($response->json('pairingCode')));

        $organization = Organization::query()->where('slug', 'hill-station-cafe')->firstOrFail();
        $store = Store::query()->where('organization_id', $organization->id)->firstOrFail();

        $this->assertSame('coffee-shop', $store->business_mode);
        $this->assertFalse($organization->suspended);

        $owner = User::query()->where('username', 'anareyes')->firstOrFail();
        $this->assertDatabaseHas('organization_memberships', [
            'organization_id' => $organization->id,
            'user_id' => $owner->id,
            'membership_role' => 'admin',
        ]);
        $this->assertDatabaseHas('store_memberships', [
            'store_id' => $store->id,
            'user_id' => $owner->id,
            'membership_role' => 'admin',
        ]);

        $subscription = Subscription::query()->where('organization_id', $organization->id)->firstOrFail();
        $this->assertSame(Subscription::STATUS_PENDING, $subscription->status);
        $this->assertSame(49900, $subscription->amount_cents);
        $this->assertSame('GC-99881', $subscription->gcash_reference);
        $this->assertNull($subscription->verified_at);
    }

    public function test_the_new_owner_can_immediately_sign_in_and_the_store_is_discoverable(): void
    {
        $created = $this->postJson('/api/signup', $this->payload())->assertCreated()->json();

        // The code handed back is the one customers type.
        $this->postJson('/api/store-codes/resolve', ['code' => $created['pairingCode']])
            ->assertOk()
            ->assertJsonPath('orgSlug', $created['organizationSlug'])
            ->assertJsonPath('storeName', 'Hill Station Cafe');

        $this->postJson('/api/staff-sessions', [
            'organizationSlug' => $created['organizationSlug'],
            'storeCode' => 'main',
            'username' => 'anareyes',
            'password' => 'secret123',
        ])->assertOk()->assertJsonPath('user.roleId', 'admin');
    }

    public function test_a_till_can_pair_with_the_issued_code(): void
    {
        $created = $this->postJson('/api/signup', $this->payload())->assertCreated()->json();

        $this->postJson('/api/device-sessions', [
            'organizationSlug' => $created['organizationSlug'],
            'storeCode' => 'main',
            'pairingCode' => $created['pairingCode'],
            'deviceName' => 'Counter 1',
            'platform' => 'web',
            'appVersion' => '0.1.0',
        ])->assertOk();
    }

    public function test_duplicate_business_names_get_distinct_slugs(): void
    {
        $first = $this->postJson('/api/signup', $this->payload())->assertCreated()->json();
        $second = $this->postJson('/api/signup', $this->payload(['username' => 'someoneelse']))
            ->assertCreated()->json();

        $this->assertSame('hill-station-cafe', $first['organizationSlug']);
        $this->assertSame('hill-station-cafe-2', $second['organizationSlug']);
        $this->assertNotSame($first['pairingCode'], $second['pairingCode']);
    }

    public function test_taken_username_is_rejected(): void
    {
        $this->postJson('/api/signup', $this->payload())->assertCreated();

        $this->postJson('/api/signup', $this->payload(['businessName' => 'Another Cafe']))
            ->assertStatus(422)
            ->assertJsonValidationErrors('username');
    }

    public function test_username_is_matched_case_insensitively(): void
    {
        $this->postJson('/api/signup', $this->payload())->assertCreated();

        $this->postJson('/api/signup', $this->payload([
            'businessName' => 'Another Cafe',
            'username' => 'ANAREYES',
        ]))->assertStatus(422)->assertJsonValidationErrors('username');
    }

    public function test_short_password_and_bad_business_mode_are_rejected(): void
    {
        $this->postJson('/api/signup', $this->payload(['password' => 'abc']))
            ->assertStatus(422)
            ->assertJsonValidationErrors('password');

        $this->postJson('/api/signup', $this->payload(['businessMode' => 'laundromat']))
            ->assertStatus(422)
            ->assertJsonValidationErrors('businessMode');
    }

    public function test_the_form_can_sign_up_without_a_payment_reference(): void
    {
        // What the signup form now sends: early access is free, so it collects
        // no GCash reference. The subscription is still created, pending, with
        // nothing yet to verify.
        $payload = $this->payload();
        unset($payload['gcashReference']);

        $response = $this->postJson('/api/signup', $payload)->assertCreated();

        $organization = Organization::query()->where('slug', $response->json('organizationSlug'))->firstOrFail();
        $subscription = Subscription::query()->where('organization_id', $organization->id)->firstOrFail();

        $this->assertSame(Subscription::STATUS_PENDING, $subscription->status);
        $this->assertSame('', $subscription->gcash_reference);
    }

    public function test_a_failed_signup_leaves_nothing_behind(): void
    {
        $this->postJson('/api/signup', $this->payload(['businessName' => '']))
            ->assertStatus(422);

        // The Firestore version created the auth user before the batch write,
        // so a failure orphaned an account. One transaction means none of it
        // lands.
        $this->assertDatabaseCount('organizations', 0);
        $this->assertDatabaseCount('users', 0);
        $this->assertDatabaseCount('subscriptions', 0);
    }

    public function test_a_nail_salon_can_sign_up_but_is_not_discoverable_by_customers(): void
    {
        $created = $this->postJson('/api/signup', $this->payload(['businessMode' => 'nail-salon']))
            ->assertCreated()->json();

        // Staff binding works...
        $this->postJson('/api/store-codes/resolve-staff', ['code' => $created['pairingCode']])
            ->assertOk();

        // ...but there is nothing to put in a cart.
        $this->postJson('/api/store-codes/resolve', ['code' => $created['pairingCode']])
            ->assertStatus(409);
    }
}
