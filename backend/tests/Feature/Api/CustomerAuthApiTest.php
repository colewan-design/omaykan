<?php

namespace Tests\Feature\Api;

use App\Models\CustomerAccount;
use App\Models\Order;
use App\Models\Product;
use App\Notifications\CustomerPasswordReset;
use Illuminate\Foundation\Testing\DatabaseMigrations;
use Illuminate\Support\Facades\Notification;
use Tests\TestCase;

/**
 * The storefront's own identity — accounts for the people who buy.
 *
 * DatabaseMigrations rather than RefreshDatabase, matching the other API
 * tests: placing an order broadcasts from DB::afterCommit, which never fires
 * inside a transaction that gets rolled back.
 */
class CustomerAuthApiTest extends TestCase
{
    use DatabaseMigrations;

    /** @return array{token: string, account: array<string, mixed>} */
    private function register(array $overrides = []): array
    {
        $payload = array_merge([
            'name' => 'Christian Colewan',
            'email' => 'shopper@example.com',
            'phone' => '09171234567',
            'password' => 'baguio-pines-2026',
            'password_confirmation' => 'baguio-pines-2026',
        ], $overrides);

        $response = $this->postJson('/api/customer/register', $payload)->assertCreated();

        return ['token' => $response->json('token'), 'account' => $response->json('account')];
    }

    /**
     * Guards are container singletons and cache the user they resolved, and
     * the container survives between requests inside one test. Without the
     * forget, the second request in a test authenticates as the first one's
     * owner however the header changed — which would make every "one customer
     * cannot touch another's" assertion here pass for the wrong reason.
     */
    private function withCustomer(string $token): self
    {
        $this->app['auth']->forgetGuards();

        return $this->withHeader('Authorization', "Bearer {$token}");
    }

    // -- Registration and sign-in -------------------------------------------

    public function test_registration_creates_an_account_and_returns_a_token(): void
    {
        $result = $this->register();

        $this->assertNotEmpty($result['token']);
        $this->assertSame('shopper@example.com', $result['account']['email']);
        $this->assertSame([], $result['account']['addresses']);
        // Defaults arrive without ever having been written.
        $this->assertTrue($result['account']['preferences']['emailUpdates']);
        $this->assertFalse($result['account']['preferences']['marketingEmails']);
    }

    public function test_registration_rejects_an_email_that_differs_only_in_case(): void
    {
        $this->register();

        $this->postJson('/api/customer/register', [
            'name' => 'Someone Else',
            'email' => 'SHOPPER@Example.com',
            'password' => 'another-good-password',
            'password_confirmation' => 'another-good-password',
        ])->assertStatus(422)->assertJsonValidationErrors('email');
    }

    public function test_registration_rejects_a_short_password(): void
    {
        $this->postJson('/api/customer/register', [
            'name' => 'Christian Colewan',
            'email' => 'shopper@example.com',
            'password' => 'short',
            'password_confirmation' => 'short',
        ])->assertStatus(422)->assertJsonValidationErrors('password');
    }

    public function test_sign_in_works_and_is_case_insensitive_on_the_email(): void
    {
        $this->register();

        $this->postJson('/api/customer/login', [
            'email' => 'SHOPPER@example.com',
            'password' => 'baguio-pines-2026',
        ])->assertOk()->assertJsonPath('account.name', 'Christian Colewan');
    }

    public function test_sign_in_says_the_same_thing_for_a_wrong_password_and_an_unknown_email(): void
    {
        $this->register();

        $wrongPassword = $this->postJson('/api/customer/login', [
            'email' => 'shopper@example.com',
            'password' => 'not-the-password',
        ])->assertStatus(422);

        $unknownEmail = $this->postJson('/api/customer/login', [
            'email' => 'nobody@example.com',
            'password' => 'not-the-password',
        ])->assertStatus(422);

        // Identical replies: this endpoint must not answer "does this person
        // shop here?" for anyone who cares to ask.
        $this->assertSame(
            $wrongPassword->json('errors.email'),
            $unknownEmail->json('errors.email'),
        );
    }

    public function test_me_needs_a_token(): void
    {
        $this->getJson('/api/customer/me')->assertUnauthorized();
    }

    public function test_signing_out_revokes_only_this_device(): void
    {
        $phone = $this->register()['token'];
        $laptop = $this->postJson('/api/customer/login', [
            'email' => 'shopper@example.com',
            'password' => 'baguio-pines-2026',
        ])->assertOk()->json('token');

        $this->withCustomer($phone)->postJson('/api/customer/logout')->assertOk();

        $this->withCustomer($phone)->getJson('/api/customer/me')->assertUnauthorized();
        $this->withCustomer($laptop)->getJson('/api/customer/me')->assertOk();
    }

    // -- Guard separation ---------------------------------------------------

    public function test_a_customer_token_cannot_reach_the_seller_api(): void
    {
        $token = $this->register()['token'];

        // auth:sanctum has no configured provider, so Sanctum itself will
        // happily accept this token; `merchant.token` is what turns it away.
        // 403 rather than 401 — the token is real, just not for this API.
        $this->withCustomer($token)->getJson('/api/seller/online-orders')->assertForbidden();
        $this->withCustomer($token)->getJson('/api/user')->assertForbidden();
    }

    // -- Profile ------------------------------------------------------------

    public function test_preferences_are_merged_not_replaced(): void
    {
        $token = $this->register()['token'];

        $this->withCustomer($token)
            ->patchJson('/api/customer/account', ['preferences' => ['smsUpdates' => true]])
            ->assertOk()
            ->assertJsonPath('account.preferences.smsUpdates', true)
            // Untouched by a single-key write.
            ->assertJsonPath('account.preferences.emailUpdates', true);

        $this->withCustomer($token)
            ->patchJson('/api/customer/account', ['preferences' => ['substitutions' => 'refund']])
            ->assertOk()
            ->assertJsonPath('account.preferences.substitutions', 'refund')
            ->assertJsonPath('account.preferences.smsUpdates', true);
    }

    public function test_changing_the_email_requires_the_current_password(): void
    {
        $token = $this->register()['token'];

        $this->withCustomer($token)
            ->patchJson('/api/customer/account/email', [
                'email' => 'attacker@example.com',
                'currentPassword' => 'guessing',
            ])
            ->assertStatus(422)
            ->assertJsonValidationErrors('currentPassword');

        $this->withCustomer($token)
            ->patchJson('/api/customer/account/email', [
                'email' => 'new@example.com',
                'currentPassword' => 'baguio-pines-2026',
            ])
            ->assertOk()
            ->assertJsonPath('account.email', 'new@example.com');
    }

    public function test_changing_the_password_signs_out_other_devices_but_not_this_one(): void
    {
        $phone = $this->register()['token'];
        $laptop = $this->postJson('/api/customer/login', [
            'email' => 'shopper@example.com',
            'password' => 'baguio-pines-2026',
        ])->json('token');

        $this->withCustomer($phone)
            ->patchJson('/api/customer/account/password', [
                'currentPassword' => 'baguio-pines-2026',
                'password' => 'a-brand-new-password',
                'password_confirmation' => 'a-brand-new-password',
            ])
            ->assertOk();

        $this->withCustomer($phone)->getJson('/api/customer/me')->assertOk();
        $this->withCustomer($laptop)->getJson('/api/customer/me')->assertUnauthorized();
    }

    // -- Addresses ----------------------------------------------------------

    private function addAddress(string $token, array $overrides = []): array
    {
        return $this->withCustomer($token)
            ->postJson('/api/customer/addresses', array_merge([
                'label' => 'Home',
                'line1' => '14 Marcoville Road',
                'barangay' => 'Bakakeng',
                'city' => 'Baguio City',
            ], $overrides))
            ->assertCreated()
            ->json();
    }

    public function test_the_first_address_becomes_the_default_and_the_second_does_not(): void
    {
        $token = $this->register()['token'];

        $first = $this->addAddress($token);
        $this->assertTrue($first['account']['addresses'][0]['isDefault']);

        $second = $this->addAddress($token, ['label' => 'Work', 'line1' => '2 Session Road']);
        $defaults = array_column($second['account']['addresses'], 'isDefault');
        $this->assertSame([true, false], $defaults);
    }

    public function test_making_an_address_default_clears_the_previous_one(): void
    {
        $token = $this->register()['token'];
        $this->addAddress($token);
        $workId = $this->addAddress($token, ['label' => 'Work', 'line1' => '2 Session Road'])['addressId'];

        $response = $this->withCustomer($token)
            ->patchJson("/api/customer/addresses/{$workId}", ['isDefault' => true])
            ->assertOk();

        $addresses = collect($response->json('account.addresses'));
        $this->assertSame(1, $addresses->where('isDefault', true)->count());
        $this->assertSame($workId, $addresses->firstWhere('isDefault', true)['id']);
    }

    public function test_deleting_the_default_promotes_another_address(): void
    {
        $token = $this->register()['token'];
        $homeId = $this->addAddress($token)['addressId'];
        $this->addAddress($token, ['label' => 'Work', 'line1' => '2 Session Road']);

        $response = $this->withCustomer($token)
            ->deleteJson("/api/customer/addresses/{$homeId}")
            ->assertOk();

        $addresses = $response->json('account.addresses');
        $this->assertCount(1, $addresses);
        $this->assertTrue($addresses[0]['isDefault']);
    }

    public function test_one_customer_cannot_touch_anothers_address(): void
    {
        $mine = $this->register()['token'];
        $addressId = $this->addAddress($mine)['addressId'];

        $theirs = $this->register([
            'email' => 'someone-else@example.com',
            'password' => 'a-different-password',
            'password_confirmation' => 'a-different-password',
        ])['token'];

        $this->withCustomer($theirs)
            ->patchJson("/api/customer/addresses/{$addressId}", ['label' => 'Stolen'])
            ->assertNotFound();

        $this->withCustomer($theirs)
            ->deleteJson("/api/customer/addresses/{$addressId}")
            ->assertNotFound();
    }

    // -- Payment methods ----------------------------------------------------

    public function test_cash_can_only_be_saved_once_but_wallets_can_repeat(): void
    {
        $token = $this->register()['token'];

        $this->withCustomer($token)->postJson('/api/customer/payment-methods', ['kind' => 'cash'])->assertCreated();
        $this->withCustomer($token)->postJson('/api/customer/payment-methods', ['kind' => 'cash'])
            ->assertStatus(422)
            ->assertJsonValidationErrors('kind');

        $this->withCustomer($token)
            ->postJson('/api/customer/payment-methods', ['kind' => 'ewallet', 'detail' => '09171234567'])
            ->assertCreated();
        $this->withCustomer($token)
            ->postJson('/api/customer/payment-methods', ['kind' => 'ewallet', 'detail' => '09189998888'])
            ->assertCreated()
            ->assertJsonCount(3, 'account.paymentMethods');
    }

    public function test_an_ewallet_needs_a_number(): void
    {
        $token = $this->register()['token'];

        $this->withCustomer($token)
            ->postJson('/api/customer/payment-methods', ['kind' => 'ewallet'])
            ->assertStatus(422)
            ->assertJsonValidationErrors('detail');
    }

    // -- Orders -------------------------------------------------------------

    private function placeOrder(?string $token, array $guest = []): string
    {
        $product = Product::query()->where('sku', 'ESP-0001')->firstOrFail();

        if ($token === null) {
            // Guest checkout has to be genuinely tokenless — see withCustomer
            // for why a stale guard would otherwise answer for it.
            $this->app['auth']->forgetGuards();
        }

        $request = $token === null ? $this->withoutHeader('Authorization') : $this->withCustomer($token);

        return $request->postJson('/api/online-orders', array_filter([
            'orgSlug' => 'demo-coffee',
            'storeCode' => 'main',
            'businessMode' => 'coffee-shop',
            'items' => [['productId' => $product->id, 'quantity' => 1]],
            'guest' => $guest ?: null,
            'fulfillment' => ['method' => 'pickup'],
        ]))->assertCreated()->json('orderId');
    }

    public function test_an_order_placed_while_signed_in_belongs_to_the_account(): void
    {
        $this->seed();
        $token = $this->register()['token'];

        $orderId = $this->placeOrder($token);

        // The contact details came off the account — the form sent none.
        $order = Order::query()->findOrFail($orderId);
        $this->assertNotNull($order->customer_account_id);
        $this->assertSame('Christian Colewan', $order->guest_contact['name']);
        $this->assertSame('shopper@example.com', $order->guest_contact['email']);

        $this->withCustomer($token)
            ->getJson('/api/customer/orders')
            ->assertOk()
            ->assertJsonCount(1, 'orders')
            ->assertJsonPath('orders.0.orderId', $orderId);
    }

    public function test_a_guest_order_stays_unattached(): void
    {
        $this->seed();
        $token = $this->register()['token'];

        // Same email, no token: this must NOT be claimed by the account, or
        // anyone could harvest an order list by typing someone's address.
        $orderId = $this->placeOrder(null, ['name' => 'Christian Colewan', 'email' => 'shopper@example.com']);

        $this->assertNull(Order::query()->findOrFail($orderId)->customer_account_id);
        $this->withCustomer($token)->getJson('/api/customer/orders')->assertOk()->assertJsonCount(0, 'orders');
    }

    public function test_a_customer_cannot_fetch_another_customers_order_through_the_account_route(): void
    {
        $this->seed();
        $mine = $this->register()['token'];
        $orderId = $this->placeOrder($mine);

        $theirs = $this->register([
            'email' => 'someone-else@example.com',
            'password' => 'a-different-password',
            'password_confirmation' => 'a-different-password',
        ])['token'];

        $this->withCustomer($theirs)->getJson("/api/customer/orders/{$orderId}")->assertNotFound();
    }

    // -- Password reset -----------------------------------------------------

    public function test_forgot_password_answers_the_same_way_for_an_unknown_address(): void
    {
        Notification::fake();
        $this->register();

        $known = $this->postJson('/api/customer/forgot-password', ['email' => 'shopper@example.com'])->assertOk();
        $unknown = $this->postJson('/api/customer/forgot-password', ['email' => 'nobody@example.com'])->assertOk();

        $this->assertSame($known->json('message'), $unknown->json('message'));
        Notification::assertCount(1);
    }

    public function test_a_reset_link_sets_a_new_password_and_revokes_every_session(): void
    {
        Notification::fake();
        $oldToken = $this->register()['token'];

        $this->postJson('/api/customer/forgot-password', ['email' => 'shopper@example.com'])->assertOk();

        $account = CustomerAccount::findByEmail('shopper@example.com');
        $resetToken = null;

        Notification::assertSentTo($account, CustomerPasswordReset::class, function ($notification) use ($account, &$resetToken) {
            $url = $notification->toMail($account)->actionUrl;
            // The link has to land on the portal, not on a Blade route that
            // this application does not have.
            $this->assertStringContainsString('/account?', $url);
            parse_str(parse_url($url, PHP_URL_QUERY) ?: '', $query);
            $resetToken = $query['token'] ?? null;

            return $resetToken !== null;
        });

        $this->postJson('/api/customer/reset-password', [
            'token' => $resetToken,
            'email' => 'shopper@example.com',
            'password' => 'a-freshly-chosen-password',
            'password_confirmation' => 'a-freshly-chosen-password',
        ])->assertOk()->assertJsonPath('account.email', 'shopper@example.com');

        // Whoever had the old session no longer does — that is the point of
        // resetting a password you think somebody else knows.
        $this->withCustomer($oldToken)->getJson('/api/customer/me')->assertUnauthorized();

        $this->postJson('/api/customer/login', [
            'email' => 'shopper@example.com',
            'password' => 'a-freshly-chosen-password',
        ])->assertOk();
    }

    public function test_a_used_reset_token_stops_working(): void
    {
        Notification::fake();
        $this->register();
        $this->postJson('/api/customer/forgot-password', ['email' => 'shopper@example.com'])->assertOk();

        $account = CustomerAccount::findByEmail('shopper@example.com');
        $resetToken = null;

        Notification::assertSentTo($account, CustomerPasswordReset::class, function ($notification) use ($account, &$resetToken) {
            parse_str(parse_url($notification->toMail($account)->actionUrl, PHP_URL_QUERY) ?: '', $query);
            $resetToken = $query['token'] ?? null;

            return true;
        });

        $body = [
            'token' => $resetToken,
            'email' => 'shopper@example.com',
            'password' => 'a-freshly-chosen-password',
            'password_confirmation' => 'a-freshly-chosen-password',
        ];

        $this->postJson('/api/customer/reset-password', $body)->assertOk();
        $this->postJson('/api/customer/reset-password', $body)
            ->assertStatus(422)
            ->assertJsonValidationErrors('token');
    }
}
