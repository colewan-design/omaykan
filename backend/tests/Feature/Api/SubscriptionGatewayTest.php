<?php

namespace Tests\Feature\Api;

use App\Models\Subscription;
use App\Models\SubscriptionPayment;
use App\Services\Billing\GatewaySettlement;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Illuminate\Support\Facades\Http;
use Tests\TestCase;

/**
 * Paying through PayMongo rather than by hand.
 *
 * Nothing here reaches PayMongo: the HTTP client is faked, which is the point
 * — the settlement's contract is "re-read the session and believe that", so
 * the tests are about what it does with each answer it could get back.
 */
class SubscriptionGatewayTest extends TestCase
{
    use RefreshDatabase;

    private const SESSION = 'cs_test_abc123';

    private function subscription(): Subscription
    {
        // The seeded fixture, same as the other billing tests use.
        $this->seed();
        $organization = \App\Models\Organization::query()->firstOrFail();

        return Subscription::query()->firstOrCreate(
            ['organization_id' => $organization->id],
            [
                'status' => Subscription::STATUS_PENDING,
                'plan' => 'standard-monthly',
                'amount_cents' => 49900,
                'gcash_reference' => 'seed-reference',
            ],
        );
    }

    private function pendingCheckout(Subscription $subscription): SubscriptionPayment
    {
        return SubscriptionPayment::query()->create([
            'subscription_id' => $subscription->id,
            'organization_id' => $subscription->organization_id,
            'status' => SubscriptionPayment::STATUS_SUBMITTED,
            'source' => SubscriptionPayment::SOURCE_PAYMONGO,
            'provider_session_id' => self::SESSION,
            'reference' => self::SESSION,
            'amount_cents' => 49900,
        ]);
    }

    private function fakeSession(string $status): void
    {
        Http::fake(['api.paymongo.com/*' => Http::response([
            'data' => [
                'id' => self::SESSION,
                'attributes' => [
                    'payments' => [[
                        'id' => 'pay_test_999',
                        'attributes' => ['status' => $status],
                    ]],
                ],
            ],
        ], 200)]);
    }

    public function test_a_paid_session_records_the_period_and_activates_the_subscription(): void
    {
        config(['paymongo.secret_key' => 'sk_test_x']);
        $subscription = $this->subscription();
        $this->pendingCheckout($subscription);
        $this->fakeSession('paid');

        $this->assertTrue(app(GatewaySettlement::class)->settle(self::SESSION));

        $payment = SubscriptionPayment::query()->where('provider_session_id', self::SESSION)->firstOrFail();
        $this->assertSame(SubscriptionPayment::STATUS_ACCEPTED, $payment->status);
        $this->assertSame('pay_test_999', $payment->provider_payment_id);

        $subscription->refresh();
        $this->assertSame(Subscription::STATUS_ACTIVE, $subscription->status);
        $this->assertNotNull($subscription->current_period_ends_at);
    }

    /** PayMongo retries until it gets a 2xx. A second delivery must buy nothing. */
    public function test_settling_twice_does_not_buy_two_months(): void
    {
        config(['paymongo.secret_key' => 'sk_test_x']);
        $subscription = $this->subscription();
        $this->pendingCheckout($subscription);
        $this->fakeSession('paid');

        $settlement = app(GatewaySettlement::class);
        $this->assertTrue($settlement->settle(self::SESSION));

        $subscription->refresh();
        $first = $subscription->current_period_ends_at->copy();

        $this->assertFalse($settlement->settle(self::SESSION));

        $subscription->refresh();
        $this->assertTrue($first->equalTo($subscription->current_period_ends_at));
        $this->assertSame(1, SubscriptionPayment::query()->where('provider_session_id', self::SESSION)->count());
    }

    public function test_an_unpaid_session_settles_nothing(): void
    {
        config(['paymongo.secret_key' => 'sk_test_x']);
        $subscription = $this->subscription();
        $this->pendingCheckout($subscription);
        $this->fakeSession('awaiting_payment_method');

        $this->assertFalse(app(GatewaySettlement::class)->settle(self::SESSION));

        $this->assertSame(
            SubscriptionPayment::STATUS_SUBMITTED,
            SubscriptionPayment::query()->where('provider_session_id', self::SESSION)->firstOrFail()->status,
        );
        $subscription->refresh();
        $this->assertNull($subscription->current_period_ends_at);
    }

    /** A session we never opened is not ours to act on. */
    public function test_an_unknown_session_is_ignored(): void
    {
        config(['paymongo.secret_key' => 'sk_test_x']);
        Http::fake();

        $this->assertFalse(app(GatewaySettlement::class)->settle('cs_test_not_ours'));
        Http::assertNothingSent();
    }

    /** Paying early adds a month rather than losing the remainder of this one. */
    public function test_paying_early_extends_from_the_existing_period_end(): void
    {
        config(['paymongo.secret_key' => 'sk_test_x']);
        $subscription = $this->subscription();
        $subscription->current_period_ends_at = now()->addDays(10);
        $subscription->save();

        $this->pendingCheckout($subscription);
        $this->fakeSession('paid');

        app(GatewaySettlement::class)->settle(self::SESSION);

        $subscription->refresh();
        $this->assertTrue($subscription->current_period_ends_at->greaterThan(now()->addDays(39)));
    }

    /**
     * Create is v2, read is v1.
     *
     * Pinned by a test because it is exactly the kind of asymmetry a tidy-up
     * would "fix" into one constant, and the failure it causes is silent: a
     * 404 becomes a null session becomes a payment that never settles, with
     * nothing in the log to say the URL was wrong.
     */
    public function test_a_session_is_created_on_v2_and_read_back_on_v1(): void
    {
        config(['paymongo.secret_key' => 'sk_test_x']);
        $subscription = $this->subscription();

        Http::fake([
            'api.paymongo.com/v2/checkout_sessions' => Http::response([
                'data' => ['id' => self::SESSION, 'attributes' => ['checkout_url' => 'https://checkout.paymongo.com/x']],
            ], 200),
            'api.paymongo.com/v1/checkout_sessions/*' => Http::response([
                'data' => ['id' => self::SESSION, 'attributes' => ['payments' => []]],
            ], 200),
        ]);

        $gateway = app(\App\Services\Billing\PayMongoGateway::class);
        $gateway->openCheckout($subscription, 'https://omaykan.com/ok', 'https://omaykan.com/no');
        $gateway->readCheckout(self::SESSION);

        Http::assertSent(fn ($request) => $request->method() === 'POST'
            && $request->url() === 'https://api.paymongo.com/v2/checkout_sessions');

        Http::assertSent(fn ($request) => $request->method() === 'GET'
            && $request->url() === 'https://api.paymongo.com/v1/checkout_sessions/'.self::SESSION);
    }

    /** What we know about the payer is sent, so they do not retype it. */
    public function test_known_billing_details_are_prefilled(): void
    {
        config(['paymongo.secret_key' => 'sk_test_x']);
        $subscription = $this->subscription();

        Http::fake(['api.paymongo.com/v2/checkout_sessions' => Http::response([
            'data' => ['id' => self::SESSION, 'attributes' => ['checkout_url' => 'https://checkout.paymongo.com/x']],
        ], 200)]);

        app(\App\Services\Billing\PayMongoGateway::class)->openCheckout(
            $subscription,
            'https://omaykan.com/ok',
            'https://omaykan.com/no',
            ['name' => 'Aling Nena', 'email' => 'nena@example.com'],
        );

        Http::assertSent(function ($request) {
            $billing = $request->data()['data']['attributes']['billing'] ?? null;

            return $billing === ['name' => 'Aling Nena', 'email' => 'nena@example.com'];
        });
    }

    /**
     * A staff user can sign in by username and have no email at all. An
     * empty string would prefill the form with nothing while still counting
     * as answered, so the field is omitted instead.
     */
    public function test_empty_billing_details_are_omitted_rather_than_sent_blank(): void
    {
        config(['paymongo.secret_key' => 'sk_test_x']);
        $subscription = $this->subscription();

        Http::fake(['api.paymongo.com/v2/checkout_sessions' => Http::response([
            'data' => ['id' => self::SESSION, 'attributes' => ['checkout_url' => 'https://checkout.paymongo.com/x']],
        ], 200)]);

        app(\App\Services\Billing\PayMongoGateway::class)->openCheckout(
            $subscription,
            'https://omaykan.com/ok',
            'https://omaykan.com/no',
            ['name' => 'Aling Nena', 'email' => null],
        );

        Http::assertSent(function ($request) {
            $billing = $request->data()['data']['attributes']['billing'] ?? null;

            return $billing === ['name' => 'Aling Nena'];
        });
    }

    /** The configured methods are what the checkout offers, in order. */
    public function test_the_configured_payment_methods_are_sent(): void
    {
        config([
            'paymongo.secret_key' => 'sk_test_x',
            'paymongo.payment_methods' => ['gcash', 'card', 'qrph'],
        ]);
        $subscription = $this->subscription();

        Http::fake(['api.paymongo.com/v2/checkout_sessions' => Http::response([
            'data' => ['id' => self::SESSION, 'attributes' => ['checkout_url' => 'https://checkout.paymongo.com/x']],
        ], 200)]);

        app(\App\Services\Billing\PayMongoGateway::class)
            ->openCheckout($subscription, 'https://omaykan.com/ok', 'https://omaykan.com/no');

        Http::assertSent(fn ($request) => ($request->data()['data']['attributes']['payment_method_types'] ?? null)
            === ['gcash', 'card', 'qrph']);
    }

    /** Without keys the gateway path stays shut rather than half-working. */
    public function test_the_gateway_is_disabled_without_a_key(): void
    {
        config(['paymongo.secret_key' => '']);

        $this->assertFalse(app(\App\Services\Billing\PayMongoGateway::class)->enabled());
    }
}
