package com.omaykan.seller.core.network

import com.omaykan.seller.core.network.dto.GoogleTokenResponseDto
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.POST
import retrofit2.http.Url

/**
 * Google's token endpoint. The one call this app makes to a server that is not
 * ours.
 *
 * It turns the authorization code the browser handed back into an ID token,
 * which is the only thing our own API wants. Doing the exchange here rather
 * than posting the code to the backend keeps `/api/staff/auth/google` to a
 * single payload shape shared with the web register — see SellerDto.
 *
 * `@Url` with an absolute address, so this rides the same OkHttp client and the
 * same Retrofit instance as everything else without a second base URL to
 * configure. The auth interceptor deliberately skips it: our bearer token has
 * no business being sent to Google.
 */
interface GoogleOAuthApi {

    /*
     * No default argument values, deliberately. Retrofit builds this interface
     * with a dynamic proxy, and Kotlin compiles a defaulted interface method
     * into a synthetic bridge that the proxy does not implement — the kind of
     * thing that works until the day it does not. The repository passes all six.
     */
    @FormUrlEncoded
    @POST
    suspend fun exchangeCode(
        @Url url: String,
        @Field("client_id") clientId: String,
        @Field("code") code: String,
        @Field("code_verifier") codeVerifier: String,
        @Field("redirect_uri") redirectUri: String,
        @Field("grant_type") grantType: String,
    ): GoogleTokenResponseDto

    companion object {
        const val TOKEN_ENDPOINT = "https://oauth2.googleapis.com/token"
    }
}
