<?php

namespace App\Services;

use RuntimeException;

/**
 * A Google sign-in that could not be believed.
 *
 * Every message on this is written to be shown to the shopper, because the
 * controller turns it straight into a 422 on the `credential` field. They stay
 * vague about *why* on purpose: "issued for a different app" and "failed its
 * signature check" are the same event from the shopper's side — something is
 * wrong with the token — and spelling out which check a forged token tripped
 * only helps whoever forged it.
 */
class GoogleIdentityException extends RuntimeException {}
