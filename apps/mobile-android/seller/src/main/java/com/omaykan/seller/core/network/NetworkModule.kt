package com.omaykan.seller.core.network

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.omaykan.seller.BuildConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton

/** The API origin. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApiBaseUrl

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    @ApiBaseUrl
    fun provideBaseUrl(): String = BuildConfig.API_BASE_URL.trimEnd('/')

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        // The order payload carries a dozen columns this app has no screen for,
        // and nulls throughout — an unassigned rider, a pickup order's absent
        // delivery stage. Decoding those onto defaults is what keeps one odd
        // order from failing the whole list.
        explicitNulls = false
        coerceInputValues = true
    }

    @Provides
    @Singleton
    fun provideOkHttp(auth: StaffAuthInterceptor): OkHttpClient = OkHttpClient.Builder()
        // First in the chain, so the header is on the request the logging
        // interceptor sees.
        .addInterceptor(auth)
        /*
         * Say we want JSON, on every request.
         *
         * Retrofit's converter never sends this, and Laravel's behaviour when a
         * request does not ask for JSON is to *redirect* an unauthenticated
         * caller to `route('login')`. This API has no such route, so a dead
         * token came back as a 500 — an error the app can only show as "The
         * server had a problem" — rather than the 401 that clears the token and
         * moves the screen to sign-in. A rider sat on that banner forever,
         * re-polling with a credential the server had already refused.
         *
         * Fixed on the server as well — bootstrap/app.php renders every error
         * under the api prefix as JSON regardless — and still stated here: this
         * is what the client actually accepts, and it costs one header.
         */
        .addInterceptor { chain ->
            chain.proceed(
                chain.request().newBuilder()
                    .header("Accept", "application/json")
                    .build(),
            )
        }
        // A shop with one bar of signal is the normal case, not the edge one.
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        /*
         * Off, and this one matters more here than in the shopper app.
         *
         * A retried settle-payment is a second payment row against a drawer
         * somebody has to reconcile at the end of their shift. The server
         * refuses the duplicate with a 422 — see SellerOrderController — but
         * the right place to not send it twice is here, before it is sent.
         */
        .retryOnConnectionFailure(false)
        .build()

    @Provides
    @Singleton
    fun provideRetrofit(
        client: OkHttpClient,
        json: Json,
        @ApiBaseUrl baseUrl: String,
    ): Retrofit = Retrofit.Builder()
        .baseUrl("$baseUrl/")
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    @Provides
    @Singleton
    fun provideApi(retrofit: Retrofit): SellerApi = retrofit.create(SellerApi::class.java)

    /**
     * Google's token endpoint, on the same client as everything else.
     *
     * It rides this Retrofit instance because its one call carries an absolute
     * `@Url`, so there is no second base URL to configure — and the auth
     * interceptor is host-scoped, so the shop's bearer token is never sent to
     * Google.
     */
    @Provides
    @Singleton
    fun provideGoogleOAuthApi(retrofit: Retrofit): GoogleOAuthApi =
        retrofit.create(GoogleOAuthApi::class.java)
}
