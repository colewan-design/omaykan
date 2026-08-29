package com.omaykan.storefront.core.network

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.omaykan.storefront.BuildConfig
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

/** The API origin, so nothing has to reach for BuildConfig to build an image URL. */
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
        // The server sends null for an absent price or pin. Decoding those onto
        // a non-null field with a default is what keeps one odd product from
        // failing the whole catalog.
        explicitNulls = false
        coerceInputValues = true
    }

    @Provides
    @Singleton
    fun provideOkHttp(auth: CustomerAuthInterceptor): OkHttpClient = OkHttpClient.Builder()
        // First in the chain, so the header is on the request the logging
        // interceptor and the cache both see.
        .addInterceptor(auth)
        // A shopper on a jeepney is the normal case, not the edge one. Long
        // enough to survive a handover between cells, short enough that a dead
        // connection surfaces as "no connection" while they still care.
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        // Off by default: a retried POST is a second order. Reads that want it
        // ask for it themselves.
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
    fun provideApi(retrofit: Retrofit): OmaykanApi = retrofit.create(OmaykanApi::class.java)

    /**
     * Google's token endpoint, on the same client. Its calls carry an absolute
     * `@Url`, so the base URL above is never consulted for them and the auth
     * interceptor leaves them alone.
     */
    @Provides
    @Singleton
    fun provideGoogleOAuthApi(retrofit: Retrofit): GoogleOAuthApi =
        retrofit.create(GoogleOAuthApi::class.java)
}
