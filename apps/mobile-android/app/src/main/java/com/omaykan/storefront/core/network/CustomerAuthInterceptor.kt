package com.omaykan.storefront.core.network

import com.omaykan.storefront.core.auth.CustomerTokenStore
import dagger.Lazy
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Attaches the shopper's bearer token to calls that go to our own API.
 *
 * On every one of them, not only the ones under `/api/customer`. That is what
 * makes a signed-in shopper's order land in their history: POST /api/online-orders
 * reads the token as optional and back-fills the guest contact fields from the
 * account when one is present. Without a token it is still a perfectly good
 * guest order, which is the whole point.
 *
 * Skipped for anything that is not ours. The only such call is the code
 * exchange at Google's token endpoint, and sending our session token to Google
 * would be handing a credential to a third party for no reason at all.
 *
 * The token store is a `Lazy`, and that is load-bearing rather than tidy.
 * OmaykanApp field-injects OkHttpClient, so Hilt builds this interceptor inside
 * `Application.onCreate` — and CustomerTokenStore opens EncryptedSharedPreferences
 * on construction, which means Android keystore work. Injected directly, that
 * lands on the main thread before the first frame, on every cold start, on the
 * cheapest phone the shopper owns. Deferred like this it happens on OkHttp's
 * own thread, on the first request, where waiting on a disk is already the job.
 */
@Singleton
class CustomerAuthInterceptor @Inject constructor(
    private val tokens: Lazy<CustomerTokenStore>,
    @ApiBaseUrl private val baseUrl: String,
) : Interceptor {

    private val apiHost: String? = baseUrl.toHttpUrlOrNull()?.host

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()

        // Host checked before the store is touched, so a call to Google never
        // opens the keystore at all.
        if (apiHost == null ||
            request.url.host != apiHost ||
            // An Authorization header already on the request wins: nothing sets
            // one today, but silently overwriting a caller's own header is the
            // kind of helpfulness that is very hard to debug later.
            request.header("Authorization") != null
        ) {
            return chain.proceed(request)
        }

        val token = tokens.get().current()

        if (token == null) {
            return chain.proceed(request)
        }

        val response = chain.proceed(
            request.newBuilder().header("Authorization", "Bearer $token").build(),
        )

        /*
         * A token our own API has just refused is a dead token — revoked from
         * another device, or cleared by a password reset — and keeping it would
         * break far more than the call that found out. `POST /api/online-orders`
         * takes a bearer token as *optional*, so a stale one turns a working
         * guest checkout into a 401: the shopper cannot order at all until they
         * sign in, which is exactly the gate this app is not allowed to have.
         *
         * Dropped here, at the one place that knows a request carried it.
         * AccountRepository watches the store and moves every screen to signed
         * out; the cart is never touched.
         */
        if (response.code == 401) tokens.get().clear()

        return response
    }
}
