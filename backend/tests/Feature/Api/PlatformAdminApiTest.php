<?php

namespace Tests\Feature\Api;

use App\Mail\PlatformAdminReplyMail;
use App\Models\Organization;
use App\Models\PlatformAdmin;
use App\Models\Subscription;
use App\Models\User;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Illuminate\Support\Facades\Mail;
use Tests\TestCase;

/**
 * Covers the replacement for api/platform-admin.ts.
 */
class PlatformAdminApiTest extends TestCase
{
    use RefreshDatabase;

    private const OPERATOR_PASSWORD = 'operator-password-1234';

    private PlatformAdmin $operator;

    protected function setUp(): void
    {
        parent::setUp();

        $this->operator = PlatformAdmin::query()->create([
            'name' => 'Platform Operator',
            'email' => 'operator@example.test',
            'password' => self::OPERATOR_PASSWORD,
        ]);
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
        return $this->withToken($this->operatorToken())->postJson('/api/platform-admin', $body);
    }

    /** A real sign-in rather than actingAs, so the login path is exercised too. */
    private function operatorToken(): string
    {
        return $this->postJson('/api/platform-admin/login', [
            'email' => $this->operator->email,
            'password' => self::OPERATOR_PASSWORD,
        ])->assertOk()->json('token');
    }

    public function test_sign_in_is_required(): void
    {
        $this->postJson('/api/platform-admin', ['action' => 'listOrgs'])->assertStatus(401);

        $this->withToken('not-a-real-token')
            ->postJson('/api/platform-admin', ['action' => 'listOrgs'])
            ->assertStatus(401);
    }

    public function test_a_wrong_password_is_refused_without_saying_which_half_was_wrong(): void
    {
        $missing = $this->postJson('/api/platform-admin/login', [
            'email' => 'nobody@example.test',
            'password' => self::OPERATOR_PASSWORD,
        ])->assertStatus(422);

        $wrong = $this->postJson('/api/platform-admin/login', [
            'email' => $this->operator->email,
            'password' => 'not-the-password',
        ])->assertStatus(422);

        // The same message for "no such account" and "wrong password", so the
        // endpoint cannot be used to enumerate operators.
        $this->assertSame($missing->json('errors.email.0'), $wrong->json('errors.email.0'));
    }

    /**
     * The reason operator accounts exist at all: access can be taken away from
     * one person, immediately, without rotating anything for anyone else.
     */
    public function test_a_disabled_operator_cannot_use_a_token_it_already_holds(): void
    {
        $token = $this->operatorToken();

        $this->withToken($token)->postJson('/api/platform-admin', ['action' => 'listOrgs'])->assertOk();

        $this->operator->forceFill(['disabled_at' => now()])->save();

        // A test reuses one container across requests, so the guard still holds
        // the operator it resolved a moment ago. Production gets a fresh
        // container per request; this is what that looks like.
        $this->app['auth']->forgetGuards();

        $this->withToken($token)
            ->postJson('/api/platform-admin', ['action' => 'listOrgs'])
            ->assertStatus(403);

        $this->postJson('/api/platform-admin/login', [
            'email' => $this->operator->email,
            'password' => self::OPERATOR_PASSWORD,
        ])->assertStatus(403);
    }

    /**
     * The guard separation is the whole argument for a fourth table: a store
     * owner's token is a perfectly valid token, and it must not reach here.
     */
    public function test_a_staff_token_cannot_reach_the_operator_tools(): void
    {
        $this->signUpTenant();

        $owner = User::query()->where('email', 'anareyes@example.test')->firstOrFail();
        $staffToken = $owner->createToken('staff-session:test', ['staff'])->plainTextToken;

        $this->withToken($staffToken)
            ->postJson('/api/platform-admin', ['action' => 'listOrgs'])
            ->assertStatus(401);
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
        $owner->forceFill(['email_verified_at' => now()])->save();

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

    public function test_a_superadmin_can_email_an_owner_from_the_portal(): void
    {
        Mail::fake();

        $slug = $this->signUpTenant()['organizationSlug'];
        $owner = User::query()->where('username', 'anareyes')->firstOrFail();

        $this->call_admin([
            'action' => 'sendOwnerEmail',
            'organizationSlug' => $slug,
            'uid' => $owner->id,
            'subject' => 'Need one more document',
            'message' => "Please send a clearer GCash receipt.\nThank you.",
        ])->assertOk()->assertJsonPath('queued', true);

        Mail::assertQueued(PlatformAdminReplyMail::class, function (PlatformAdminReplyMail $mail) use ($owner) {
            return $mail->hasTo($owner->email)
                && $mail->subjectLine === 'Need one more document'
                && $mail->messageBody === "Please send a clearer GCash receipt.\nThank you.";
        });
    }

    public function test_emailing_an_owner_without_an_email_is_rejected(): void
    {
        $slug = $this->signUpTenant()['organizationSlug'];
        $owner = User::query()->where('username', 'anareyes')->firstOrFail();
        Mail::fake();
        $owner->forceFill(['email' => null])->save();

        $this->call_admin([
            'action' => 'sendOwnerEmail',
            'organizationSlug' => $slug,
            'uid' => $owner->id,
            'subject' => 'Hello',
            'message' => 'Test body',
        ])->assertStatus(422);

        Mail::assertNotQueued(PlatformAdminReplyMail::class);
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
