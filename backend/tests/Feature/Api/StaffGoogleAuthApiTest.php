<?php

namespace Tests\Feature\Api;

use App\Models\Store;
use App\Models\StoreMembership;
use App\Models\User;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Illuminate\Support\Facades\Cache;
use Illuminate\Support\Facades\Http;
use Tests\TestCase;

/**
 * Sign in with Google, for the staff card the register and admin app share.
 *
 * The endpoint existed before anything pressed it; these tests arrived with the
 * button that made it reachable.
 *
 * What separates this from the customer door is that it never creates an
 * account. A staff account is a claim on somebody else's shop, so it is made by
 * that shop — through signup, for an owner, or by an admin in Staff — and
 * Google only proves who is holding the phone. Most of what is below is that
 * one sentence, checked from the outside: a valid Google account that nobody
 * has been given access to must end up with no session, no user row, and a
 * message telling them who to ask.
 *
 * Real tokens, really verified, the same way CustomerGoogleAuthApiTest does it:
 * each test signs a JWT with the checked-in test key and serves the matching
 * public key from a faked `oauth2/v1/certs`, so the production verifier runs
 * its actual signature check rather than being stubbed out.
 */
class StaffGoogleAuthApiTest extends TestCase
{
    use RefreshDatabase;

    private const WEB_CLIENT_ID = '111111111111-web.apps.googleusercontent.com';

    private const KID = 'test-key-1';

    /** The seeded admin's address, which is what a Google identity matches on. */
    private const ADMIN_EMAIL = 'admin@example.com';

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
            'email' => self::ADMIN_EMAIL,
            'email_verified' => true,
            'name' => 'Shop Admin',
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
        return $this->postJson('/api/staff/auth/google', [
            'credential' => $this->token($claims, $header),
        ]);
    }

    // -- The happy path -------------------------------------------------------

    public function test_google_signs_in_the_account_that_owns_the_address_and_offers_its_store(): void
    {
        $this->seed();

        $response = $this->signInWith()->assertOk();

        $this->assertNotEmpty($response->json('token'));
        $this->assertSame('admin', $response->json('user.username'));
        $this->assertSame(self::ADMIN_EMAIL, $response->json('user.email'));
        $response->assertJsonPath('stores.0.code', 'main')
            ->assertJsonPath('stores.0.role', 'admin');
    }

    /**
     * The Google door is the first of the same two steps the password door
     * takes: the token it hands back names no store, and is worth nothing to
     * the seller API until one is chosen.
     */
    public function test_the_google_token_is_unscoped_until_a_store_is_chosen(): void
    {
        $this->seed();

        $signIn = $this->signInWith()->assertOk();
        $pending = $signIn->json('token');

        $this->withToken($pending)
            ->getJson('/api/seller/online-orders')
            ->assertForbidden();

        $scoped = $this->withToken($pending)
            ->postJson('/api/staff/session-store', ['storeId' => $signIn->json('stores.0.id')])
            ->assertOk()
            ->json('token');

        $this->withToken($scoped)
            ->getJson('/api/seller/online-orders')
            ->assertOk();
    }

    /**
     * Pressing the button is the same proof as clicking the link in our own
     * verification mail, made by the only other party that can read the mailbox
     * — so someone invited by their manager who never clicked it is verified by
     * having signed in, and is matched on the subject id from then on.
     */
    public function test_signing_in_links_the_google_identity_and_verifies_the_address(): void
    {
        $this->seed();

        $admin = User::query()->where('username', 'admin')->firstOrFail();
        $admin->forceFill(['google_sub' => null, 'email_verified_at' => null])->save();

        $this->signInWith()->assertOk();

        $admin->refresh();

        $this->assertSame(self::SUB, $admin->google_sub);
        $this->assertTrue($admin->hasVerifiedEmail());
        $this->assertSame('https://lh3.googleusercontent.com/a/portrait', $admin->avatar_url);
    }

    /**
     * Google lets someone change the address on their account. The link is to
     * the subject id, so it follows rather than stranding them.
     */
    public function test_a_linked_account_that_changed_its_google_email_keeps_its_account(): void
    {
        $this->seed();

        $before = User::query()->count();

        $this->signInWith()->assertOk();
        $this->signInWith(['email' => 'moved@example.com'])->assertOk();

        $this->assertSame($before, User::query()->count());
        $this->assertSame('moved@example.com', User::findByGoogleSub(self::SUB)->email);
    }

    // -- The refusals ---------------------------------------------------------

    /**
     * The one that matters. A perfectly valid Google account nobody has been
     * given access to is not a new empty session and not a new user row — it is
     * an instruction to go and ask someone.
     */
    public function test_a_google_account_no_shop_has_added_is_refused_and_creates_nothing(): void
    {
        $this->seed();

        $before = User::query()->count();

        $this->signInWith([
            'email' => 'stranger@gmail.com',
            'sub' => '999999999999999999999',
        ])
            ->assertForbidden()
            ->assertJsonPath(
                'message',
                "That Google account isn't set up for any shop yet. Ask an admin to add you in Staff.",
            );

        $this->assertSame($before, User::query()->count());
    }

    /**
     * An address on a Workspace domain that never completed verification.
     * Trusting it would make this a way into any shop whose staff email
     * somebody can merely type.
     */
    public function test_an_unverified_google_address_is_refused(): void
    {
        $this->seed();

        $this->signInWith(['email_verified' => false])
            ->assertStatus(422)
            ->assertJsonValidationErrors('credential');
    }

    public function test_an_account_with_no_membership_anywhere_is_refused(): void
    {
        $this->seed();

        User::query()->create([
            'name' => 'Nobody',
            'username' => 'nobody',
            'email' => 'nobody@example.com',
            'password' => 'secret1',
            'status' => 'active',
        ]);

        $this->signInWith(['email' => 'nobody@example.com', 'sub' => '888888888888888888888'])
            ->assertForbidden();
    }

    public function test_a_deactivated_account_cannot_sign_in_with_google(): void
    {
        $this->seed();

        $store = Store::query()->firstOrFail();
        $leaver = User::query()->create([
            'name' => 'Former Cashier',
            'username' => 'leaver',
            'email' => 'leaver@example.com',
            'password' => 'secret1',
            'status' => 'suspended',
        ]);
        StoreMembership::query()->create([
            'store_id' => $store->id,
            'user_id' => $leaver->id,
            'membership_role' => 'cashier',
        ]);

        $this->signInWith(['email' => 'leaver@example.com', 'sub' => '777777777777777777777'])
            ->assertForbidden();
    }

    /**
     * The signature check is the whole feature. A token with the right claims
     * and the wrong key is the attack this endpoint exists to survive.
     */
    public function test_a_token_whose_signature_does_not_match_is_refused(): void
    {
        $this->seed();

        [$header, $payload] = explode('.', $this->token());

        $this->postJson('/api/staff/auth/google', [
            'credential' => $header.'.'.$payload.'.'.$this->base64Url('not-a-signature'),
        ])
            ->assertStatus(422)
            ->assertJsonValidationErrors('credential');

        $this->assertNull(User::findByGoogleSub(self::SUB));
    }

    public function test_a_token_minted_for_another_application_is_refused(): void
    {
        $this->seed();

        $this->signInWith(['aud' => '222222222222-someone-else.apps.googleusercontent.com'])
            ->assertStatus(422)
            ->assertJsonValidationErrors('credential');
    }

    public function test_an_expired_token_is_refused(): void
    {
        $this->seed();

        $this->signInWith(['iat' => time() - 7200, 'exp' => time() - 3600])
            ->assertStatus(422)
            ->assertJsonValidationErrors('credential');
    }

    // -- The other door -------------------------------------------------------

    /**
     * An account created by the Google button has no password at all. Telling
     * that person "incorrect username or password" would be true and useless.
     */
    public function test_the_password_door_tells_a_google_only_account_which_button_to_press(): void
    {
        $this->seed();

        $store = Store::query()->firstOrFail();
        $user = User::query()->create([
            'name' => 'Google Only',
            'username' => 'googleonly',
            'email' => 'googleonly@example.com',
            'status' => 'active',
        ]);
        $user->forceFill(['password' => null, 'google_sub' => '666666666666666666666'])->save();

        StoreMembership::query()->create([
            'store_id' => $store->id,
            'user_id' => $user->id,
            'membership_role' => 'cashier',
        ]);

        $this->postJson('/api/staff/sign-in', [
            'identifier' => 'googleonly@example.com',
            'password' => 'anything-at-all',
        ])
            ->assertStatus(422)
            ->assertJsonPath(
                'errors.identifier.0',
                'This account signs in with Google. Use the Google button instead.',
            );
    }
}
