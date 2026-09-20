<?php

namespace App\Services\Billing;

use App\Models\PlatformSetting;
use App\Models\Subscription;
use Illuminate\Support\Facades\Http;
use RuntimeException;

/**
 * The only thing that talks to PayMongo.
 *
 * Two calls: open a checkout, and read one back. Nothing here decides whether
 * a subscription is paid — `GatewaySettlement` does that, and it does it by
 * reading a session back rather than believing what arrives at the webhook.
 *
 * ## Why v2
 *
 * `/v2/checkout_sessions` is what PayMongo tells new integrations to use.
 * Hosted checkout rather than a payment intent built here, because the hosted
 * page is what keeps card numbers and GCash credentials off our server
 * entirely — there is nothing to handle, so there is nothing to leak.
 */
class PayMongoGateway
{
    /**
     * Two versions, and the split is not a preference.
     *
     * Creating a checkout session is v2, which is what PayMongo tells new
     * integrations to use. **Reading one back is v1** — `GET
     * /v2/checkout_sessions/{id}` does not exist and answers
     * `{"code":"not_found","detail":"The requested route does not exist"}`,
     * which `readCheckout` turned into a null session and a payment that
     * never settled. Found by running a real test payment end to end; no
     * amount of reading their documentation would have said so.
     */
    private const CREATE_BASE = 'https://api.paymongo.com/v2';

    private const READ_BASE = 'https://api.paymongo.com/v1';

    /** Configured at all? Without keys every gateway path stays shut. */
    public function enabled(): bool
    {
        return (string) config('paymongo.secret_key') !== '';
    }

    /**
     * Open a hosted checkout for one subscription period.
     *
     * Returns the session id and the URL to send the merchant to. The amount
     * is the live platform price, not the one frozen on the subscription row
     * at signup — the same rule the merchant's own screen follows.
     */
    public function openCheckout(Subscription $subscription, string $successUrl, string $cancelUrl): array
    {
        $plan = PlatformSetting::current()->planSettings();
        $amount = (int) ($plan['amountCents'] ?? 0);

        if ($amount <= 0) {
            throw new RuntimeException('Refusing to open a checkout for a zero price.');
        }

        $response = $this->request()->post(self::CREATE_BASE.'/checkout_sessions', [
            'data' => [
                'attributes' => [
                    'line_items' => [[
                        'name' => 'Omaykan subscription',
                        'quantity' => 1,
                        'amount' => $amount,
                        'currency' => 'PHP',
                    ]],
                    // GCash only, deliberately: it is how merchants already
                    // pay, and adding cards would mean a conversation about
                    // chargebacks nobody has had yet.
                    'payment_method_types' => ['gcash'],
                    'success_url' => $successUrl,
                    'cancel_url' => $cancelUrl,
                    'description' => 'Omaykan subscription',
                    /*
                     * Ours, carried through PayMongo and back. The settlement
                     * does not depend on it — it re-reads the session by id —
                     * but it makes a payment traceable to a tenant from
                     * PayMongo's own dashboard, which is where somebody will
                     * be looking when something has gone wrong.
                     */
                    'metadata' => [
                        'subscription_id' => (string) $subscription->id,
                        'organization_id' => (string) $subscription->organization_id,
                    ],
                ],
            ],
        ]);

        if (! $response->successful()) {
            throw new RuntimeException('PayMongo refused to open a checkout: '.$response->status());
        }

        return [
            'id' => (string) $response->json('data.id'),
            'url' => (string) $response->json('data.attributes.checkout_url'),
        ];
    }

    /**
     * Read a checkout session back.
     *
     * This is the authoritative answer to "was it paid", and it is why the
     * webhook does not need to be believed beyond its signature: whatever
     * shape the notification takes, the truth is fetched here with our own
     * secret key.
     */
    public function readCheckout(string $sessionId): ?array
    {
        $response = $this->request()->get(self::READ_BASE.'/checkout_sessions/'.$sessionId);

        return $response->successful() ? ($response->json('data') ?? null) : null;
    }

    /**
     * The first settled payment on a session, or null while none has settled.
     *
     * A session carries a list because a shopper may fail once and succeed on
     * the second try; only a `paid` one counts.
     */
    public function settledPayment(array $session): ?array
    {
        foreach ($session['attributes']['payments'] ?? [] as $payment) {
            $status = $payment['attributes']['status'] ?? $payment['status'] ?? null;

            if ($status === 'paid') {
                return $payment;
            }
        }

        return null;
    }

    private function request()
    {
        // Basic auth with the secret key as the username and no password,
        // which is how PayMongo authenticates every server-side call.
        return Http::withBasicAuth((string) config('paymongo.secret_key'), '')
            ->acceptJson()
            ->timeout(20);
    }
}
