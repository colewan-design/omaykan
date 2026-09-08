package com.omaykan.seller.core.network

/**
 * Everything the UI is allowed to know about a failed call.
 *
 * Retrofit's HttpException and java.io.IOException never leave core/network: a
 * screen should be choosing between "say this under the code field" and "you
 * are offline", not reading status codes.
 *
 * The same sealed shape as :app's, with one case this app needs and the
 * storefront does not — [Forbidden]. A seller call is scoped to the store the
 * session signed in to, so a 403 here means something specific and worth its
 * own sentence.
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
     * 401. The token is dead — signed out elsewhere, or retired by an admin.
     * The only honest answer is to sign in again.
     */
    class Unauthorized(message: String) : ApiException(message)

    /**
     * 403. The account is real and this particular thing is not theirs to do:
     * an order at another shop, an action only a manager has, or — at sign-in —
     * an unverified address, a disabled account, or no membership anywhere.
     * The server's own sentence says which, and each names a different fix.
     */
    class Forbidden(message: String) : ApiException(message)

    /** 404. */
    class NotFound(message: String) : ApiException(message)

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
