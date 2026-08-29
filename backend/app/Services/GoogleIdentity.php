<?php

namespace App\Services;

/**
 * A person as Google just vouched for them.
 *
 * Only the claims we act on. Everything here has been through
 * GoogleIdentityVerifier — an instance of this class existing at all is the
 * statement that the signature, the audience and the expiry all checked out.
 */
final readonly class GoogleIdentity
{
    public function __construct(
        /** Google's stable id for the person. The thing an account is keyed on. */
        public string $sub,
        public string $email,
        public bool $emailVerified,
        public string $name,
        public ?string $picture,
    ) {}
}
