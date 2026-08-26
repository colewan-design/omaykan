<?php

namespace Tests\Feature\Api\Platform;

use App\Models\PlatformAdmin;
use App\Models\User;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Illuminate\Testing\TestResponse;
use Tests\TestCase;

/**
 * Shared setup for the operator portal tests.
 *
 * Every test in here needs the same two things — a tenant that exists because
 * somebody signed up for it, and a bearer token for an operator — and getting
 * either subtly wrong is how a test passes against the wrong guard.
 */
abstract class PlatformTestCase extends TestCase
{
    use RefreshDatabase;

    /** Creates a real tenant the way a merchant would, through /api/signup. */
    protected function signUpTenant(array $overrides = []): array
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

    protected function operator(
        string $role = PlatformAdmin::ROLE_OWNER,
        string $email = 'ops@omaykan.test',
        string $status = PlatformAdmin::STATUS_ACTIVE,
    ): PlatformAdmin {
        return PlatformAdmin::query()->create([
            'name' => 'Platform Operator',
            'email' => $email,
            'password' => 'operator-password-1',
            'role' => $role,
            'status' => $status,
        ]);
    }

    /** A live portal token, minted the way AuthController mints one. */
    protected function tokenFor(PlatformAdmin $admin): string
    {
        return $admin->createToken(PlatformAdmin::TOKEN_NAME, ['platform'])->plainTextToken;
    }

    /**
     * @return array<string, string>
     */
    protected function authHeader(string $token): array
    {
        return ['Authorization' => 'Bearer '.$token];
    }

    /**
     * A merchant owner who has clicked the link in their signup email.
     *
     * Staff sign-in refuses an unverified address (see StaffSessionController),
     * so any test that signs in as the owner — to prove a password reset took,
     * or that disabling revoked a session — has to get past that first.
     */
    protected function verifyOwnerEmail(string $username = 'anareyes'): User
    {
        $owner = User::query()->where('username', $username)->firstOrFail();
        $owner->markEmailAsVerified();

        return $owner;
    }

    /**
     * Forgets resolved guards before every request.
     *
     * A test reuses one container across requests, so a guard still holds
     * whoever it resolved a moment ago — which quietly makes the *previous*
     * request's identity the one a later assertion sees. That breaks exactly
     * the tests that matter here: disabling an account and checking its live
     * token, or switching between an owner and an operator. Production builds
     * a fresh container per request; this is what that looks like. The rider
     * delivery tests do the same thing by hand — doing it here means no test
     * can forget.
     */
    public function call($method, $uri, $parameters = [], $cookies = [], $files = [], $server = [], $content = null): TestResponse
    {
        $this->app['auth']->forgetGuards();

        return parent::call($method, $uri, $parameters, $cookies, $files, $server, $content);
    }
}
