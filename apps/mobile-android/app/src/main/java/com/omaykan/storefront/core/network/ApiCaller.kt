package com.omaykan.storefront.core.network

import com.omaykan.storefront.core.network.dto.ApiErrorDto
import kotlinx.serialization.json.Json
import retrofit2.HttpException
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The single place an HTTP failure becomes an ApiException.
 *
 * Repositories wrap every call in this rather than each inventing its own error
 * handling, which is what keeps the 409 from the store-code lookup and the 422
 * from checkout still meaningful by the time they reach a screen.
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

    /** As above, but a 404 is an absence rather than a failure. */
    suspend fun <T> callOrNull(block: suspend () -> T): T? =
        try {
            call(block)
        } catch (e: ApiException.NotFound) {
            null
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
            404 -> ApiException.NotFound(message ?: "Not found.")
            409 -> ApiException.Conflict(
                message ?: "This store is not set up for online ordering.",
            )
            422 -> ApiException.Validation(
                message ?: "Please check the details above.",
                parsed?.errors.orEmpty(),
            )
            429 -> ApiException.RateLimited(
                message ?: "Too many attempts. Give it a moment.",
                response?.headers()?.get("Retry-After")?.toLongOrNull(),
            )
            else -> ApiException.Server(message ?: "The server had a problem.", e.code())
        }
    }
}
