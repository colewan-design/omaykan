<?php

namespace Tests\Feature\Api;

use App\Models\Organization;
use App\Models\Subscription;
use App\Models\User;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Tests\TestCase;

/**
 * Covers the replacement for api/platform-admin.ts.
 */
class PlatformAdminApiTest extends TestCase
{
    use RefreshDatabase;

    private const SECRET = 'test-operator-secret';

    protected function setUp(): void
    {
        parent::setUp();

        config(['services.platform_admin.secret' => self::SECRET]);
    }

    /** Creates a real tenant the way a merchant would. */
    private function signUpTenant(array $overrides = []): array
    {
        $payload = array_merge([
            'businessName' => 'Hill Station Cafe',
            'ownerFullName' => 'Ana Reyes',
            'username' => 'anareyes',
            'password' => 'secret123',
            'businessMode' => 'coffee-shop',
            'gcashReference' => 'GC-99881',
        ], $overrides);

        // Derived from the username so that signing up a second tenant does
        // not collide on the unique email index.
        $payload += ['email' => strtolower($payload['username']).'@example.test'];

        return $this->postJson('/api/signup', $payload)->assertCreated()->json();
    }

    private function call_admin(array $body)
    {
        return $this->postJson('/api/platform-admin', array_merge(['secret' => self::SECRET], $body));
    }

    public function test_the_secret_is_required(): void
    {
        $this->postJson('/api/platform-admin', ['action' => 'listOrgs'])->assertStatus(401);
        $this->postJson('/api/platform-admin', ['action' => 'listOrgs', 'secret' => 'wrong'])->assertStatus(401);
    }

    public function test_an_unconfigured_secret_refuses_rather_than_opening_the_door(): void
    {
        config(['services.platform_admin.secret' => null]);

        // An empty configured secret must not be satisfiable by sending an
        // empty secret.
        $this->postJson('/api/platform-admin', ['action' => 'listOrgs', 'secret' => ''])
            ->assertStatus(500);
    }

    public function test_list_orgs_returns_store_subscription_and_admins(): void
    {
        $created = $this->signUpTenant();

        $response = $this->call_admin(['action' => 'listOrgs'])->assertOk();

        $response
            ->assertJsonPath('organizations.0.organizationSlug', $created['organizationSlug'])
            ->assertJsonPath('organizations.0.organizationName', 'Hill Station Cafe')
            ->assertJsonPath('organizations.0.suspended', false)
            ->assertJsonPath('organizations.0.store.businessMode', 'coffee-shop')
            ->assertJsonPath('organizations.0.store.pairingCode', $created['pairingCode'])
            ->assertJsonPath('organizations.0.subscription.status', Subscription::STATUS_PENDING)
            ->assertJsonPath('organizations.0.subscription.gcashReference', 'GC-99881')
            ->assertJsonPath('organizations.0.admins.0.username', 'anareyes')
            ->assertJsonPath('organizations.0.admins.0.disabled', false);
    }

    public function test_verifying_and_rejecting_a_subscription(): void
    {
        $created = $this->signUpTenant();
        $slug = $created['organizationSlug'];

        $this->call_admin(['action' => 'verify', 'organizationSlug' => $slug])
            ->assertOk()
            ->assertJsonPath('status', Subscription::STATUS_ACTIVE);

        $subscription = Subscription::query()->firstOrFail();
        $this->assertNotNull($subscription->verified_at);

        $this->call_admin([
            'action' => 'reject',
            'organizationSlug' => $slug,
            'reason' => 'Reference not found',
        ])->assertOk()->assertJsonPath('status', Subscription::STATUS_REJECTED);

        $subscription->refresh();
        $this->assertNull($subscription->verified_at);
        $this->assertSame('Reference not found', $subscription->rejection_reason);
    }

    public function test_suspending_and_reactivating_an_org(): void
    {
        $slug = $this->signUpTenant()['organizationSlug'];

        $this->call_admin(['action' => 'suspendOrg', 'organizationSlug' => $slug])
            ->assertOk()->assertJsonPath('suspended', true);

        $this->assertTrue(Organization::query()->where('slug', $slug)->firstOrFail()->suspended);

        $this->call_admin(['action' => 'reactivateOrg', 'organizationSlug' => $slug])
            ->assertOk()->assertJsonPath('suspended', false);
    }

    public function test_resetting_an_owner_password_issues_a_working_one_and_kills_old_sessions(): void
    {
        $created = $this->signUpTenant();
        $slug = $created['organizationSlug'];
        $owner = User::query()->where('username', 'anareyes')->firstOrFail();

        // An existing session, which must not survive the reset.
        $this->postJson('/api/staff-sessions', [
            'organizationSlug' => $slug,
            'storeCode' => 'main',
            'username' => 'anareyes',
            'password' => 'secret123',
        ])->assertOk();
        $this->assertSame(1, $owner->tokens()->count());

        $password = $this->call_admin([
            'action' => 'resetOwnerPassword',
            'organizationSlug' => $slug,
            'uid' => $owner->id,
        ])->assertOk()->json('password');

        $this->assertSame(12, strlen($password));
        $this->assertSame(0, $owner->tokens()->count());

        $this->postJson('/api/staff-sessions', [
            'organizationSlug' => $slug,
            'storeCode' => 'main',
            'username' => 'anareyes',
            'password' => 'secret123',
        ])->assertStatus(422);

        $this->postJson('/api/staff-sessions', [
            'organizationSlug' => $slug,
            'storeCode' => 'main',
            'username' => 'anareyes',
            'password' => $password,
        ])->assertOk();
    }

    public function test_disabling_an_owner_blocks_sign_in(): void
    {
        $created = $this->signUpTenant();
        $slug = $created['organizationSlug'];
        $owner = User::query()->where('username', 'anareyes')->firstOrFail();

        $this->call_admin([
            'action' => 'setOwnerDisabled',
            'organizationSlug' => $slug,
            'uid' => $owner->id,
            'disabled' => true,
        ])->assertOk()->assertJsonPath('disabled', true);

        $this->call_admin(['action' => 'listOrgs'])
            ->assertOk()
            ->assertJsonPath('organizations.0.admins.0.disabled', true);
    }

    public function test_an_owner_of_another_org_cannot_be_targeted(): void
    {
        $this->signUpTenant();
        $other = $this->signUpTenant(['businessName' => 'Other Cafe', 'username' => 'otherowner']);

        $foreignOwner = User::query()->where('username', 'otherowner')->firstOrFail();

        $this->call_admin([
            'action' => 'setOwnerDisabled',
            'organizationSlug' => 'hill-station-cafe',
            'uid' => $foreignOwner->id,
            'disabled' => true,
        ])->assertNotFound();
    }

    public function test_deleting_an_org_requires_retyping_the_slug(): void
    {
        $slug = $this->signUpTenant()['organizationSlug'];

        $this->call_admin([
            'action' => 'deleteOrg',
            'organizationSlug' => $slug,
            'confirmSlug' => 'not-the-slug',
        ])->assertStatus(422)->assertJsonValidationErrors('confirmSlug');

        $this->assertDatabaseCount('organizations', 1);
    }

    public function test_deleting_an_org_removes_it_and_its_owner(): void
    {
        $slug = $this->signUpTenant()['organizationSlug'];

        $this->call_admin([
            'action' => 'deleteOrg',
            'organizationSlug' => $slug,
            'confirmSlug' => $slug,
        ])->assertOk()->assertJsonPath('deleted', true);

        $this->assertDatabaseCount('organizations', 0);
        $this->assertDatabaseCount('subscriptions', 0);
        $this->assertDatabaseCount('stores', 0);
        $this->assertSame(0, User::query()->where('username', 'anareyes')->count());
    }

    public function test_unknown_action_is_rejected(): void
    {
        $this->call_admin(['action' => 'dropEverything'])
            ->assertStatus(422)
            ->assertJsonValidationErrors('action');
    }
}
