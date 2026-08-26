<?php

namespace Tests\Feature\Api;

use App\Models\Organization;
use App\Models\Store;
use App\Models\User;
use App\Notifications\SellerEmailVerification;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Illuminate\Support\Facades\Notification;
use Tests\TestCase;

class StaffAuthApiTest extends TestCase
{
    use RefreshDatabase;

    public function test_seeded_admin_can_log_in_through_staff_session_api(): void
    {
        $this->seed();

        $this->postJson('/api/staff-sessions', [
            'organizationSlug' => 'demo-coffee',
            'storeCode' => 'main',
            'username' => 'admin',
            'password' => 'password',
        ])
            ->assertOk()
            ->assertJsonPath('user.username', 'admin')
            ->assertJsonPath('user.roleId', 'admin')
            ->assertJsonPath('session.authSource', 'remote');
    }

    public function test_register_creates_a_remote_staff_account_for_the_store(): void
    {
        $this->seed();
        Notification::fake();

        $response = $this->postJson('/api/staff-register', [
            'organizationSlug' => 'demo-coffee',
            'storeCode' => 'main',
            'fullName' => 'Cashier One',
            'username' => 'cashier1',
            'email' => 'cashier1@example.com',
            'password' => 'secret',
        ])->assertCreated();

        $this->assertDatabaseHas('users', [
            'username' => 'cashier1',
            'name' => 'Cashier One',
            'email' => 'cashier1@example.com',
        ]);

        $userId = $response->json('user.id');
        $organization = Organization::query()->where('slug', 'demo-coffee')->firstOrFail();
        $store = Store::query()->where('organization_id', $organization->id)->where('code', 'main')->firstOrFail();

        $this->assertDatabaseHas('organization_memberships', [
            'organization_id' => $organization->id,
            'user_id' => $userId,
        ]);

        $this->assertDatabaseHas('store_memberships', [
            'store_id' => $store->id,
            'user_id' => $userId,
        ]);
    }

    // -- Email verification -------------------------------------------------

    public function test_registering_sends_a_verification_link_and_no_session(): void
    {
        $this->seed();
        Notification::fake();

        $this->postJson('/api/staff-register', [
            'organizationSlug' => 'demo-coffee',
            'storeCode' => 'main',
            'fullName' => 'Cashier One',
            'username' => 'cashier1',
            'email' => 'cashier1@example.com',
            'password' => 'secret',
        ])
            ->assertCreated()
            ->assertJsonPath('verificationRequired', true)
            // The account exists but cannot be used yet. Handing back a token
            // here would make verification decorative for that first session.
            ->assertJsonMissingPath('session');

        Notification::assertSentTo(
            User::query()->where('username', 'cashier1')->firstOrFail(),
            SellerEmailVerification::class,
        );
    }

    public function test_email_is_required_to_register(): void
    {
        $this->seed();

        $this->postJson('/api/staff-register', [
            'organizationSlug' => 'demo-coffee',
            'storeCode' => 'main',
            'fullName' => 'Cashier One',
            'username' => 'cashier1',
            'password' => 'secret',
        ])
            ->assertStatus(422)
            ->assertJsonValidationErrors('email');

        $this->assertDatabaseMissing('users', ['username' => 'cashier1']);
    }

    /**
     * Compared against the lowercased stored value, so a differently-cased
     * address cannot claim a second account on the same mailbox.
     */
    public function test_an_email_already_in_use_is_rejected_whatever_its_case(): void
    {
        $this->seed();
        Notification::fake();

        $this->postJson('/api/staff-register', [
            'organizationSlug' => 'demo-coffee',
            'storeCode' => 'main',
            'fullName' => 'Cashier One',
            'username' => 'cashier1',
            'email' => 'cashier1@example.com',
            'password' => 'secret',
        ])->assertCreated();

        $this->postJson('/api/staff-register', [
            'organizationSlug' => 'demo-coffee',
            'storeCode' => 'main',
            'fullName' => 'Cashier Two',
            'username' => 'cashier2',
            'email' => 'Cashier1@Example.com',
            'password' => 'secret',
        ])->assertStatus(422);

        $this->assertDatabaseMissing('users', ['username' => 'cashier2']);
    }

    public function test_a_newly_registered_account_cannot_sign_in_until_it_is_verified(): void
    {
        $this->seed();
        Notification::fake();

        $this->postJson('/api/staff-register', [
            'organizationSlug' => 'demo-coffee',
            'storeCode' => 'main',
            'fullName' => 'Cashier One',
            'username' => 'cashier1',
            'email' => 'cashier1@example.com',
            'password' => 'secret',
        ])->assertCreated();

        $this->postJson('/api/staff-sessions', [
            'organizationSlug' => 'demo-coffee',
            'storeCode' => 'main',
            'username' => 'cashier1',
            'password' => 'secret',
        ])->assertForbidden();

        // Refused sign-ins resend the link rather than stranding someone who
        // deleted the first mail — twice now, once per attempt.
        Notification::assertSentToTimes(
            User::query()->where('username', 'cashier1')->firstOrFail(),
            SellerEmailVerification::class,
            2,
        );
    }

    public function test_verifying_the_address_lets_the_account_sign_in(): void
    {
        $this->seed();
        Notification::fake();

        $this->postJson('/api/staff-register', [
            'organizationSlug' => 'demo-coffee',
            'storeCode' => 'main',
            'fullName' => 'Cashier One',
            'username' => 'cashier1',
            'email' => 'cashier1@example.com',
            'password' => 'secret',
        ])->assertCreated();

        User::query()->where('username', 'cashier1')->firstOrFail()->markEmailAsVerified();

        $this->postJson('/api/staff-sessions', [
            'organizationSlug' => 'demo-coffee',
            'storeCode' => 'main',
            'username' => 'cashier1',
            'password' => 'secret',
        ])
            ->assertOk()
            ->assertJsonPath('user.username', 'cashier1')
            ->assertJsonPath('session.authSource', 'remote');
    }
}
