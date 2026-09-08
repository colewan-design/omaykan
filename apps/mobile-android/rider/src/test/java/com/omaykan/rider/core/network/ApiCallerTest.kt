package com.omaykan.rider.core.network

import com.omaykan.rider.core.model.RiderStatus
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException

/**
 * The one place a status code becomes something a screen can act on.
 *
 * The 403 cases are the reason this file exists: the same code means "your
 * account is pending" and "that job belongs to someone else", and telling them
 * apart wrongly either signs a working rider out of their board or leaves a
 * suspended one tapping Accept into a wall.
 */
class ApiCallerTest {

    private val caller = ApiCaller(Json { ignoreUnknownKeys = true })

    private fun error(code: Int, body: String, retryAfter: String? = null): HttpException {
        val request = Request.Builder().url("https://omaykan.com/api/rider/board").build()
        val builder = Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message("error")

        retryAfter?.let { builder.header("Retry-After", it) }

        return HttpException(
            retrofit2.Response.error<Any>(
                body.toResponseBody("application/json".toMediaType()),
                builder.build(),
            ),
        )
    }

    private suspend fun failWith(code: Int, body: String, retryAfter: String? = null) =
        runCatching { caller.call<Unit> { throw error(code, body, retryAfter) } }.exceptionOrNull()

    @Test
    fun `the approval gate becomes Gated, carrying the status and the note`() = runTest {
        val thrown = failWith(
            403,
            """{"message":"Your rider account is suspended.",
                "riderStatus":"suspended","reviewNote":"Plate does not match."}""",
        )

        val gated = thrown as ApiException.Gated
        assertEquals(RiderStatus.Suspended, gated.status)
        assertEquals("Plate does not match.", gated.reviewNote)
        assertEquals("Your rider account is suspended.", gated.message)
    }

    /**
     * A 403 with no `riderStatus` is `abort_unless` on somebody else's job, not
     * the gate. Mistaking it for the gate would move a working rider to a
     * status screen for a mis-tap.
     */
    @Test
    fun `a 403 without a rider status is a forbidden job, not the gate`() = runTest {
        val thrown = failWith(403, """{"message":"That delivery belongs to another rider."}""")

        assertTrue(thrown is ApiException.Forbidden)
        assertEquals("That delivery belongs to another rider.", thrown?.message)
    }

    @Test
    fun `losing the race for a job is Taken, with the server's sentence`() = runTest {
        val thrown = failWith(
            409,
            """{"message":"Another rider took that one. Pull down to refresh the board."}""",
        )

        assertTrue(thrown is ApiException.Taken)
        assertEquals(
            "Another rider took that one. Pull down to refresh the board.",
            thrown?.message,
        )
    }

    @Test
    fun `a 422 keeps the per-field errors registration needs`() = runTest {
        val thrown = failWith(
            422,
            """{"message":"The given data was invalid.",
                "errors":{"email":["There is already a rider account with that email."],
                          "plateImage":["The plate image must be an image."]}}""",
        )

        val validation = thrown as ApiException.Validation
        assertEquals(
            "There is already a rider account with that email.",
            validation.first("email"),
        )
        assertEquals("The plate image must be an image.", validation.first("plateImage"))
        assertNull(validation.first("name"))
    }

    /** A business-rule 422 has no `errors` and its message is the whole point. */
    @Test
    fun `a 422 with no fields still carries the server's own sentence`() = runTest {
        val thrown = failWith(
            422,
            """{"message":"Mark the order picked up before delivering it."}""",
        )

        val validation = thrown as ApiException.Validation
        assertEquals("Mark the order picked up before delivering it.", validation.message)
        assertTrue(validation.errors.isEmpty())
    }

    @Test
    fun `a 429 keeps the Retry-After header`() = runTest {
        val thrown = failWith(429, """{"message":"Too Many Attempts."}""", retryAfter = "45")

        assertEquals(45L, (thrown as ApiException.RateLimited).retryAfterSeconds)
    }

    @Test
    fun `an unparseable body still produces something a screen can show`() = runTest {
        val thrown = failWith(500, "<html>502 Bad Gateway</html>")

        val server = thrown as ApiException.Server
        assertEquals(500, server.statusCode)
        assertEquals("The server had a problem.", server.message)
    }

    @Test
    fun `a dropped connection is Offline`() = runTest {
        val thrown = runCatching {
            caller.call<Unit> { throw java.io.IOException("unexpected end of stream") }
        }.exceptionOrNull()

        assertTrue(thrown is ApiException.Offline)
    }

    /** An ApiException already thrown downstream passes through unwrapped. */
    @Test
    fun `an ApiException is not wrapped a second time`() = runTest {
        val original = ApiException.Taken("gone")
        val thrown = runCatching { caller.call<Unit> { throw original } }.exceptionOrNull()

        assertTrue(thrown === original)
    }
}
