package com.omaykan.rider.core.network

import com.omaykan.rider.core.auth.RiderSessionStore
import dagger.Lazy
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Attaches this rider's token to calls that go to our own API.
 *
 * Every call, not only the ones under `/api/rider`. There is nothing else this
 * app talks to, and register and login run before there is a token to attach,
 * so the narrower rule would only be a rule waiting to be forgotten.
 *
 * The token store is a `Lazy`, and that is load-bearing rather than tidy.
 * RiderApp does not field-inject OkHttp, but Hilt still builds this interceptor
 * the first time anything asks for the client — and RiderSessionStore opens
 * EncryptedSharedPreferences on construction, which means Android keystore
 * work. Injected directly, that could land on the main thread before the first
 * frame. Deferred like this it happens on OkHttp's own thread, on the first
 * request, where waiting on a disk is already the job.
 */
@Singleton
class RiderAuthInterceptor @Inject constructor(
    private val session: Lazy<RiderSessionStore>,
    @ApiBaseUrl private val baseUrl: String,
) : Interceptor {

    private val apiHost: String? = baseUrl.toHttpUrlOrNull()?.host

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()

        if (apiHost == null ||
            request.url.host != apiHost ||
            // An Authorization header already on the request wins. Nothing sets
            // one today, and silently overwriting a caller's own header is the
            // kind of helpfulness that is very hard to debug later.
            request.header("Authorization") != null
        ) {
            return chain.proceed(request)
        }

        val token = session.get().currentToken()
            ?: return chain.proceed(request)

        val response = chain.proceed(
            request.newBuilder().header("Authorization", "Bearer $token").build(),
        )

        /*
         * A token our own API has just refused is a dead token, and keeping it
         * would leave the app polling a board it can no longer read while
         * showing the last one it managed to fetch — a rider heading to a shop
         * for a job the server no longer believes is theirs.
         *
         * Dropped here, at the one place that knows a request carried it.
         * SessionRepository watches the store and moves every screen back to
         * sign-in.
         *
         * A 403 is deliberately left alone, and here that matters more than in
         * the other two apps: the approval gate answers 403 to a *pending*
         * rider whose token is perfectly good and whose account may be approved
         * an hour from now. Clearing it would sign them out every time they
         * checked.
         */
        if (response.code == 401) session.get().clear()

        return response
    }
}
