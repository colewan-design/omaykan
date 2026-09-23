package com.omaykan.seller.core.network

import com.omaykan.seller.core.auth.StaffTokenStore
import dagger.Lazy
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Attaches the signed-in staff token to calls that go to our own API.
 *
 * Every call, not only the ones under `/api/seller`. There is nothing else this
 * app talks to, and the sign-in endpoints run before there is a token to
 * attach, so the narrower rule would only be a rule waiting to be forgotten.
 *
 * The token store is a `Lazy`, and that is load-bearing rather than tidy.
 * SellerApp does not field-inject OkHttp, but Hilt still builds this
 * interceptor the first time anything asks for the client — and StaffTokenStore
 * opens EncryptedSharedPreferences on construction, which means Android keystore
 * work. Injected directly, that could land on the main thread before the first
 * frame. Deferred like this it happens on OkHttp's own thread, on the first
 * request, where waiting on a disk is already the job.
 */
@Singleton
class StaffAuthInterceptor @Inject constructor(
    private val tokens: Lazy<StaffTokenStore>,
    @ApiBaseUrl private val baseUrl: String,
) : Interceptor {

    private val apiHost: String? = baseUrl.toHttpUrlOrNull()?.host

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()

        if (apiHost == null ||
            request.url.host != apiHost ||
            // An Authorization header already on the request wins. Choosing a
            // store sets one — the pending token from sign-in, which is not the
            // stored one — and silently overwriting a caller's own header is the
            // kind of helpfulness that is very hard to debug later.
            request.header("Authorization") != null
        ) {
            return chain.proceed(request)
        }

        val token = tokens.get().current()
            ?: return chain.proceed(request)

        val response = chain.proceed(
            request.newBuilder().header("Authorization", "Bearer $token").build(),
        )

        /*
         * A token our own API has just refused is a dead token, and keeping it
         * would leave the app polling a store it can no longer read while
         * showing the last list it managed to fetch — a merchant reading stale
         * orders and not knowing it.
         *
         * Dropped here, at the one place that knows a request carried it.
         * SessionRepository watches the store and moves every screen back to
         * sign-in.
         *
         * A 403 is deliberately left alone. It means this account is not allowed
         * to do that *particular* thing — someone else's order, a manager-only
         * action — and the token is perfectly good for the rest of the shift.
         */
        if (response.code == 401) tokens.get().clear()

        return response
    }
}
