<?php

namespace Tests\Feature\Api;

use App\Models\CustomerAccount;
use Illuminate\Foundation\Testing\DatabaseMigrations;
use Tests\TestCase;

/**
 * The account portal: profile, credentials, addresses and payment methods.
 *
 * Untested until now, and worth covering properly because a second client — the
 * Android app — has just started depending on all of it. Two properties matter
 * more than the happy paths and are asserted throughout:
 *
 *  - **One shopper never reaches another's rows.** Every id is resolved through
 *    the signed-in account, so somebody else's is a 404 and not a leak.
 *  - **Every write answers with the whole account.** Saving an address can move
 *    the default off another one, and deleting the default promotes its
 *    neighbour, so a reply carrying one row would leave a client patching up a
 *    list whose shape it no longer knows.
 */
class CustomerAccountApiTest extends TestCase
{
    use DatabaseMigrations;

    private const PASSWORD = 'baguio-pines-2026';

    /** @return array{token: string, account: array<string, mixed>} */
    private function signedIn(string $email = 'shopper@example.com'): array
    {
        $this->postJson('/api/customer/register', [
            'name' => 'Christian Colewan',
            'email' => $email,
            'phone' => '09171234567',
            'password' => self::PASSWORD,
            'password_confirmation' => self::PASSWORD,
        ])->assertCreated();

        CustomerAccount::findByEmail($email)?->forceFill(['email_verified_at' => now()])->save();

        $response = $this->postJson('/api/customer/login', [
            'email' => $email,
            'password' => self::PASSWORD,
        ])->assertOk();

        return ['token' => $response->json('token'), 'account' => $response->json('account')];
    }

    /** Guards cache the user they resolved; without this a second request in one test keeps the first one's owner. */
    private function as(string $token): self
    {
        $this->app['auth']->forgetGuards();

        return $this->withHeader('Authorization', "Bearer {$token}");
    }

    /** @return array<string, mixed> */
    private function address(array $overrides = []): array
    {
        return array_merge([
            'label' => 'Home',
            'line1' => '12 Session Road',
            'barangay' => 'Kabayanan',
            'city' => 'Baguio',
            'notes' => 'Green gate past the court',
        ], $overrides);
    }

    // -- Profile ------------------------------------------------------------

    public function test_it_updates_the_name_and_phone(): void
    {
        ['token' => $token] = $this->signedIn();

        $this->as($token)
            ->patchJson('/api/customer/account', ['name' => 'Chris C', 'phone' => '09998887777'])
            ->assertOk()
            ->assertJsonPath('account.name', 'Chris C')
            ->assertJsonPath('account.phone', '09998887777');
    }

    /** The portal saves one toggle at a time; a whole-object write would blank the rest. */
    public function test_preferences_are_merged_not_replaced(): void
    {
        ['token' => $token] = $this->signedIn();

        $this->as($token)
            ->patchJson('/api/customer/account', ['preferences' => ['smsUpdates' => true]])
            ->assertOk()
            ->assertJsonPath('account.preferences.smsUpdates', true)
            // Untouched, and still at the server's own default.
            ->assertJsonPath('account.preferences.emailUpdates', true)
            ->assertJsonPath('account.preferences.substitutions', 'call');

        $this->as($token)
            ->patchJson('/api/customer/account', ['preferences' => ['substitutions' => 'refund']])
            ->assertOk()
            ->assertJsonPath('account.preferences.substitutions', 'refund')
            ->assertJsonPath('account.preferences.smsUpdates', true);
    }

    public function test_an_unknown_substitution_choice_is_refused(): void
    {
        ['token' => $token] = $this->signedIn();

        $this->as($token)
            ->patchJson('/api/customer/account', ['preferences' => ['substitutions' => 'shrug']])
            ->assertStatus(422)
            ->assertJsonValidationErrors('preferences.substitutions');
    }

    public function test_the_profile_is_behind_the_customer_guard(): void
    {
        $this->patchJson('/api/customer/account', ['name' => 'Nobody'])->assertUnauthorized();
    }

    // -- Credentials --------------------------------------------------------

    public function test_changing_the_email_needs_the_current_password(): void
    {
        ['token' => $token] = $this->signedIn();

        $this->as($token)
            ->patchJson('/api/customer/account/email', [
                'email' => 'moved@example.com',
                'currentPassword' => 'not-the-password',
            ])
            ->assertStatus(422);

        $this->as($token)
            ->patchJson('/api/customer/account/email', [
                'email' => 'moved@example.com',
                'currentPassword' => self::PASSWORD,
            ])
            ->assertOk()
            ->assertJsonPath('account.email', 'moved@example.com');
    }

    public function test_an_email_already_in_use_is_refused(): void
    {
        $this->signedIn('first@example.com');
        ['token' => $token] = $this->signedIn('second@example.com');

        $this->as($token)
            ->patchJson('/api/customer/account/email', [
                'email' => 'first@example.com',
                'currentPassword' => self::PASSWORD,
            ])
            ->assertStatus(422)
            ->assertJsonValidationErrors('email');
    }

    /**
     * Google's copy is what the next sign-in arrives with, so moving ours
     * either gets undone or mints a second account.
     */
    public function test_a_google_only_account_cannot_change_its_email_here(): void
    {
        ['token' => $token] = $this->signedIn();

        // What a Google-only account looks like: an identity, and no password.
        CustomerAccount::findByEmail('shopper@example.com')
            ?->forceFill(['password' => null, 'google_sub' => 'google-123'])->save();

        $this->as($token)
            ->patchJson('/api/customer/account/email', [
                'email' => 'moved@example.com',
                'currentPassword' => self::PASSWORD,
            ])
            ->assertStatus(422)
            ->assertJsonValidationErrors('email');
    }

    public function test_changing_the_password_signs_out_other_devices_but_not_this_one(): void
    {
        ['token' => $first] = $this->signedIn();

        // A second device on the same account.
        $this->app['auth']->forgetGuards();
        $second = $this->postJson('/api/customer/login', [
            'email' => 'shopper@example.com',
            'password' => self::PASSWORD,
        ])->assertOk()->json('token');

        $this->as($first)
            ->patchJson('/api/customer/account/password', [
                'currentPassword' => self::PASSWORD,
                'password' => 'session-road-mist',
                'password_confirmation' => 'session-road-mist',
            ])
            ->assertOk();

        $this->as($first)->getJson('/api/customer/me')->assertOk();
        $this->as($second)->getJson('/api/customer/me')->assertUnauthorized();
    }

    public function test_the_wrong_current_password_does_not_change_it(): void
    {
        ['token' => $token] = $this->signedIn();

        $this->as($token)
            ->patchJson('/api/customer/account/password', [
                'currentPassword' => 'wrong',
                'password' => 'session-road-mist',
                'password_confirmation' => 'session-road-mist',
            ])
            ->assertStatus(422);

        $this->app['auth']->forgetGuards();
        $this->postJson('/api/customer/login', [
            'email' => 'shopper@example.com',
            'password' => self::PASSWORD,
        ])->assertOk();
    }

    /** Not a change of credential — the first one, proved by the session itself. */
    public function test_a_google_only_account_sets_its_first_password_without_one(): void
    {
        ['token' => $token] = $this->signedIn();

        CustomerAccount::findByEmail('shopper@example.com')
            ?->forceFill(['password' => null, 'google_sub' => 'google-123'])->save();

        $this->as($token)
            ->patchJson('/api/customer/account/password', [
                'password' => 'session-road-mist',
                'password_confirmation' => 'session-road-mist',
            ])
            ->assertOk()
            ->assertJsonPath('account.hasPassword', true);
    }

    // -- Addresses ----------------------------------------------------------

    public function test_the_first_address_saved_becomes_the_default(): void
    {
        ['token' => $token] = $this->signedIn();

        $this->as($token)
            ->postJson('/api/customer/addresses', $this->address(['isDefault' => false]))
            ->assertCreated()
            ->assertJsonPath('account.addresses.0.isDefault', true)
            ->assertJsonPath('account.addresses.0.label', 'Home');
    }

    public function test_only_one_address_is_ever_the_default(): void
    {
        ['token' => $token] = $this->signedIn();

        $this->as($token)->postJson('/api/customer/addresses', $this->address())->assertCreated();
        $second = $this->as($token)
            ->postJson('/api/customer/addresses', $this->address([
                'label' => "Mum's",
                'isDefault' => true,
            ]))
            ->assertCreated()
            ->json('addressId');

        $addresses = $this->as($token)->getJson('/api/customer/me')->json('account.addresses');

        $this->assertCount(1, array_filter($addresses, fn ($row) => $row['isDefault']));
        // Default first, so a client can take the head of the list.
        $this->assertSame($second, $addresses[0]['id']);
    }

    /** Half a coordinate quietly changes the delivery quote, so it is refused. */
    public function test_half_a_pin_is_rejected(): void
    {
        ['token' => $token] = $this->signedIn();

        $this->as($token)
            ->postJson('/api/customer/addresses', $this->address(['lat' => 16.41]))
            ->assertStatus(422)
            ->assertJsonValidationErrors('lng');

        $this->as($token)
            ->postJson('/api/customer/addresses', $this->address(['lat' => 16.41, 'lng' => 120.59]))
            ->assertCreated()
            ->assertJsonPath('account.addresses.0.lat', 16.41)
            ->assertJsonPath('account.addresses.0.lng', 120.59);
    }

    public function test_an_address_can_be_edited(): void
    {
        ['token' => $token] = $this->signedIn();
        $id = $this->as($token)
            ->postJson('/api/customer/addresses', $this->address())
            ->json('addressId');

        $this->as($token)
            ->patchJson("/api/customer/addresses/{$id}", ['line1' => '14 Session Road'])
            ->assertOk()
            ->assertJsonPath('account.addresses.0.line1', '14 Session Road')
            // Untouched by a partial write.
            ->assertJsonPath('account.addresses.0.label', 'Home');
    }

    /**
     * The regression guard for dropping `sometimes` off lat/lng.
     *
     * Those rules have to run on an absent field for required_with to catch
     * half a pin — but running them must not turn "did not mention the pin"
     * into "clear the pin", or every edit to a house number would silently cost
     * the shopper their delivery quote.
     */
    public function test_editing_an_address_leaves_an_existing_pin_alone(): void
    {
        ['token' => $token] = $this->signedIn();
        $id = $this->as($token)
            ->postJson('/api/customer/addresses', $this->address(['lat' => 16.41, 'lng' => 120.59]))
            ->json('addressId');

        $this->as($token)
            ->patchJson("/api/customer/addresses/{$id}", ['line1' => '14 Session Road'])
            ->assertOk()
            ->assertJsonPath('account.addresses.0.lat', 16.41)
            ->assertJsonPath('account.addresses.0.lng', 120.59);
    }

    /** And clearing it is still possible, for an address that has moved. */
    public function test_a_pin_can_be_cleared_explicitly(): void
    {
        ['token' => $token] = $this->signedIn();
        $id = $this->as($token)
            ->postJson('/api/customer/addresses', $this->address(['lat' => 16.41, 'lng' => 120.59]))
            ->json('addressId');

        $this->as($token)
            ->patchJson("/api/customer/addresses/{$id}", ['lat' => null, 'lng' => null])
            ->assertOk()
            ->assertJsonPath('account.addresses.0.lat', null)
            ->assertJsonPath('account.addresses.0.lng', null);
    }

    public function test_deleting_the_default_promotes_the_next_one(): void
    {
        ['token' => $token] = $this->signedIn();

        $first = $this->as($token)
            ->postJson('/api/customer/addresses', $this->address())
            ->json('addressId');
        $this->as($token)
            ->postJson('/api/customer/addresses', $this->address(['label' => "Mum's"]))
            ->assertCreated();

        $this->as($token)
            ->deleteJson("/api/customer/addresses/{$first}")
            ->assertOk()
            ->assertJsonCount(1, 'account.addresses')
            ->assertJsonPath('account.addresses.0.label', "Mum's")
            // Never a list without a default.
            ->assertJsonPath('account.addresses.0.isDefault', true);
    }

    public function test_one_shopper_cannot_touch_anothers_address(): void
    {
        ['token' => $mine] = $this->signedIn('first@example.com');
        $id = $this->as($mine)
            ->postJson('/api/customer/addresses', $this->address())
            ->json('addressId');

        ['token' => $theirs] = $this->signedIn('second@example.com');

        $this->as($theirs)->patchJson("/api/customer/addresses/{$id}", ['city' => 'Manila'])->assertNotFound();
        $this->as($theirs)->deleteJson("/api/customer/addresses/{$id}")->assertNotFound();

        $this->as($mine)
            ->getJson('/api/customer/me')
            ->assertJsonPath('account.addresses.0.city', 'Baguio');
    }

    // -- Payment methods ----------------------------------------------------

    public function test_a_payment_method_is_saved_and_defaulted(): void
    {
        ['token' => $token] = $this->signedIn();

        $this->as($token)
            ->postJson('/api/customer/payment-methods', ['kind' => 'cash'])
            ->assertCreated()
            ->assertJsonPath('account.paymentMethods.0.kind', 'cash')
            ->assertJsonPath('account.paymentMethods.0.isDefault', true);
    }

    /** Cash carries no detail, so a second row would be an exact duplicate. */
    public function test_cash_cannot_be_saved_twice(): void
    {
        ['token' => $token] = $this->signedIn();

        $this->as($token)->postJson('/api/customer/payment-methods', ['kind' => 'cash'])->assertCreated();
        $this->as($token)
            ->postJson('/api/customer/payment-methods', ['kind' => 'cash'])
            ->assertStatus(422)
            ->assertJsonValidationErrors('kind');
    }

    public function test_an_ewallet_needs_a_number(): void
    {
        ['token' => $token] = $this->signedIn();

        $this->as($token)
            ->postJson('/api/customer/payment-methods', ['kind' => 'ewallet'])
            ->assertStatus(422)
            ->assertJsonValidationErrors('detail');

        $this->as($token)
            ->postJson('/api/customer/payment-methods', ['kind' => 'ewallet', 'detail' => '09171234567'])
            ->assertCreated()
            ->assertJsonPath('account.paymentMethods.0.detail', '09171234567');
    }

    public function test_one_shopper_cannot_touch_anothers_payment_method(): void
    {
        ['token' => $mine] = $this->signedIn('first@example.com');
        $id = $this->as($mine)
            ->postJson('/api/customer/payment-methods', ['kind' => 'cash'])
            ->json('account.paymentMethods.0.id');

        ['token' => $theirs] = $this->signedIn('second@example.com');

        $this->as($theirs)->deleteJson("/api/customer/payment-methods/{$id}")->assertNotFound();
        $this->as($mine)->getJson('/api/customer/me')->assertJsonCount(1, 'account.paymentMethods');
    }
}
