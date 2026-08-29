<?php

namespace App\Services;

use Illuminate\Support\Facades\Cache;
use Illuminate\Support\Facades\Http;
use Throwable;

/**
 * Turns a Google ID token into a GoogleIdentity, or refuses.
 *
 * Both front ends arrive here with the same artefact — a signed JWT — by
 * different roads. The web storefront gets one straight from Google Identity
 * Services when the shopper presses the button; the Android app runs an
 * authorization-code exchange with PKCE behind a Custom Tab and pulls the
 * `id_token` out of the reply. One token type, one verification path, one
 * endpoint on our side.
 *
 * Verified here rather than by asking Google's `tokeninfo` endpoint. That call
 * is a network round trip on the critical path of every sign-in, and it fails
 * the way an outage fails — everyone at once. The signature is checkable
 * offline against Google's published certificates, which are cached, so an
 * ordinary sign-in touches nothing but our own CPU.
 *
 * No JWT library. Google publishes its signing certificates in PEM at
 * `oauth2/v1/certs`, which is exactly what `openssl_verify` wants, so the whole
 * job is a base64url decode and one openssl call — not worth a dependency, and
 * a dependency here would be a dependency in the login path.
 */
class GoogleIdentityVerifier
{
    /** PEM form, keyed by `kid`. The JWK form at v3/certs would need assembling. */
    private const CERTS_URL = 'https://www.googleapis.com/oauth2/v1/certs';

    private const CACHE_KEY = 'google:oauth:certs';

    /** Google mints tokens with either spelling of the issuer. Both are it. */
    private const ISSUERS = ['accounts.google.com', 'https://accounts.google.com'];

    /**
     * Clock skew allowed on `exp` and `iat`. A phone with a minute-fast clock
     * is not an attack, and Google's own libraries allow the same.
     */
    private const LEEWAY_SECONDS = 60;

    public function verify(string $idToken): GoogleIdentity
    {
        $audiences = $this->audiences();

        if ($audiences === []) {
            throw new GoogleIdentityException('Google sign-in is not configured on this server.');
        }

        $parts = explode('.', $idToken);

        if (count($parts) !== 3) {
            throw new GoogleIdentityException('That Google sign-in could not be read.');
        }

        [$encodedHeader, $encodedPayload, $encodedSignature] = $parts;

        $header = $this->decodeSegment($encodedHeader);
        $claims = $this->decodeSegment($encodedPayload);
        $signature = $this->base64UrlDecode($encodedSignature);

        // Pinned, not read-and-trusted. `alg: none` is the oldest JWT hole
        // there is, and accepting an HMAC alg would let a token be signed with
        // the public key everybody already has.
        if (($header['alg'] ?? null) !== 'RS256') {
            throw new GoogleIdentityException('That Google sign-in used an unexpected signature.');
        }

        $kid = $header['kid'] ?? null;

        if (! is_string($kid) || $kid === '') {
            throw new GoogleIdentityException('That Google sign-in named no signing key.');
        }

        $this->assertSignature($encodedHeader.'.'.$encodedPayload, $signature, $kid);
        $this->assertClaims($claims, $audiences);

        $email = strtolower(trim((string) ($claims['email'] ?? '')));

        if ($email === '') {
            throw new GoogleIdentityException('That Google account did not share an email address.');
        }

        return new GoogleIdentity(
            sub: (string) $claims['sub'],
            email: $email,
            // Google sends the flag as a real boolean, and as the string "true"
            // from some older paths. Anything not affirmatively true is treated
            // as unverified.
            emailVerified: filter_var($claims['email_verified'] ?? false, FILTER_VALIDATE_BOOL),
            name: trim((string) ($claims['name'] ?? '')) ?: $this->nameFromEmail($email),
            picture: $this->cleanPicture($claims['picture'] ?? null),
        );
    }

    /**
     * Every client id a token may be addressed to.
     *
     * More than one, because the web button and the Android app are separate
     * OAuth clients in the Google console — they have to be, the console keys
     * an Android client on the package name and signing certificate — and each
     * stamps its own id into `aud`.
     *
     * @return list<string>
     */
    private function audiences(): array
    {
        $configured = [
            config('services.google.client_id'),
            config('services.google.android_client_id'),
            ...(array) config('services.google.extra_client_ids', []),
        ];

        return array_values(array_unique(array_filter(array_map(
            fn ($id) => is_string($id) ? trim($id) : '',
            $configured,
        ))));
    }

    /**
     * @param  array<string, mixed>  $claims
     * @param  list<string>  $audiences
     */
    private function assertClaims(array $claims, array $audiences): void
    {
        if (! in_array((string) ($claims['iss'] ?? ''), self::ISSUERS, true)) {
            throw new GoogleIdentityException('That sign-in did not come from Google.');
        }

        // The check that stops a token minted for somebody else's app being
        // replayed at ours. Without it, any Google login anywhere is a login
        // here.
        if (! in_array((string) ($claims['aud'] ?? ''), $audiences, true)) {
            throw new GoogleIdentityException('That Google sign-in was issued for a different app.');
        }

        if (! isset($claims['sub']) || (string) $claims['sub'] === '') {
            throw new GoogleIdentityException('That Google sign-in identified nobody.');
        }

        $now = time();

        if ((int) ($claims['exp'] ?? 0) < $now - self::LEEWAY_SECONDS) {
            throw new GoogleIdentityException('That Google sign-in has expired. Please try again.');
        }

        if ((int) ($claims['iat'] ?? 0) > $now + self::LEEWAY_SECONDS) {
            throw new GoogleIdentityException('That Google sign-in is dated in the future.');
        }
    }

    private function assertSignature(string $signed, string $signature, string $kid): void
    {
        $certificate = $this->certificates()[$kid] ?? null;

        // An unknown kid is the ordinary look of a key rotation, not of an
        // attack, so the cache is dropped and Google asked once more. Only a
        // second miss is a failure.
        if ($certificate === null) {
            $certificate = $this->certificates(fresh: true)[$kid] ?? null;
        }

        if ($certificate === null) {
            throw new GoogleIdentityException('That Google sign-in was signed with an unknown key.');
        }

        $key = openssl_pkey_get_public($certificate);

        if ($key === false) {
            throw new GoogleIdentityException('Could not read the Google signing key.');
        }

        if (openssl_verify($signed, $signature, $key, OPENSSL_ALGO_SHA256) !== 1) {
            throw new GoogleIdentityException('That Google sign-in failed its signature check.');
        }
    }

    /**
     * Google's current signing certificates, PEM by `kid`.
     *
     * Cached for an hour — well inside Google's own rotation, which it
     * advertises in the response's Cache-Control and performs gradually,
     * keeping the retiring key servable long after the new one appears.
     *
     * @return array<string, string>
     */
    private function certificates(bool $fresh = false): array
    {
        if ($fresh) {
            Cache::forget(self::CACHE_KEY);
        }

        return Cache::remember(self::CACHE_KEY, now()->addHour(), function (): array {
            try {
                $response = Http::timeout(5)->retry(2, 200)->get(self::CERTS_URL);
            } catch (Throwable $e) {
                throw new GoogleIdentityException('Could not reach Google to check that sign-in.', previous: $e);
            }

            if (! $response->successful()) {
                throw new GoogleIdentityException('Could not reach Google to check that sign-in.');
            }

            $certificates = array_filter(
                (array) ($response->json() ?? []),
                fn ($pem, $kid) => is_string($kid) && is_string($pem),
                ARRAY_FILTER_USE_BOTH,
            );

            if ($certificates === []) {
                throw new GoogleIdentityException('Google published no signing keys.');
            }

            return $certificates;
        });
    }

    /** @return array<string, mixed> */
    private function decodeSegment(string $segment): array
    {
        $decoded = json_decode($this->base64UrlDecode($segment), true);

        if (! is_array($decoded)) {
            throw new GoogleIdentityException('That Google sign-in could not be read.');
        }

        return $decoded;
    }

    private function base64UrlDecode(string $value): string
    {
        $decoded = base64_decode(strtr($value, '-_', '+/'), true);

        if ($decoded === false) {
            throw new GoogleIdentityException('That Google sign-in could not be read.');
        }

        return $decoded;
    }

    /**
     * Only https, and only for the avatar. The value ends up in an <img src>
     * on the storefront, so a `javascript:` URL smuggled through a claim would
     * be stored XSS with a Google signature on it.
     */
    private function cleanPicture(mixed $picture): ?string
    {
        if (! is_string($picture) || $picture === '') {
            return null;
        }

        return str_starts_with($picture, 'https://') ? $picture : null;
    }

    /** A Google account with no name is rare but allowed; the inbox half will do. */
    private function nameFromEmail(string $email): string
    {
        return ucfirst(strtok($email, '@') ?: 'Shopper');
    }
}
