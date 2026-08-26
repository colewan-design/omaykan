<?php

namespace Tests\Feature\Api\Platform;

use App\Models\PlatformAdmin;
use App\Models\PlatformAuditLog;

/**
 * Managing the operators themselves — owner-only, all of it.
 *
 * The interesting cases are the two ways an owner could lock the platform out
 * of its own portal: disabling themselves, and disabling the last owner.
 * Recovering from either needs SSH, so neither is allowed.
 */
class PlatformAdminManagementApiTest extends PlatformTestCase
{
    public function test_an_owner_can_list_operators(): void
    {
        $owner = $this->operator();
        $this->operator(role: PlatformAdmin::ROLE_OPERATOR, email: 'queue@omaykan.test');

        $this->getJson('/api/platform/admins', $this->authHeader($this->tokenFor($owner)))
            ->assertOk()
            ->assertJsonCount(2, 'admins');
    }

    public function test_an_operator_cannot_manage_operators(): void
    {
        $operator = $this->operator(role: PlatformAdmin::ROLE_OPERATOR, email: 'queue@omaykan.test');
        $headers = $this->authHeader($this->tokenFor($operator));

        $this->getJson('/api/platform/admins', $headers)->assertStatus(403);

        $this->postJson('/api/platform/admins', [
            'name' => 'Sneaky',
            'email' => 'sneaky@omaykan.test',
            'role' => PlatformAdmin::ROLE_OWNER,
        ], $headers)->assertStatus(403);

        $this->postJson(
            "/api/platform/admins/{$operator->id}/status",
            ['status' => PlatformAdmin::STATUS_DISABLED],
            $headers,
        )->assertStatus(403);

        $this->assertDatabaseCount('platform_admins', 1);
    }

    public function test_creating_an_operator_reveals_the_password_once(): void
    {
        $token = $this->tokenFor($this->operator());

        $response = $this->postJson('/api/platform/admins', [
            'name' => 'Queue Worker',
            'email' => 'Queue@Omaykan.test',
            'role' => PlatformAdmin::ROLE_OPERATOR,
        ], $this->authHeader($token))->assertCreated();

        $password = $response->json('password');
        $this->assertSame(12, strlen($password));
        $response->assertJsonPath('admin.email', 'queue@omaykan.test')
            ->assertJsonPath('admin.role', PlatformAdmin::ROLE_OPERATOR);

        // It works, and nothing stored the plaintext.
        $this->postJson('/api/platform/login', [
            'email' => 'queue@omaykan.test',
            'password' => $password,
        ])->assertOk();

        $log = PlatformAuditLog::query()->where('action', 'operator.created')->sole();
        $this->assertStringNotContainsString($password, json_encode($log->context));
    }

    public function test_a_duplicate_email_is_rejected(): void
    {
        $token = $this->tokenFor($this->operator());

        $this->postJson('/api/platform/admins', [
            'name' => 'Another Ops',
            'email' => 'ops@omaykan.test',
            'role' => PlatformAdmin::ROLE_OPERATOR,
        ], $this->authHeader($token))->assertStatus(422)->assertJsonValidationErrors('email');
    }

    public function test_disabling_an_operator_revokes_their_tokens_immediately(): void
    {
        $ownerToken = $this->tokenFor($this->operator());
        $operator = $this->operator(role: PlatformAdmin::ROLE_OPERATOR, email: 'queue@omaykan.test');
        $operatorToken = $this->tokenFor($operator);

        $this->getJson('/api/platform/me', $this->authHeader($operatorToken))->assertOk();

        $this->postJson(
            "/api/platform/admins/{$operator->id}/status",
            ['status' => PlatformAdmin::STATUS_DISABLED],
            $this->authHeader($ownerToken),
        )->assertOk()->assertJsonPath('admin.status', PlatformAdmin::STATUS_DISABLED);

        $this->assertSame(0, $operator->tokens()->count());
        $this->getJson('/api/platform/me', $this->authHeader($operatorToken))->assertStatus(401);

        // The account stays, so its audit rows keep pointing at a real name.
        $this->assertDatabaseHas('platform_admins', ['email' => 'queue@omaykan.test']);

        $this->postJson(
            "/api/platform/admins/{$operator->id}/status",
            ['status' => PlatformAdmin::STATUS_ACTIVE],
            $this->authHeader($ownerToken),
        )->assertOk()->assertJsonPath('admin.status', PlatformAdmin::STATUS_ACTIVE);
    }

    public function test_an_owner_cannot_disable_themselves(): void
    {
        $owner = $this->operator();

        $this->postJson(
            "/api/platform/admins/{$owner->id}/status",
            ['status' => PlatformAdmin::STATUS_DISABLED],
            $this->authHeader($this->tokenFor($owner)),
        )->assertStatus(422)->assertJsonValidationErrors('status');

        $this->assertTrue($owner->fresh()->isActive());
    }

    public function test_the_last_active_owner_cannot_be_disabled(): void
    {
        $first = $this->operator();
        $second = $this->operator(email: 'ops2@omaykan.test');

        // Two owners: disabling one is fine.
        $this->postJson(
            "/api/platform/admins/{$second->id}/status",
            ['status' => PlatformAdmin::STATUS_DISABLED],
            $this->authHeader($this->tokenFor($first)),
        )->assertOk();

        // One left, and it is the caller — refused twice over.
        $this->postJson(
            "/api/platform/admins/{$first->id}/status",
            ['status' => PlatformAdmin::STATUS_DISABLED],
            $this->authHeader($this->tokenFor($first)),
        )->assertStatus(422);

        $this->assertTrue($first->fresh()->isActive());
    }
}
