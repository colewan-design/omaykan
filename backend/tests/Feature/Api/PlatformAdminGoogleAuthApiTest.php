<?php

namespace Tests\Feature\Api;

use App\Models\PlatformAdmin;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Illuminate\Support\Facades\Cache;
use Illuminate\Support\Facades\Http;
use Tests\TestCase;

/**
 * Sign in with Google, for the operator console.
 *
 * This is the one identity that acts across every tenant, so the tests that
 * matter most here are the refusals. There is no register endpoint behind this
 * door and no way for it to mint an operator: rows are made from the console
 * with `platform-admin:create`, and a Google identity with no row must be
 * turned away with nothing created.
 *
 * Real tokens, really verified, the same way StaffGoogleAuthApiTest does it:
 * each test signs a JWT with the checked-in test key and serves the matching
 * public key from a faked `oauth2/v1/certs`, so the production verifier runs
 * its actual signature check rather than being stubbed out.
 */
class PlatformAdminGoogleAuthApiTest extends TestCase
{
    use RefreshDatabase;

    private const WEB_CLIENT_ID = '111111111111-web.apps.googleusercontent.com';

    private const KID = 'test-key-1';

    private const OPERATOR_EMAIL = 'operator@example.test';

    private const SUB = '104729361882910473625';

    protected function setUp(): void
    {
        parent::setUp();

        config([
            'services.google.client_id' => self::WEB_CLIENT_ID,
            'services.google.android_client_id' => null,
            'services.google.extra_client_ids' => [],
        ]);

        Cache::flush();

        Http::fake([
            'https://www.googleapis.com/oauth2/v1/certs' => Http::response([
                self::KID => $this->publicKeyPem(),
            ]),
        ]);
    }

    // -- Fixtures -------------------------------------------------------------

    private function operator(array $overrides = []): PlatformAdmin
    {
        return PlatformAdmin::query()->create(array_merge([
            'name' => 'Platform Operator',
            'email' => self::OPERATOR_EMAIL,
            'password' => 'operator-password-1',
        ], $overrides));
    }

    // -- The fixture key and the tokens it signs ------------------------------

    private function privateKey(): \OpenSSLAsymmetricKey
    {
        $key = openssl_pkey_get_private(
            file_get_contents(base_path('tests/Fixtures/google-test-signing-key.pem')),
        );

        $this->assertNotFalse($key, 'The test signing key would not load.');

        return $key;
    }

    private function publicKeyPem(): string
    {
        return openssl_pkey_get_details($this->privateKey())['key'];
    }

    private function base64Url(string $value): string
    {
        return rtrim(strtr(base64_encode($value), '+/', '-_'), '=');
    }

    /** @param array<string, mixed> $claims */
    private function token(array $claims = [], array $header = []): string
    {
        $header = json_encode(array_merge(
            ['alg' => 'RS256', 'kid' => self::KID, 'typ' => 'JWT'],
            $header,
        ));

        $payload = json_encode(array_merge([
            'iss' => 'https://accounts.google.com',
            'aud' => self::WEB_CLIENT_ID,
            'sub' => self::SUB,
            'email' => self::OPERATOR_EMAIL,
            'email_verified' => true,
            'name' => 'Platform Operator',
            'picture' => 'https://lh3.googleusercontent.com/a/portrait',
            'iat' => time() - 5,
            'exp' => time() + 3600,
        ], $claims));

        $signed = $this->base64Url($header).'.'.$this->base64Url($payload);

        openssl_sign($signed, $signature, $this->privateKey(), OPENSSL_ALGO_SHA256);

        return $signed.'.'.$this->base64Url($signature);
    }

    /** @param array<string, mixed> $claims */
    private function signInWith(array $claims = [], array $header = [])
    {
        return $this->postJson('/api/platform-admin/auth/google', [
            'credential' => $this->token($claims, $header),
        ]);
    }

    // -- The happy path -------------------------------------------------------

    public function test_google_signs_in_the_operator_that_owns_the_address(): void
    {
        $this->operator();

        $response = $this->signInWith()->assertOk();

        $this->assertNotEmpty($response->json('token'));
        $this->assertSame(self::OPERATOR_EMAIL, $response->json('admin.email'));
    }

    /** The token it hands back carries real cross-tenant access. */
    public function test_the_google_token_reaches_the_operator_api(): void
    {
        $this->operator();

        $token = $this->signInWith()->assertOk()->json('token');

        $this->withToken($token)
            ->getJson('/api/platform-admin/me')
            ->assertOk()
            ->assertJsonPath('admin.email', self::OPERATOR_EMAIL);
    }

    public function test_signing_in_links_the_google_identity_to_the_existing_account(): void
    {
        $operator = $this->operator();

        $this->signInWith()->assertOk();

        $this->assertSame(self::SUB, $operator->fresh()->google_sub);

        // Matched on the sub from here, not the address.
        $this->signInWith(['email' => 'ops@example.test'])->assertOk();

        $this->assertSame(1, PlatformAdmin::query()->count());
        $this->assertSame('ops@example.test', $operator->fresh()->email);
    }

    /**
     * Same as the password door: one live session at a time, so "sign in again"
     * is a usable answer to a token you think may have leaked.
     */
    public function test_signing_in_revokes_the_tokens_already_out(): void
    {
        $operator = $this->operator();

        $stale = $operator->createToken('platform-admin', [PlatformAdmin::ABILITY])->plainTextToken;

        $this->signInWith()->assertOk();

        $this->withToken($stale)->getJson('/api/platform-admin/me')->assertUnauthorized();
    }

    // -- What it refuses ------------------------------------------------------

    /** The rule this file is mostly about: no row, no operator, nothing made. */
    public function test_a_google_account_with_no_operator_row_is_refused_and_creates_nothing(): void
    {
        $this->signInWith()->assertForbidden();

        $this->assertSame(0, PlatformAdmin::query()->count());
    }

    public function test_a_disabled_operator_is_refused(): void
    {
        $this->operator(['disabled_at' => now()]);

        $this->signInWith()
            ->assertForbidden()
            ->assertJsonPath('message', 'That operator account has been disabled.');
    }

    public function test_an_unverified_google_address_is_refused(): void
    {
        $this->operator();

        $this->signInWith(['email_verified' => false])
            ->assertStatus(422)
            ->assertJsonValidationErrors('credential');
    }

    public function test_a_token_for_another_audience_is_refused(): void
    {
        $this->operator();

        $this->signInWith(['aud' => '222222222222-other.apps.googleusercontent.com'])
            ->assertStatus(422)
            ->assertJsonValidationErrors('credential');
    }

    public function test_an_expired_token_is_refused(): void
    {
        $this->operator();

        // Well past the verifier's 60-second clock-skew leeway, not on its edge.
        $this->signInWith(['iat' => time() - 7200, 'exp' => time() - 3600])
            ->assertStatus(422)
            ->assertJsonValidationErrors('credential');
    }

    public function test_a_token_signed_by_the_wrong_key_is_refused(): void
    {
        $this->operator();

        [$header, $payload] = explode('.', $this->token());

        $this->postJson('/api/platform-admin/auth/google', [
            'credential' => $header.'.'.$payload.'.'.$this->base64Url('not-a-signature'),
        ])
            ->assertStatus(422)
            ->assertJsonValidationErrors('credential');
    }
}
