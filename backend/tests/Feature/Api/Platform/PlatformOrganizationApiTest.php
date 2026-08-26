<?php

namespace Tests\Feature\Api\Platform;

use App\Models\Organization;
use App\Models\PlatformAdmin;
use App\Models\PlatformAuditLog;
use App\Models\Subscription;
use App\Models\User;

/**
 * Tenant management: the verification queue, the suspend switch, owner logins,
 * and deletion.
 *
 * Carries over what PlatformAdminApiTest covered — the behaviour did not
 * change, only the way it is addressed and who is allowed to do it — plus the
 * two properties that are new: every mutation writes exactly one audit row,
 * and deletion is owner-only.
 */
class PlatformOrganizationApiTest extends PlatformTestCase
{
    public function test_the_list_returns_store_subscription_and_owners(): void
    {
        $created = $this->signUpTenant();
        $token = $this->tokenFor($this->operator());

        $this->getJson('/api/platform/organizations', $this->authHeader($token))
            ->assertOk()
            ->assertJsonPath('total', 1)
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

    public function test_the_list_can_be_searched_and_filtered(): void
    {
        $this->signUpTenant();
        $other = $this->signUpTenant(['businessName' => 'Session Road Grocery', 'username' => 'grocer']);
        $token = $this->tokenFor($this->operator());

        $this->getJson('/api/platform/organizations?q=grocery', $this->authHeader($token))
            ->assertOk()
            ->assertJsonPath('total', 1)
            ->assertJsonPath('organizations.0.organizationName', 'Session Road Grocery');

        $this->postJson(
            "/api/platform/organizations/{$other['organizationSlug']}/suspension",
            ['suspended' => true],
            $this->authHeader($token),
        )->assertOk();

        $this->getJson('/api/platform/organizations?state=suspended', $this->authHeader($token))
            ->assertOk()
            ->assertJsonPath('total', 1)
            ->assertJsonPath('organizations.0.organizationSlug', $other['organizationSlug']);

        $this->getJson('/api/platform/organizations?state=active', $this->authHeader($token))
            ->assertOk()
            ->assertJsonPath('total', 1)
            ->assertJsonPath('organizations.0.organizationName', 'Hill Station Cafe');

        $this->getJson(
            '/api/platform/organizations?subscription='.Subscription::STATUS_PENDING,
            $this->authHeader($token),
        )->assertOk()->assertJsonPath('total', 2);
    }

    public function test_the_detail_view_carries_the_numbers_and_the_trail(): void
    {
        $slug = $this->signUpTenant()['organizationSlug'];
        $token = $this->tokenFor($this->operator());

        $this->postJson(
            "/api/platform/organizations/{$slug}/suspension",
            ['suspended' => true, 'reason' => 'Chargeback'],
            $this->authHeader($token),
        )->assertOk();

        $this->getJson("/api/platform/organizations/{$slug}", $this->authHeader($token))
            ->assertOk()
            ->assertJsonPath('organization.organizationSlug', $slug)
            ->assertJsonPath('organization.suspended', true)
            ->assertJsonPath('organization.staffCount', 1)
            ->assertJsonPath('organization.stores.0.pairingCode', fn ($code) => is_string($code))
            ->assertJsonPath('organization.last30Days.orders', 0)
            ->assertJsonPath('auditTrail.0.action', 'organization.suspended')
            ->assertJsonPath('auditTrail.0.actorEmail', 'ops@omaykan.test')
            ->assertJsonPath('auditTrail.0.context.reason', 'Chargeback');
    }

    public function test_an_unknown_tenant_is_a_404(): void
    {
        $token = $this->tokenFor($this->operator());

        $this->getJson('/api/platform/organizations/no-such-shop', $this->authHeader($token))
            ->assertNotFound();
    }

    public function test_verifying_and_rejecting_a_subscription(): void
    {
        $slug = $this->signUpTenant()['organizationSlug'];
        $token = $this->tokenFor($this->operator());

        $this->postJson(
            "/api/platform/organizations/{$slug}/subscription",
            ['status' => Subscription::STATUS_ACTIVE],
            $this->authHeader($token),
        )->assertOk()->assertJsonPath('status', Subscription::STATUS_ACTIVE);

        $subscription = Subscription::query()->firstOrFail();
        $this->assertNotNull($subscription->verified_at);

        $this->postJson(
            "/api/platform/organizations/{$slug}/subscription",
            ['status' => Subscription::STATUS_REJECTED, 'reason' => 'Reference not found'],
            $this->authHeader($token),
        )->assertOk()->assertJsonPath('status', Subscription::STATUS_REJECTED);

        $subscription->refresh();
        $this->assertNull($subscription->verified_at);
        $this->assertSame('Reference not found', $subscription->rejection_reason);

        $this->assertSame(
            ['subscription.verified', 'subscription.rejected'],
            PlatformAuditLog::query()->orderBy('created_at')->pluck('action')->all(),
        );
    }

    public function test_a_subscription_status_outside_the_two_is_rejected(): void
    {
        $slug = $this->signUpTenant()['organizationSlug'];
        $token = $this->tokenFor($this->operator());

        $this->postJson(
            "/api/platform/organizations/{$slug}/subscription",
            ['status' => Subscription::STATUS_PENDING],
            $this->authHeader($token),
        )->assertStatus(422)->assertJsonValidationErrors('status');
    }

    public function test_suspending_and_reactivating_a_tenant(): void
    {
        $slug = $this->signUpTenant()['organizationSlug'];
        $token = $this->tokenFor($this->operator());

        $this->postJson(
            "/api/platform/organizations/{$slug}/suspension",
            ['suspended' => true],
            $this->authHeader($token),
        )->assertOk()->assertJsonPath('suspended', true);

        $this->assertTrue(Organization::query()->where('slug', $slug)->firstOrFail()->suspended);

        $this->postJson(
            "/api/platform/organizations/{$slug}/suspension",
            ['suspended' => false],
            $this->authHeader($token),
        )->assertOk()->assertJsonPath('suspended', false);

        $this->assertSame(
            ['organization.suspended', 'organization.reactivated'],
            PlatformAuditLog::query()->orderBy('created_at')->pluck('action')->all(),
        );
    }

    public function test_resetting_an_owner_password_issues_a_working_one_and_kills_old_sessions(): void
    {
        $slug = $this->signUpTenant()['organizationSlug'];
        $owner = $this->verifyOwnerEmail();
        $token = $this->tokenFor($this->operator());

        // An existing session, which must not survive the reset.
        $this->postJson('/api/staff-sessions', [
            'organizationSlug' => $slug,
            'storeCode' => 'main',
            'username' => 'anareyes',
            'password' => 'secret123',
        ])->assertOk();
        $this->assertSame(1, $owner->tokens()->count());

        $password = $this->postJson(
            "/api/platform/organizations/{$slug}/owners/{$owner->id}/password-reset",
            [],
            $this->authHeader($token),
        )->assertOk()->json('password');

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

        $log = PlatformAuditLog::query()->sole();
        $this->assertSame('owner.password_reset', $log->action);
        $this->assertSame($slug, $log->subject_id);
        $this->assertSame('anareyes', $log->context['username']);
        // The point of the whole exercise: the plaintext is relayed, never kept.
        $this->assertStringNotContainsString($password, json_encode($log->context));
    }

    public function test_disabling_an_owner_blocks_sign_in_and_revokes_tokens(): void
    {
        $slug = $this->signUpTenant()['organizationSlug'];
        $owner = $this->verifyOwnerEmail();
        $token = $this->tokenFor($this->operator());

        $this->postJson('/api/staff-sessions', [
            'organizationSlug' => $slug,
            'storeCode' => 'main',
            'username' => 'anareyes',
            'password' => 'secret123',
        ])->assertOk();

        $this->postJson(
            "/api/platform/organizations/{$slug}/owners/{$owner->id}/status",
            ['disabled' => true],
            $this->authHeader($token),
        )->assertOk()->assertJsonPath('disabled', true);

        $this->assertSame(0, $owner->tokens()->count());

        $this->getJson('/api/platform/organizations', $this->authHeader($token))
            ->assertOk()
            ->assertJsonPath('organizations.0.admins.0.disabled', true);

        $this->postJson(
            "/api/platform/organizations/{$slug}/owners/{$owner->id}/status",
            ['disabled' => false],
            $this->authHeader($token),
        )->assertOk()->assertJsonPath('disabled', false);
    }

    public function test_an_owner_of_another_tenant_cannot_be_targeted(): void
    {
        $this->signUpTenant();
        $this->signUpTenant(['businessName' => 'Other Cafe', 'username' => 'otherowner']);

        $foreignOwner = User::query()->where('username', 'otherowner')->firstOrFail();
        $token = $this->tokenFor($this->operator());

        $this->postJson(
            "/api/platform/organizations/hill-station-cafe/owners/{$foreignOwner->id}/status",
            ['disabled' => true],
            $this->authHeader($token),
        )->assertNotFound();
    }

    public function test_deleting_a_tenant_requires_retyping_the_slug(): void
    {
        $slug = $this->signUpTenant()['organizationSlug'];
        $token = $this->tokenFor($this->operator());

        $this->deleteJson(
            "/api/platform/organizations/{$slug}",
            ['confirmSlug' => 'not-the-slug'],
            $this->authHeader($token),
        )->assertStatus(422)->assertJsonValidationErrors('confirmSlug');

        $this->assertDatabaseCount('organizations', 1);
    }

    /**
     * The reason the role column exists. Under the shared secret, anyone who
     * could work the verification queue could also destroy a merchant's entire
     * history.
     */
    public function test_an_operator_cannot_delete_a_tenant(): void
    {
        $slug = $this->signUpTenant()['organizationSlug'];
        $token = $this->tokenFor($this->operator(
            role: PlatformAdmin::ROLE_OPERATOR,
            email: 'queue@omaykan.test',
        ));

        $this->deleteJson(
            "/api/platform/organizations/{$slug}",
            ['confirmSlug' => $slug],
            $this->authHeader($token),
        )->assertStatus(403);

        $this->assertDatabaseCount('organizations', 1);
    }

    public function test_an_owner_deleting_a_tenant_removes_it_and_its_staff(): void
    {
        $slug = $this->signUpTenant()['organizationSlug'];
        $token = $this->tokenFor($this->operator());

        $this->deleteJson(
            "/api/platform/organizations/{$slug}",
            ['confirmSlug' => $slug],
            $this->authHeader($token),
        )->assertOk()->assertJsonPath('deleted', true);

        $this->assertDatabaseCount('organizations', 0);
        $this->assertDatabaseCount('subscriptions', 0);
        $this->assertDatabaseCount('stores', 0);
        $this->assertSame(0, User::query()->where('username', 'anareyes')->count());

        // The row has to outlive the tenant — it is keyed by slug, not by a
        // foreign key, precisely so that it survives this.
        $log = PlatformAuditLog::query()->where('action', 'organization.deleted')->sole();
        $this->assertSame($slug, $log->subject_id);
        $this->assertSame('Hill Station Cafe', $log->context['organizationName']);
        $this->assertSame(1, $log->context['deletedUsers']);
    }
}
