package com.omaykan.storefront.core.network

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit

/**
 * The status codes this app actually distinguishes between.
 *
 * Each of these is a screen behaving differently: a 409 sends the shopper away
 * with an explanation, a 404 tells them to check the code, a 422 lights up one
 * field. Collapsing any two of them into "something went wrong" is the kind of
 * regression nothing else here would catch.
 */
class ApiCallerTest {

    private lateinit var server: MockWebServer
    private lateinit var api: OmaykanApi
    private lateinit var caller: ApiCaller

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()

        val json = Json { ignoreUnknownKeys = true }
        api = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(OmaykanApi::class.java)
        caller = ApiCaller(json)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `409 becomes Conflict, carrying the server's own sentence`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(409)
                .setBody("""{"message":"This store is not set up for online ordering."}"""),
        )

        val failure = runCatching { caller.call { api.stores() } }.exceptionOrNull()

        assertTrue(failure is ApiException.Conflict)
        assertEquals("This store is not set up for online ordering.", failure?.message)
    }

    @Test
    fun `404 becomes NotFound`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(404)
                .setBody("""{"message":"We couldn't find a store with that code."}"""),
        )

        val failure = runCatching { caller.call { api.stores() } }.exceptionOrNull()

        assertTrue(failure is ApiException.NotFound)
    }

    @Test
    fun `422 carries the field paths Laravel used`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(422)
                .setBody(
                    """
                    {"message":"The given data was invalid.",
                     "errors":{"fulfillment.address":["We do not deliver to that address yet."]}}
                    """.trimIndent(),
                ),
        )

        val failure = runCatching { caller.call { api.stores() } }.exceptionOrNull()

        assertTrue(failure is ApiException.Validation)
        assertEquals(
            "We do not deliver to that address yet.",
            (failure as ApiException.Validation).first("fulfillment.address"),
        )
    }

    @Test
    fun `429 keeps Retry-After so the UI can say how long`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(429)
                .setHeader("Retry-After", "37")
                .setBody("""{"message":"Too Many Attempts."}"""),
        )

        val failure = runCatching { caller.call { api.stores() } }.exceptionOrNull()

        assertTrue(failure is ApiException.RateLimited)
        assertEquals(37L, (failure as ApiException.RateLimited).retryAfterSeconds)
    }

    @Test
    fun `a dropped connection is Offline, not Unexpected`() = runTest {
        server.shutdown()

        val failure = runCatching { caller.call { api.stores() } }.exceptionOrNull()

        assertTrue(failure is ApiException.Offline)
    }

    @Test
    fun `a body that will not decode is Unexpected, not a crash`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("not json at all"))

        val failure = runCatching { caller.call { api.stores() } }.exceptionOrNull()

        assertTrue(failure is ApiException.Unexpected)
    }
}
