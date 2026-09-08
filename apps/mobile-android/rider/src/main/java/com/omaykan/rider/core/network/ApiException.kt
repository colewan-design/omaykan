package com.omaykan.rider.core.network

import com.omaykan.rider.core.model.RiderStatus

/**
 * Everything the UI is allowed to know about a failed call.
 *
 * Retrofit's HttpException and java.io.IOException never leave core/network: a
 * screen should be choosing between "say this under the email field" and "you
 * are offline", not reading status codes.
 *
 * The same sealed shape as :app's and :seller's, with two cases this app needs
 * and neither of those has:
 *
 *  - [Gated], because a 403 here is not "you may not do that" but "your account
 *    is not approved yet", and it carries the status that says which of the
 *    three that is.
 *  - [Taken], because two riders tapping the same job a second apart is the
 *    normal case on a busy board, not an error worth apologising for.
 */
sealed class ApiException(
    override val message: String,
    override val cause: Throwable? = null,
) : Exception(message, cause) {

    /** 422. Keys are Laravel's field paths, so map them back onto the field. */
    class Validation(
        message: String,
        val errors: Map<String, List<String>>,
    ) : ApiException(message) {
        fun first(field: String): String? = errors[field]?.firstOrNull()
    }

    /**
     * 401. The token is dead — signed out on another phone, or revoked. The
     * only honest answer is to sign in again.
     */
    class Unauthorized(message: String) : ApiException(message)

    /**
     * 403 from EnsureRiderIsApproved, carrying the account's real status.
     *
     * This is the one failure that is not an error at all: it is the approval
     * gate, and it can arrive mid-shift when an operator suspends someone who
     * is holding a phone. Every repository call surfaces it, and
     * SessionRepository turns it into a screen — so a suspended rider reads why
     * rather than watching a board fail to refresh.
     */
    class Gated(
        message: String,
        val status: RiderStatus,
        val reviewNote: String?,
    ) : ApiException(message)

    /**
     * 403 that is not the approval gate: someone else's delivery.
     *
     * Reachable in practice — a rider with the app open on two phones, one of
     * them holding a job they released on the other.
     */
    class Forbidden(message: String) : ApiException(message)

    /** 404. */
    class NotFound(message: String) : ApiException(message)

    /**
     * 409 from `accept`: another rider got there first.
     *
     * A case of its own because the right response is not an error banner but a
     * refresh — the board the rider is looking at is out of date, and the next
     * tap should be against what is actually still there.
     */
    class Taken(message: String) : ApiException(message)

    /** 429. retryAfterSeconds is present when the server sent the header. */
    class RateLimited(
        message: String,
        val retryAfterSeconds: Long?,
    ) : ApiException(message)

    /** Any other non-2xx. */
    class Server(message: String, val statusCode: Int) : ApiException(message)

    /** No usable connection, a DNS failure, a timeout. */
    class Offline(cause: Throwable?) : ApiException("No connection.", cause)

    /** A body that would not decode, or anything genuinely unforeseen. */
    class Unexpected(cause: Throwable?) : ApiException("Something went wrong.", cause)
}
