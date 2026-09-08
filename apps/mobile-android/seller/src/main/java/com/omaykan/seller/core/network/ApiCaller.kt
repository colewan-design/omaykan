package com.omaykan.seller.core.network

import com.omaykan.seller.core.network.dto.ApiErrorDto
import kotlinx.serialization.json.Json
import retrofit2.HttpException
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The single place an HTTP failure becomes an ApiException.
 *
 * Repositories wrap every call in this rather than each inventing its own error
 * handling, which is what keeps the 422 from a double settlement — "That order
 * has already been settled." — still meaningful by the time it reaches a sheet.
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
            401 -> ApiException.Unauthorized(message ?: "This device is no longer paired.")
            403 -> ApiException.Forbidden(message ?: "This device cannot do that.")
            404 -> ApiException.NotFound(message ?: "Not found.")
            /*
             * The server's own sentence, every time one is offered.
             *
             * A 422 here is rarely a malformed field — it is a business rule
             * saying no, and it says why: "That order has already been
             * settled.", "That order is for pickup, not delivery." Replacing
             * either with "Please check the details above" would leave a
             * merchant staring at a form with nothing wrong on it.
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
