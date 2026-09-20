<?php

namespace App\Http\Controllers\Api;

use App\Http\Controllers\Controller;
use App\Services\Billing\GatewaySettlement;
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
 * ## What it does not do
 *
 * Decide anything. It verifies, pulls out any checkout session the payload
 * mentions, and hands it to `GatewaySettlement`, which re-reads the session
 * from PayMongo with our own key before believing a word of it. The body is a
 * pointer, never evidence.
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
    public function handle(Request $request, GatewaySettlement $settlement): JsonResponse
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
         * Find a checkout session id anywhere in the payload, and settle it.
         *
         * Deliberately shape-agnostic. PayMongo's payload differs between
         * event types and their documentation does not pin down where the
         * session id sits for each, so rather than guess at one path this
         * looks in the places it can be and gives up quietly otherwise. That
         * is safe because the id is only a *pointer*: GatewaySettlement reads
         * the session back from PayMongo with our own key and believes that,
         * not this body. A wrong guess settles nothing; a missed one is
         * caught when the merchant returns from GCash, or by the next retry.
         */
        foreach ($this->sessionIds($event) as $sessionId) {
            $settlement->settle($sessionId);
        }

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
     * Every `cs_…` identifier the payload mentions, in no particular order.
     *
     * A checkout session id is recognisable on sight, which is what makes a
     * search like this reasonable where guessing a path is not.
     */
    private function sessionIds(?array $event): array
    {
        $found = [];
        // Bound to a variable first: array_walk_recursive takes its subject
        // by reference, and `$event ?? []` is a temporary it cannot bind to.
        $payload = $event ?? [];

        array_walk_recursive($payload, function ($value) use (&$found) {
            if (is_string($value) && str_starts_with($value, 'cs_')) {
                $found[$value] = true;
            }
        });

        return array_keys($found);
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
