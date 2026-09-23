<?php

namespace App\Services;

use Illuminate\Support\Facades\Cache;
use Illuminate\Support\Facades\Http;
use RuntimeException;

/**
 * Firebase Cloud Messaging, HTTP v1, with nothing but the service-account key.
 *
 * No Google SDK: all this needs is one signed JWT traded for an access token,
 * and one POST per message. The key file stays on the server, outside the
 * repo, and FIREBASE_CREDENTIALS points at it. Without it
 * {@see self::configured()} is false and nobody calls {@see self::send()} —
 * a server with no Firebase project sends no push, and still takes orders.
 */
class PushSender
{
    public const SENT = 'sent';

    /** The token is dead — app uninstalled, or its data cleared. Forget it. */
    public const INVALID = 'invalid';

    /** Anything else. Worth a log line, not worth deleting the token over. */
    public const FAILED = 'failed';

    private const SCOPE = 'https://www.googleapis.com/auth/firebase.messaging';

    public function configured(): bool
    {
        $path = config('services.firebase.credentials');

        return is_string($path) && $path !== '' && is_readable($path);
    }

    /**
     * @param  array<string, string>  $data  handed to the app as intent extras
     */
    public function send(string $token, string $title, string $body, array $data = [], ?string $tag = null): string
    {
        $key = $this->key();

        $response = Http::withToken($this->accessToken($key))
            ->timeout(10)
            ->post("https://fcm.googleapis.com/v1/projects/{$key['project_id']}/messages:send", [
                'message' => [
                    'token' => $token,
                    'notification' => ['title' => $title, 'body' => $body],
                    'data' => (object) $data,
                    'android' => [
                        'priority' => 'high',
                        'notification' => array_filter([
                            // Must match the channel the app creates, or
                            // Android files it under a generic one.
                            'channel_id' => 'order_updates',
                            // One notification per order: a later update
                            // replaces the earlier rather than stacking.
                            'tag' => $tag,
                        ]),
                    ],
                ],
            ]);

        if ($response->successful()) {
            return self::SENT;
        }

        $code = $response->json('error.details.0.errorCode') ?? $response->json('error.status');

        // UNREGISTERED is the documented answer for a token that will never
        // work again; older responses say the same thing with a bare 404.
        if ($code === 'UNREGISTERED' || $response->status() === 404) {
            return self::INVALID;
        }

        report(new RuntimeException("FCM send failed ({$response->status()}): {$response->body()}"));

        return self::FAILED;
    }

    /** @return array{project_id: string, client_email: string, private_key: string} */
    private function key(): array
    {
        $key = json_decode((string) file_get_contents(config('services.firebase.credentials')), true);

        if (! is_array($key) || empty($key['project_id']) || empty($key['client_email']) || empty($key['private_key'])) {
            throw new RuntimeException('FIREBASE_CREDENTIALS is not a service-account key file.');
        }

        return $key;
    }

    /**
     * An OAuth token for the messaging scope. Google's last an hour; this
     * caches for a little less, so a busy lunch is one exchange, not hundreds.
     */
    private function accessToken(array $key): string
    {
        return Cache::remember('fcm-access-token:'.$key['client_email'], now()->addMinutes(50), function () use ($key) {
            $now = time();
            $jwt = $this->jwt([
                'iss' => $key['client_email'],
                'scope' => self::SCOPE,
                'aud' => 'https://oauth2.googleapis.com/token',
                'iat' => $now,
                'exp' => $now + 3600,
            ], $key['private_key']);

            $response = Http::asForm()->timeout(10)->post('https://oauth2.googleapis.com/token', [
                'grant_type' => 'urn:ietf:params:oauth:grant-type:jwt-bearer',
                'assertion' => $jwt,
            ]);

            $token = $response->json('access_token');
            if (! $response->successful() || ! is_string($token)) {
                throw new RuntimeException("Google token exchange failed ({$response->status()}): {$response->body()}");
            }

            return $token;
        });
    }

    private function jwt(array $claims, string $privateKey): string
    {
        $segments = [
            $this->base64Url(json_encode(['alg' => 'RS256', 'typ' => 'JWT'])),
            $this->base64Url(json_encode($claims)),
        ];

        if (! openssl_sign(implode('.', $segments), $signature, $privateKey, OPENSSL_ALGO_SHA256)) {
            throw new RuntimeException('Could not sign the token request with the service-account key.');
        }

        $segments[] = $this->base64Url($signature);

        return implode('.', $segments);
    }

    private function base64Url(string $value): string
    {
        return rtrim(strtr(base64_encode($value), '+/', '-_'), '=');
    }
}
