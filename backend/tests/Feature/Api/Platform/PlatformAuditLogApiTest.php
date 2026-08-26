<?php

namespace Tests\Feature\Api\Platform;

use App\Models\PlatformAdmin;
use App\Models\PlatformAuditLog;
use App\Models\Subscription;
use App\Models\User;

/**
 * The record, and reading it.
 *
 * The property that matters: every mutation writes exactly one row naming the
 * operator who made it. That is the whole reason named accounts replaced the
 * shared secret, so it is asserted per action rather than in general.
 */
class PlatformAuditLogApiTest extends PlatformTestCase
{
    public function test_every_mutation_writes_exactly_one_row_attributed_to_its_actor(): void
    {
        $slug = $this->signUpTenant()['organizationSlug'];
        $owner = User::query()->where('username', 'anareyes')->firstOrFail();
        $admin = $this->operator();
        $token = $this->tokenFor($admin);
        $headers = $this->authHeader($token);

        // Signing in is itself recorded, so start from a known count rather
        // than from zero.
        $before = PlatformAuditLog::query()->count();

        $this->postJson("/api/platform/organizations/{$slug}/subscription",
            ['status' => Subscription::STATUS_ACTIVE], $headers)->assertOk();
        $this->postJson("/api/platform/organizations/{$slug}/suspension",
            ['suspended' => true], $headers)->assertOk();
        $this->postJson("/api/platform/organizations/{$slug}/suspension",
            ['suspended' => false], $headers)->assertOk();
        $this->postJson("/api/platform/organizations/{$slug}/owners/{$owner->id}/password-reset",
            [], $headers)->assertOk();
        $this->postJson("/api/platform/organizations/{$slug}/owners/{$owner->id}/status",
            ['disabled' => true], $headers)->assertOk();

        $logs = PlatformAuditLog::query()->orderBy('created_at')->get()->skip($before)->values();

        $this->assertSame([
            'subscription.verified',
            'organization.suspended',
            'organization.reactivated',
            'owner.password_reset',
            'owner.login_disabled',
        ], $logs->pluck('action')->all());

        foreach ($logs as $log) {
            $this->assertSame($admin->id, $log->platform_admin_id);
            $this->assertSame('ops@omaykan.test', $log->actor_email);
            $this->assertSame('Organization', $log->subject_type);
            $this->assertSame($slug, $log->subject_id);
        }
    }

    public function test_reading_is_filterable_by_actor_action_and_subject(): void
    {
        $slug = $this->signUpTenant()['organizationSlug'];
        $other = $this->signUpTenant(['businessName' => 'Other Cafe', 'username' => 'otherowner']);

        $ownerToken = $this->tokenFor($this->operator());
        $operatorToken = $this->tokenFor($this->operator(
            role: PlatformAdmin::ROLE_OPERATOR,
            email: 'queue@omaykan.test',
        ));

        $this->postJson("/api/platform/organizations/{$slug}/suspension",
            ['suspended' => true], $this->authHeader($ownerToken))->assertOk();
        $this->postJson("/api/platform/organizations/{$other['organizationSlug']}/subscription",
            ['status' => Subscription::STATUS_ACTIVE], $this->authHeader($operatorToken))->assertOk();

        $this->getJson('/api/platform/audit-logs?actor=queue', $this->authHeader($ownerToken))
            ->assertOk()
            ->assertJsonPath('total', 1)
            ->assertJsonPath('logs.0.action', 'subscription.verified')
            ->assertJsonPath('logs.0.actorEmail', 'queue@omaykan.test');

        // Prefix, so one filter covers suspended, reactivated and deleted.
        $this->getJson('/api/platform/audit-logs?action=organization', $this->authHeader($ownerToken))
            ->assertOk()
            ->assertJsonPath('total', 1)
            ->assertJsonPath('logs.0.action', 'organization.suspended');

        $this->getJson("/api/platform/audit-logs?subject={$slug}", $this->authHeader($ownerToken))
            ->assertOk()
            ->assertJsonPath('total', 1)
            ->assertJsonPath('logs.0.subjectId', $slug);

        // The filter vocabulary comes from the data, not a hardcoded list.
        $this->getJson('/api/platform/audit-logs', $this->authHeader($ownerToken))
            ->assertOk()
            ->assertJsonCount(2, 'actors')
            ->assertJsonFragment(['actions' => ['organization.suspended', 'subscription.verified']]);
    }

    public function test_newest_first(): void
    {
        $slug = $this->signUpTenant()['organizationSlug'];
        $token = $this->tokenFor($this->operator());
        $headers = $this->authHeader($token);

        $this->postJson("/api/platform/organizations/{$slug}/suspension",
            ['suspended' => true], $headers)->assertOk();
        $this->postJson("/api/platform/organizations/{$slug}/suspension",
            ['suspended' => false], $headers)->assertOk();

        $this->getJson('/api/platform/audit-logs?action=organization', $headers)
            ->assertOk()
            ->assertJsonPath('logs.0.action', 'organization.reactivated');
    }

    /**
     * An operator can read every row, including an owner's. Mutual visibility
     * is the point — this replaced a credential nobody could be held to.
     */
    public function test_an_operator_can_read_the_whole_log(): void
    {
        $slug = $this->signUpTenant()['organizationSlug'];

        $this->postJson("/api/platform/organizations/{$slug}/suspension",
            ['suspended' => true], $this->authHeader($this->tokenFor($this->operator())))->assertOk();

        $operatorToken = $this->tokenFor($this->operator(
            role: PlatformAdmin::ROLE_OPERATOR,
            email: 'queue@omaykan.test',
        ));

        $this->getJson('/api/platform/audit-logs?action=organization', $this->authHeader($operatorToken))
            ->assertOk()
            ->assertJsonPath('total', 1)
            ->assertJsonPath('logs.0.actorEmail', 'ops@omaykan.test');
    }

    /** There is no write path. An editable audit trail is not one. */
    public function test_the_log_is_read_only(): void
    {
        $token = $this->tokenFor($this->operator());

        $this->postJson('/api/platform/audit-logs', [], $this->authHeader($token))
            ->assertStatus(405);
        $this->deleteJson('/api/platform/audit-logs', [], $this->authHeader($token))
            ->assertStatus(405);
    }
}
