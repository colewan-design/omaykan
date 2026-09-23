<?php

namespace Tests\Feature\Api;

use App\Models\Rider;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Illuminate\Support\Facades\Cache;
use Illuminate\Support\Facades\Http;
use Tests\TestCase;

/**
 * Sign in with Google, for the rider portal.
 *
 * The rule this file exists to hold down is that the button **never
 * registers**. A rider account is a claim to be handed a stranger's home
 * address, and what earns it is a licence photo and a plate photo an operator
 * has looked at — none of which a Google credential carries. So a valid Google
 * account with no rider row must end up with no session, no rider row, and a
 * message pointing at registration.
 *
 * Real tokens, really verified, the same way StaffGoogleAuthApiTest does it:
 * each test signs a JWT with the checked-in test key and serves the matching
 * public key from a faked `oauth2/v1/certs`, so the production verifier runs
 * its actual signature check rather than being stubbed out.
 */
class RiderGoogleAuthApiTest extends TestCase
{
    use RefreshDatabase;

    private const WEB_CLIENT_ID = '111111111111-web.apps.googleusercontent.com';

    private const KID = 'test-key-1';

    private const RIDER_EMAIL = 'jun@example.com';

    private const SUB = '104729361882910473625';

    protected function setUp(): void
    {
        parent::setUp();

        config([
            'services.google.client_id' => self::WEB_CLIENT_ID,
            'services.google.android_client_id' => null,
            'services.google.extra_client_ids' => [],
        ]);

        // The certificate set is cached for an hour, and the array store
        // outlives a single test method within one process.
        Cache::flush();

        Http::fake([
            'https://www.googleapis.com/oauth2/v1/certs' => Http::response([
                self::KID => $this->publicKeyPem(),
            ]),
        ]);
    }

    // -- Fixtures -------------------------------------------------------------

    private function rider(array $overrides = []): Rider
    {
        return Rider::query()->create(array_merge([
            'name' => 'Jun Dela Cruz',
            'email' => self::RIDER_EMAIL,
            'phone' => '0917 555 0101',
            'password' => 'ride-with-me-1',
            'license_number' => 'N01-23-456789',
            'plate_number' => 'NBC 1234',
            'license_image_path' => 'rider-documents/licence.png',
            'plate_image_path' => 'rider-documents/plate.png',
            'status' => Rider::STATUS_APPROVED,
            'reviewed_at' => now(),
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
            'email' => self::RIDER_EMAIL,
            'email_verified' => true,
            'name' => 'Jun Dela Cruz',
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
        return $this->postJson('/api/rider/auth/google', [
            'credential' => $this->token($claims, $header),
        ]);
    }

    // -- The happy path -------------------------------------------------------

    public function test_google_signs_in_the_rider_that_owns_the_address(): void
    {
        $this->rider();

        $response = $this->signInWith()->assertOk();

        $this->assertNotEmpty($response->json('token'));
        $this->assertSame(self::RIDER_EMAIL, $response->json('rider.email'));
        $this->assertSame(Rider::STATUS_APPROVED, $response->json('rider.status'));
    }

    /** The token it hands back is a working rider token, not a stub. */
    public function test_the_google_token_reaches_the_rider_api(): void
    {
        $this->rider();

        $token = $this->signInWith()->assertOk()->json('token');

        $this->withToken($token)
            ->getJson('/api/rider/me')
            ->assertOk()
            ->assertJsonPath('rider.email', self::RIDER_EMAIL);
    }

    /**
     * Pressing the button is the same proof of the address as receiving mail at
     * it, so an account registered with a password is linked to the Google
     * identity on first use and matched on the subject id from then on.
     */
    public function test_signing_in_links_the_google_identity_to_the_existing_account(): void
    {
        $rider = $this->rider();

        $this->signInWith()->assertOk();

        $this->assertSame(self::SUB, $rider->fresh()->google_sub);

        // Matched on the sub from here, not the address: the same identity
        // reaching the same row after Google changed the email on the account.
        $this->signInWith(['email' => 'jun.delacruz@example.com'])->assertOk();

        $this->assertSame(1, Rider::query()->count());
        $this->assertSame('jun.delacruz@example.com', $rider->fresh()->email);
    }

    /**
     * A rider whose address was taken by a second account since they last
     * signed in keeps their session. A stale address is much the lesser problem
     * than a unique-column collision 500ing the door.
     */
    public function test_an_address_taken_by_another_rider_is_not_followed(): void
    {
        $rider = $this->rider();
        $this->signInWith()->assertOk();

        $this->rider([
            'email' => 'taken@example.com',
            'license_number' => 'N02-00-000000',
            'plate_number' => 'XYZ 9999',
        ]);

        $this->signInWith(['email' => 'taken@example.com'])->assertOk();

        $this->assertSame(self::RIDER_EMAIL, $rider->fresh()->email);
        $this->assertSame(2, Rider::query()->count());
    }

    // -- What it refuses ------------------------------------------------------

    /** The rule this file is mostly about. */
    public function test_a_google_account_with_no_rider_profile_is_refused_and_creates_nothing(): void
    {
        $this->signInWith()
            ->assertForbidden()
            ->assertJsonPath('message', fn (string $message) => str_contains($message, 'Register first'));

        $this->assertSame(0, Rider::query()->count());
    }

    public function test_an_unverified_google_address_is_refused(): void
    {
        $this->rider();

        $this->signInWith(['email_verified' => false])
            ->assertStatus(422)
            ->assertJsonValidationErrors('credential');
    }

    public function test_a_token_for_another_audience_is_refused(): void
    {
        $this->rider();

        $this->signInWith(['aud' => '222222222222-other.apps.googleusercontent.com'])
            ->assertStatus(422)
            ->assertJsonValidationErrors('credential');
    }

    public function test_an_expired_token_is_refused(): void
    {
        $this->rider();

        // Well past the verifier's 60-second clock-skew leeway, not on its edge.
        $this->signInWith(['iat' => time() - 7200, 'exp' => time() - 3600])
            ->assertStatus(422)
            ->assertJsonValidationErrors('credential');
    }

    public function test_a_token_signed_by_the_wrong_key_is_refused(): void
    {
        $this->rider();

        $credential = $this->token();

        // Keep the header and payload, replace the signature with noise.
        [$header, $payload] = explode('.', $credential);

        $this->postJson('/api/rider/auth/google', [
            'credential' => $header.'.'.$payload.'.'.$this->base64Url('not-a-signature'),
        ])
            ->assertStatus(422)
            ->assertJsonValidationErrors('credential');
    }

    /**
     * Rejected and suspended riders sign in here for the same reason they sign
     * in with a password: their status screen carries the reviewer's note, and
     * it is the only route they have back to an operator.
     */
    public function test_a_rejected_rider_still_signs_in_to_read_why(): void
    {
        $this->rider([
            'status' => Rider::STATUS_REJECTED,
            'review_note' => 'The licence photo was unreadable.',
        ]);

        $this->signInWith()
            ->assertOk()
            ->assertJsonPath('rider.status', Rider::STATUS_REJECTED);
    }
}
