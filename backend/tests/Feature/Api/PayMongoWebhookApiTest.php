<?php

namespace Tests\Feature\Api;

use Tests\TestCase;

/**
 * The signature is the only thing guarding this endpoint, so it is the only
 * thing worth testing hard.
 *
 * The endpoint is public and unauthenticated by necessity — PayMongo carries
 * no token of ours — and it is the sole channel by which a payment is ever
 * asserted. Every test here is a way in that must stay shut.
 */
class PayMongoWebhookApiTest extends TestCase
{
    private const SECRET = 'whsk_test_pretend_this_came_from_the_dashboard';

    private function body(): string
    {
        return json_encode(['data' => ['id' => 'evt_1', 'attributes' => ['type' => 'payment.paid']]]);
    }

    private function sign(string $payload, int $timestamp, string $part = 'te', ?string $secret = null): string
    {
        $signature = hash_hmac('sha256', $timestamp.'.'.$payload, $secret ?? self::SECRET);

        return "t={$timestamp},{$part}={$signature}";
    }

    private function deliver(string $payload, ?string $header): \Illuminate\Testing\TestResponse
    {
        return $this->call(
            'POST',
            '/api/webhooks/paymongo',
            [],
            [],
            [],
            $header === null ? [] : ['HTTP_PAYMONGO_SIGNATURE' => $header],
            $payload,
        );
    }

    public function test_a_correctly_signed_payload_is_accepted(): void
    {
        config(['paymongo.webhook_secret' => self::SECRET, 'paymongo.mode' => 'test']);

        $payload = $this->body();

        $this->deliver($payload, $this->sign($payload, time()))
            ->assertOk()
            ->assertJson(['received' => true]);
    }

    /**
     * The first real delivery failed on this.
     *
     * A session's client key is `cs_<id>_client_<random>` and sits in the
     * payload beside the id, so a scan for `cs_` finds both. Settling the
     * client key looks up a session that does not exist, and the payment sat
     * unsettled with only "settlement for an unknown session" in the log.
     */
    public function test_a_client_key_is_trimmed_to_the_session_id(): void
    {
        config(['paymongo.webhook_secret' => self::SECRET, 'paymongo.mode' => 'test']);

        $settled = [];
        $this->app->bind(\App\Services\Billing\GatewaySettlement::class, function () use (&$settled) {
            return new class($settled) extends \App\Services\Billing\GatewaySettlement
            {
                public function __construct(private array &$seen) {}

                public function settle(string $sessionId): bool
                {
                    $this->seen[] = $sessionId;

                    return false;
                }
            };
        });

        $payload = json_encode(['data' => ['attributes' => ['data' => [
            'id' => 'cs_abc123',
            'attributes' => ['client_key' => 'cs_abc123_client_zzz999'],
        ]]]]);

        $this->deliver($payload, $this->sign($payload, time()))->assertOk();

        // Both spellings collapse to one lookup, on the real id.
        $this->assertSame(['cs_abc123'], $settled);
    }

    public function test_an_unconfigured_endpoint_says_so_rather_than_refusing(): void
    {
        config(['paymongo.webhook_secret' => '']);

        $payload = $this->body();

        $this->deliver($payload, $this->sign($payload, time()))->assertStatus(503);
    }

    public function test_a_request_with_no_signature_is_refused(): void
    {
        config(['paymongo.webhook_secret' => self::SECRET, 'paymongo.mode' => 'test']);

        $this->deliver($this->body(), null)->assertStatus(400);
    }

    public function test_a_forged_signature_is_refused(): void
    {
        config(['paymongo.webhook_secret' => self::SECRET, 'paymongo.mode' => 'test']);

        $payload = $this->body();
        $timestamp = time();

        $this->deliver($payload, "t={$timestamp},te=".str_repeat('a', 64))->assertStatus(400);
    }

    /** The whole point of signing the raw body: a changed body must not verify. */
    public function test_a_body_altered_after_signing_is_refused(): void
    {
        config(['paymongo.webhook_secret' => self::SECRET, 'paymongo.mode' => 'test']);

        $header = $this->sign($this->body(), time());
        $tampered = json_encode(['data' => ['id' => 'evt_1', 'attributes' => ['type' => 'payment.paid', 'amount' => 999999]]]);

        $this->deliver($tampered, $header)->assertStatus(400);
    }

    /** A captured request must not stay replayable forever. */
    public function test_a_stale_signature_is_refused(): void
    {
        config([
            'paymongo.webhook_secret' => self::SECRET,
            'paymongo.mode' => 'test',
            'paymongo.signature_tolerance' => 300,
        ]);

        $payload = $this->body();

        $this->deliver($payload, $this->sign($payload, time() - 3600))->assertStatus(400);
    }

    /**
     * The one that pinning the mode exists for.
     *
     * The header carries a test signature and a live signature side by side.
     * A live endpoint that accepted whichever half matched would accept an
     * event anybody holding a test key could produce.
     */
    public function test_a_test_signature_does_not_satisfy_a_live_endpoint(): void
    {
        config(['paymongo.webhook_secret' => self::SECRET, 'paymongo.mode' => 'live']);

        $payload = $this->body();

        $this->deliver($payload, $this->sign($payload, time(), 'te'))->assertStatus(400);
    }

    public function test_a_signature_made_with_another_secret_is_refused(): void
    {
        config(['paymongo.webhook_secret' => self::SECRET, 'paymongo.mode' => 'test']);

        $payload = $this->body();

        $this->deliver($payload, $this->sign($payload, time(), 'te', 'whsk_someone_elses_endpoint'))
            ->assertStatus(400);
    }
}
