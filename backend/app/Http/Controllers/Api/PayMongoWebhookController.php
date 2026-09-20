<?php

namespace App\Http\Controllers\Api;

use App\Http\Controllers\Controller;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\Log;

/**
 * Where PayMongo tells us a payment happened.
 *
 * Public and unauthenticated, because PayMongo is not signed in and carries
 * no token of ours. The signature *is* the authentication, and it is the only
 * one — so nothing in this class believes a single field of the body until
 * `verify()` has passed.
 *
 * ## Why this is load-bearing
 *
 * With GCash the money moves on PayMongo's side. Our server never sees the
 * payer; it learns that a subscription was paid because this endpoint was
 * called. An unverified endpoint is therefore not a leak — it is a button on
 * the open internet that marks any subscription paid.
 *
 * ## What it does not do yet
 *
 * Nothing. It verifies, logs and returns 200. Recording a payment is
 * deliberately absent: `SubscriptionPayment` today means "a merchant says
 * they transferred money and an operator agreed", and a gateway payment is a
 * different claim with a different provenance. Wiring one into the other
 * before that is decided would put unreviewed rows in the operator's queue.
 * See documentation/plan.md §4a — no gateway is still the standing decision.
 *
 * ## Signature format
 *
 * PayMongo's go-live checklist is explicit about the core: "Read the
 * `Paymongo-Signature` header. Compute HMAC-SHA256 of the raw request body
 * with your secret. Compare using a timing-safe equality check."
 *
 * The header's internal shape — `t=<timestamp>,te=<test>,li=<live>`, with the
 * signed string being the timestamp, a literal `.`, and the raw body — is
 * corroborated by their community documentation rather than quoted from a
 * page of theirs, so **confirm it against a real test delivery before this is
 * trusted with live keys.** It is parsed leniently below for that reason: an
 * unrecognised header is refused rather than guessed at.
 */
class PayMongoWebhookController extends Controller
{
    public function handle(Request $request): JsonResponse
    {
        $secret = (string) config('paymongo.webhook_secret');

        if ($secret === '') {
            // Not configured is not the same as refused. Saying so plainly
            // beats a 403 that reads like a bad signature and sends somebody
            // looking in the wrong place.
            Log::warning('PayMongo webhook called with no PAYMONGO_WEBHOOK_SECRET set.');

            return response()->json(['error' => 'webhook not configured'], 503);
        }

        // The *raw* body. A parsed-and-re-encoded one would differ by a space
        // or a key order and fail every time.
        $payload = $request->getContent();

        if (! $this->verify($request->header('Paymongo-Signature'), $payload, $secret)) {
            Log::warning('PayMongo webhook rejected: signature did not verify.');

            return response()->json(['error' => 'invalid signature'], 400);
        }

        $event = json_decode($payload, true);
        $type = data_get($event, 'data.attributes.type', 'unknown');

        Log::info('PayMongo webhook verified.', [
            'type' => $type,
            'id' => data_get($event, 'data.id'),
        ]);

        /*
         * 200 regardless of what the event was.
         *
         * PayMongo retries anything that is not a success, so answering 4xx
         * to an event we simply do not handle would earn the same unhandled
         * event again on a schedule. A signature that verified means the
         * message is genuine; that we have nothing to do with it yet is our
         * business, not a delivery failure.
         */
        return response()->json(['received' => true]);
    }

    /**
     * True when the header proves PayMongo sent exactly this body, recently.
     */
    private function verify(?string $header, string $payload, string $secret): bool
    {
        if ($header === null || $header === '') {
            return false;
        }

        $parts = [];

        foreach (explode(',', $header) as $piece) {
            [$key, $value] = array_pad(explode('=', trim($piece), 2), 2, null);

            if ($key !== null && $value !== null) {
                $parts[$key] = $value;
            }
        }

        $timestamp = $parts['t'] ?? null;

        // `te` for test mode, `li` for live. Pinned by config rather than
        // whichever is present, so a test-mode signature can never satisfy a
        // live-mode endpoint.
        $signature = $parts[config('paymongo.mode') === 'live' ? 'li' : 'te'] ?? null;

        if ($timestamp === null || $signature === null || ! ctype_digit($timestamp)) {
            return false;
        }

        $age = abs(time() - (int) $timestamp);

        if ($age > max(1, (int) config('paymongo.signature_tolerance'))) {
            Log::warning('PayMongo webhook rejected: signature too old.', ['age_seconds' => $age]);

            return false;
        }

        $expected = hash_hmac('sha256', $timestamp.'.'.$payload, $secret);

        // Timing-safe, as their checklist requires: a plain `===` leaks how
        // much of a forged signature was right, one byte at a time.
        return hash_equals($expected, $signature);
    }
}
