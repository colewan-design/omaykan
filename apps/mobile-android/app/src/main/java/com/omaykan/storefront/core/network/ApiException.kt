package com.omaykan.storefront.core.network

/**
 * Everything the UI is allowed to know about a failed call.
 *
 * Retrofit's HttpException and java.io.IOException never leave core/network: a
 * screen should be choosing between "say this on the address field" and "you
 * are offline", not reading status codes.
 */
sealed class ApiException(
    override val message: String,
    override val cause: Throwable? = null,
) : Exception(message, cause) {

    /**
     * 422. Keys are the field paths Laravel used — "fulfillment.address", not
     * "address" — so map them back onto the field rather than into a toast.
     */
    class Validation(
        message: String,
        val errors: Map<String, List<String>>,
    ) : ApiException(message) {
        fun first(field: String): String? = errors[field]?.firstOrNull()
    }

    /** 401. The token is dead. Clear it and offer sign-in; never drop the cart. */
    class Unauthorized(message: String) : ApiException(message)

    /** 404. */
    class NotFound(message: String) : ApiException(message)

    /**
     * 409. Today this means one thing: the shop exists, but it is not set up for
     * online ordering. That is a real answer about a real shop, not a bad code.
     */
    class Conflict(message: String) : ApiException(message)

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
