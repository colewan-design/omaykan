<?php

namespace Tests\Feature\Api\Platform;

use App\Models\CustomerAccount;
use App\Models\PlatformAdmin;
use App\Models\PlatformAuditLog;
use App\Models\Rider;

/**
 * Getting into the portal, and — more importantly — every way of not getting
 * in.
 *
 * The cross-guard tests are the point of this file. A platform token is the
 * most powerful credential on the system, so the property under test is that
 * the four identities are genuinely separate: no staff, shopper or rider token
 * may reach a `/api/platform/*` route, and an operator token may not reach a
 * seller one. That is asserted per guard rather than once, because each one
 * fails differently if the provider is misconfigured.
 */
class PlatformAuthApiTest extends PlatformTestCase
{
    public function test_an_operator_can_sign_in_and_read_themselves(): void
    {
        $admin = $this->operator();

        $response = $this->postJson('/api/platform/login', [
            'email' => 'ops@omaykan.test',
            'password' => 'operator-password-1',
        ])->assertOk();

        $response->assertJsonPath('admin.email', 'ops@omaykan.test')
            ->assertJsonPath('admin.role', PlatformAdmin::ROLE_OWNER);

        $token = $response->json('token');
        $this->assertNotEmpty($token);

        $this->getJson('/api/platform/me', $this->authHeader($token))
            ->assertOk()
            ->assertJsonPath('admin.email', 'ops@omaykan.test');

        $admin->refresh();
        $this->assertNotNull($admin->last_login_at);
    }

    public function test_signing_in_is_audited(): void
    {
        $this->operator();

        $this->postJson('/api/platform/login', [
            'email' => 'ops@omaykan.test',
            'password' => 'operator-password-1',
        ])->assertOk();

        $log = PlatformAuditLog::query()->sole();
        $this->assertSame('session.signed_in', $log->action);
        $this->assertSame('ops@omaykan.test', $log->actor_email);
    }

    public function test_the_email_is_case_insensitive(): void
    {
        $this->operator();

        $this->postJson('/api/platform/login', [
            'email' => 'OPS@Omaykan.test',
            'password' => 'operator-password-1',
        ])->assertOk();
    }

    public function test_a_wrong_password_and_an_unknown_account_are_indistinguishable(): void
    {
        $this->operator();

        $wrongPassword = $this->postJson('/api/platform/login', [
            'email' => 'ops@omaykan.test',
            'password' => 'not-the-password',
        ])->assertStatus(422);

        $noSuchAccount = $this->postJson('/api/platform/login', [
            'email' => 'nobody@omaykan.test',
            'password' => 'not-the-password',
        ])->assertStatus(422);

        $this->assertSame(
            $wrongPassword->json('errors.email'),
            $noSuchAccount->json('errors.email'),
        );
    }

    public function test_a_disabled_operator_cannot_sign_in(): void
    {
        $this->operator(status: PlatformAdmin::STATUS_DISABLED);

        $this->postJson('/api/platform/login', [
            'email' => 'ops@omaykan.test',
            'password' => 'operator-password-1',
        ])->assertStatus(422)->assertJsonValidationErrors('email');
    }

    /**
     * A live token has to stop working the moment the account is disabled —
     * this is what `platform.active` exists for, and the reason it is a
     * middleware on the whole group rather than a check per action.
     */
    public function test_a_live_token_stops_working_once_the_account_is_disabled(): void
    {
        $admin = $this->operator();
        $token = $this->tokenFor($admin);

        $this->getJson('/api/platform/me', $this->authHeader($token))->assertOk();

        $admin->forceFill(['status' => PlatformAdmin::STATUS_DISABLED])->save();

        $this->getJson('/api/platform/me', $this->authHeader($token))
            ->assertStatus(403)
            ->assertJsonPath('adminStatus', PlatformAdmin::STATUS_DISABLED);
    }

    public function test_no_token_is_refused(): void
    {
        $this->getJson('/api/platform/me')->assertStatus(401);
        $this->getJson('/api/platform/overview')->assertStatus(401);
        $this->getJson('/api/platform/organizations')->assertStatus(401);
        $this->getJson('/api/platform/audit-logs')->assertStatus(401);
        $this->getJson('/api/platform/riders')->assertStatus(401);
    }

    public function test_a_staff_token_cannot_reach_the_portal(): void
    {
        $slug = $this->signUpTenant()['organizationSlug'];
        $this->verifyOwnerEmail();

        $staffToken = $this->postJson('/api/staff-sessions', [
            'organizationSlug' => $slug,
            'storeCode' => 'main',
            'username' => 'anareyes',
            'password' => 'secret123',
            // Staff sign-in nests the token under `session`, not `token`.
        ])->assertOk()->json('session.authToken');

        foreach ($this->guardedRoutes() as $route) {
            $this->getJson($route, $this->authHeader($staffToken))->assertStatus(401);
        }
    }

    public function test_a_customer_token_cannot_reach_the_portal(): void
    {
        $customer = CustomerAccount::query()->create([
            'name' => 'Shopper',
            'email' => 'shopper@example.test',
            'password' => 'shopper-password-1',
        ]);

        $token = $customer->createToken('storefront', ['customer'])->plainTextToken;

        foreach ($this->guardedRoutes() as $route) {
            $this->getJson($route, $this->authHeader($token))->assertStatus(401);
        }
    }

    public function test_a_rider_token_cannot_reach_the_portal(): void
    {
        $rider = Rider::query()->create([
            'name' => 'Jun Dela Cruz',
            'email' => 'jun@example.test',
            'phone' => '0917 555 0101',
            'password' => 'ride-with-me-1',
            'license_number' => 'N01-23-456789',
            'plate_number' => 'NBC 1234',
            'license_image_path' => 'rider-documents/license.jpg',
            'plate_image_path' => 'rider-documents/plate.jpg',
            'status' => Rider::STATUS_APPROVED,
        ]);

        $token = $rider->createToken('rider-portal', ['rider'])->plainTextToken;

        foreach ($this->guardedRoutes() as $route) {
            $this->getJson($route, $this->authHeader($token))->assertStatus(401);
        }
    }

    /**
     * And the other direction: an operator is not staff.
     *
     * 403 rather than 401 here — `auth:sanctum` has no provider, so it accepts
     * any tokenable and `merchant.token` is what turns the operator away. See
     * EnsureMerchantToken.
     */
    public function test_an_operator_token_cannot_reach_a_merchant_route(): void
    {
        $this->signUpTenant();
        $token = $this->tokenFor($this->operator());

        $this->getJson('/api/sync/bootstrap', $this->authHeader($token))->assertStatus(403);
        $this->getJson('/api/user', $this->authHeader($token))->assertStatus(403);
    }

    public function test_signing_out_revokes_only_this_session(): void
    {
        $admin = $this->operator();
        $first = $this->tokenFor($admin);
        $second = $this->tokenFor($admin);

        $this->postJson('/api/platform/logout', [], $this->authHeader($first))
            ->assertOk()
            ->assertJsonPath('signedOut', true);

        $this->getJson('/api/platform/me', $this->authHeader($first))->assertStatus(401);
        $this->getJson('/api/platform/me', $this->authHeader($second))->assertOk();
    }

    /**
     * @return list<string>
     */
    private function guardedRoutes(): array
    {
        return [
            '/api/platform/me',
            '/api/platform/overview',
            '/api/platform/organizations',
            '/api/platform/riders',
            '/api/platform/audit-logs',
            '/api/platform/admins',
        ];
    }
}
