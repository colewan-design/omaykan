<?php

namespace Tests\Feature\Api;

use App\Models\CustomerAccount;
use Illuminate\Foundation\Testing\DatabaseMigrations;
use Illuminate\Support\Facades\Cache;
use Illuminate\Support\Facades\Http;
use Tests\TestCase;

/**
 * Sign in with Google, for the storefront button and the Android app.
 *
 * Real tokens, really verified. Every test here signs a JWT with a test RSA key
 * and serves the matching public key from a faked `oauth2/v1/certs`, so the
 * production verifier runs its actual signature check rather than being stubbed
 * out — which is the only part of this feature where a mistake is a way in
 * rather than a bug.
 *
 * The key lives in tests/Fixtures. It signs nothing but these tokens and is
 * checked in on purpose: generating one per run needs an openssl.cnf that a
 * Windows PHP build does not ship with, and a test that only runs on some
 * machines is a test nobody trusts.
 */
class CustomerGoogleAuthApiTest extends TestCase
{
    use DatabaseMigrations;

    private const WEB_CLIENT_ID = '111111111111-web.apps.googleusercontent.com';

    private const ANDROID_CLIENT_ID = '111111111111-android.apps.googleusercontent.com';

    private const KID = 'test-key-1';

    protected function setUp(): void
    {
        parent::setUp();

        config([
            'services.google.client_id' => self::WEB_CLIENT_ID,
            'services.google.android_client_id' => self::ANDROID_CLIENT_ID,
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

    // -- The fixture key and the tokens it signs ------------------------------

    private function privateKey(): \OpenSSLAsymmetricKey
    {
        $key = openssl_pkey_get_private(
            file_get_contents(base_path('tests/Fixtures/google-test-signing-key.pem')),
        );

        $this->assertNotFalse($key, 'The test signing key would not load.');

        return $key;
    }

    /**
     * Served in place of Google's X.509 certificates.
     *
     * `openssl_pkey_get_public` takes either form, so the verifier is exercised
     * exactly as written; a bare public key is simply the half of the fixture
     * that does not need a certificate authority to exist.
     */
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
            'sub' => '104729361882910473625',
            'email' => 'shopper@gmail.com',
            'email_verified' => true,
            'name' => 'Christian Colewan',
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
        return $this->postJson('/api/customer/auth/google', [
            'credential' => $this->token($claims, $header),
        ]);
    }

    // -- The happy paths ------------------------------------------------------

    public function test_a_new_google_shopper_gets_an_account_and_a_token(): void
    {
        $response = $this->signInWith()->assertCreated();

        $this->assertNotEmpty($response->json('token'));
        $this->assertSame('shopper@gmail.com', $response->json('account.email'));
        $this->assertSame('Christian Colewan', $response->json('account.name'));
        $this->assertTrue($response->json('account.googleLinked'));
        $this->assertFalse($response->json('account.hasPassword'));

        $account = CustomerAccount::findByEmail('shopper@gmail.com');

        $this->assertNotNull($account);
        // No verification mail, no pending state: Google has already proved the
        // shopper holds the mailbox.
        $this->assertTrue($account->hasVerifiedEmail());
        $this->assertNull($account->password);
        $this->assertSame('104729361882910473625', $account->google_sub);
    }

    public function test_the_minted_token_works_on_the_customer_api(): void
    {
        $token = $this->signInWith()->json('token');

        $this->withToken($token)
            ->getJson('/api/customer/me')
            ->assertOk()
            ->assertJsonPath('account.email', 'shopper@gmail.com');
    }

    public function test_signing_in_twice_reuses_the_one_account(): void
    {
        $this->signInWith()->assertCreated();
        // 200 rather than 201 the second time: this is a sign-in, not a sign-up.
        $this->signInWith()->assertOk();

        $this->assertSame(1, CustomerAccount::query()->count());
    }

    public function test_a_token_from_the_android_client_is_accepted_too(): void
    {
        $this->signInWith(['aud' => self::ANDROID_CLIENT_ID])->assertCreated();

        $this->assertNotNull(CustomerAccount::findByEmail('shopper@gmail.com'));
    }

    public function test_a_google_account_that_changed_its_email_keeps_its_account(): void
    {
        $this->signInWith()->assertCreated();

        $this->signInWith(['email' => 'moved@gmail.com'])->assertOk();

        $this->assertSame(1, CustomerAccount::query()->count());
        $this->assertSame(
            'moved@gmail.com',
            CustomerAccount::findByGoogleSub('104729361882910473625')->email,
        );
    }

    // -- Linking onto an account that already exists --------------------------

    public function test_google_links_onto_the_password_account_with_the_same_email(): void
    {
        $this->postJson('/api/customer/register', [
            'name' => 'Christian Colewan',
            'email' => 'shopper@gmail.com',
            'password' => 'baguio-pines-2026',
            'password_confirmation' => 'baguio-pines-2026',
        ])->assertCreated();

        $original = CustomerAccount::findByEmail('shopper@gmail.com');
        $this->assertFalse($original->hasVerifiedEmail());

        $this->signInWith()->assertOk();

        $this->assertSame(1, CustomerAccount::query()->count());

        $linked = CustomerAccount::findByEmail('shopper@gmail.com');
        $this->assertSame($original->id, $linked->id);
        $this->assertSame('104729361882910473625', $linked->google_sub);
        // Pressing the Google button proves the address as well as clicking the
        // link in our own mail would have.
        $this->assertTrue($linked->hasVerifiedEmail());
        // And the password they set still works — linking adds a way in, it
        // does not replace the one they had.
        $this->assertTrue($linked->hasPassword());

        $this->postJson('/api/customer/login', [
            'email' => 'shopper@gmail.com',
            'password' => 'baguio-pines-2026',
        ])->assertOk();
    }

    public function test_the_case_of_the_google_email_does_not_make_a_second_account(): void
    {
        $this->signInWith()->assertCreated();
        $this->signInWith(['email' => 'Shopper@Gmail.com'])->assertOk();

        $this->assertSame(1, CustomerAccount::query()->count());
    }

    // -- What must be refused -------------------------------------------------

    public function test_a_token_for_another_app_is_refused(): void
    {
        $this->signInWith(['aud' => '999-someone-else.apps.googleusercontent.com'])
            ->assertStatus(422)
            ->assertJsonValidationErrors('credential');

        $this->assertSame(0, CustomerAccount::query()->count());
    }

    public function test_a_token_signed_by_someone_else_is_refused(): void
    {
        $forged = $this->token();
        // Same header and claims, a signature that is not Google's.
        $tampered = substr($forged, 0, strrpos($forged, '.') + 1)
            .$this->base64Url(str_repeat("\x00", 256));

        $this->postJson('/api/customer/auth/google', ['credential' => $tampered])
            ->assertStatus(422)
            ->assertJsonValidationErrors('credential');

        $this->assertSame(0, CustomerAccount::query()->count());
    }

    public function test_an_unsigned_token_is_refused(): void
    {
        // The oldest JWT hole there is: claims anyone can write, `alg: none`,
        // and a verifier that reads the algorithm out of the token it is
        // checking. This one pins RS256.
        $header = $this->base64Url(json_encode(['alg' => 'none', 'kid' => self::KID]));
        $payload = $this->base64Url(json_encode([
            'iss' => 'https://accounts.google.com',
            'aud' => self::WEB_CLIENT_ID,
            'sub' => 'attacker',
            'email' => 'shopper@gmail.com',
            'email_verified' => true,
            'exp' => time() + 3600,
        ]));

        $this->postJson('/api/customer/auth/google', ['credential' => "$header.$payload."])
            ->assertStatus(422);

        $this->assertSame(0, CustomerAccount::query()->count());
    }

    public function test_an_expired_token_is_refused(): void
    {
        $this->signInWith(['iat' => time() - 7200, 'exp' => time() - 3600])
            ->assertStatus(422)
            ->assertJsonValidationErrors('credential');
    }

    public function test_a_token_from_the_wrong_issuer_is_refused(): void
    {
        $this->signInWith(['iss' => 'https://accounts.notgoogle.com'])
            ->assertStatus(422);
    }

    public function test_an_unverified_google_email_cannot_claim_an_account(): void
    {
        // The takeover this endpoint would otherwise be: a Workspace domain
        // whose address Google has not checked, aimed at somebody else's
        // account here.
        $this->postJson('/api/customer/register', [
            'name' => 'Christian Colewan',
            'email' => 'shopper@gmail.com',
            'password' => 'baguio-pines-2026',
            'password_confirmation' => 'baguio-pines-2026',
        ])->assertCreated();

        $this->signInWith(['email_verified' => false, 'sub' => 'someone-else'])
            ->assertStatus(422)
            ->assertJsonValidationErrors('credential');

        $this->assertNull(CustomerAccount::findByEmail('shopper@gmail.com')->google_sub);
    }

    public function test_the_endpoint_refuses_everything_when_no_client_id_is_configured(): void
    {
        config([
            'services.google.client_id' => null,
            'services.google.android_client_id' => null,
        ]);

        $this->signInWith()->assertStatus(422);
    }

    // -- Living with a password-less account ----------------------------------

    public function test_password_sign_in_points_a_google_only_shopper_back_at_the_button(): void
    {
        $this->signInWith()->assertCreated();

        $response = $this->postJson('/api/customer/login', [
            'email' => 'shopper@gmail.com',
            'password' => 'anything-at-all',
        ])->assertStatus(422);

        $this->assertStringContainsString('Google', $response->json('errors.email.0'));
    }

    public function test_a_google_shopper_can_set_a_first_password_without_giving_one(): void
    {
        $token = $this->signInWith()->json('token');

        $this->withToken($token)->patchJson('/api/customer/account/password', [
            'password' => 'baguio-pines-2026',
            'password_confirmation' => 'baguio-pines-2026',
        ])->assertOk()->assertJsonPath('account.hasPassword', true);

        // Both doors now work, and the Google one still finds the same account.
        $this->postJson('/api/customer/login', [
            'email' => 'shopper@gmail.com',
            'password' => 'baguio-pines-2026',
        ])->assertOk();

        $this->signInWith()->assertOk();
        $this->assertSame(1, CustomerAccount::query()->count());
    }

    public function test_a_shopper_with_a_password_still_has_to_give_it(): void
    {
        $token = $this->signInWith()->json('token');

        $this->withToken($token)->patchJson('/api/customer/account/password', [
            'password' => 'baguio-pines-2026',
            'password_confirmation' => 'baguio-pines-2026',
        ])->assertOk();

        $this->withToken($token)->patchJson('/api/customer/account/password', [
            'password' => 'a-different-one-2026',
            'password_confirmation' => 'a-different-one-2026',
        ])->assertStatus(422)->assertJsonValidationErrors('currentPassword');
    }

    public function test_a_google_only_shopper_cannot_move_their_email(): void
    {
        $token = $this->signInWith()->json('token');

        $this->withToken($token)->patchJson('/api/customer/account/email', [
            'email' => 'elsewhere@example.com',
            'currentPassword' => '',
        ])->assertStatus(422)->assertJsonValidationErrors('email');

        $this->assertSame('shopper@gmail.com', CustomerAccount::query()->first()->email);
    }
}
