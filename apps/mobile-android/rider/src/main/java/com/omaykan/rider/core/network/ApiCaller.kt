package com.omaykan.rider.core.network

import com.omaykan.rider.core.model.RiderStatus
import com.omaykan.rider.core.network.dto.ApiErrorDto
import kotlinx.serialization.json.Json
import retrofit2.HttpException
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The single place an HTTP failure becomes an ApiException.
 *
 * Repositories wrap every call in this rather than each inventing its own error
 * handling. Two of the mappings below are the reason it has to be one place:
 *
 *  - A **403** is two different things on this API. From the approval gate it
 *    is "your account is pending" and carries `riderStatus`; from a delivery
 *    action it is "that job is somebody else's". Told apart by the presence of
 *    that field, which `EnsureRiderIsApproved` always sends and `abort_unless`
 *    never does.
 *  - A **409** is only ever `accept` losing a race, and it is not a fault.
 */
@Singleton
class ApiCaller @Inject constructor(
    private val json: Json,
) {
    suspend fun <T> call(block: suspend () -> T): T =
        try {
            block()
        } catch (e: ApiException) {
            throw e
        } catch (e: HttpException) {
            throw map(e)
        } catch (e: IOException) {
            throw ApiException.Offline(e)
        } catch (e: Exception) {
            throw ApiException.Unexpected(e)
        }

    private fun map(e: HttpException): ApiException {
        val response = e.response()
        val body = runCatching { response?.errorBody()?.string() }.getOrNull()
        val parsed = body?.takeIf { it.isNotBlank() }?.let { raw ->
            runCatching { json.decodeFromString<ApiErrorDto>(raw) }.getOrNull()
        }
        val message = parsed?.message?.takeIf { it.isNotBlank() }

        return when (e.code()) {
            401 -> ApiException.Unauthorized(message ?: "Please sign in again.")

            /*
             * The gate, or a job that is not yours.
             *
             * `riderStatus` is the discriminator rather than the message text,
             * because the middleware's three sentences are the kind of copy
             * that gets rewritten and matching on them would break silently the
             * day somebody does.
             */
            403 -> parsed?.riderStatus?.let { status ->
                ApiException.Gated(
                    message = message ?: "Your rider account cannot take work yet.",
                    status = RiderStatus.fromWire(status),
                    reviewNote = parsed.reviewNote?.takeIf { it.isNotBlank() },
                )
            } ?: ApiException.Forbidden(message ?: "That delivery belongs to another rider.")

            404 -> ApiException.NotFound(message ?: "That order is no longer there.")

            409 -> ApiException.Taken(
                message ?: "Another rider took that one. Pull down to refresh the board.",
            )

            /*
             * The server's own sentence, every time one is offered.
             *
             * A 422 here is usually a business rule saying no, and it says why:
             * "Mark the order picked up before delivering it.", "You already
             * picked this order up — call the shop to hand it back." Replacing
             * either with "Please check the details above" would leave a rider
             * standing on a doorstep with no idea what to do next.
             *
             * Registration is the exception — there, a 422 really is about
             * fields, and `errors` carries them for the form to read.
             */
            422 -> ApiException.Validation(
                message ?: "Please check the details above.",
                parsed?.errors.orEmpty(),
            )

            429 -> ApiException.RateLimited(
                message ?: "Too many tries just now. Give it a moment.",
                response?.headers()?.get("Retry-After")?.toLongOrNull(),
            )

            else -> ApiException.Server(message ?: "The server had a problem.", e.code())
        }
    }
}
