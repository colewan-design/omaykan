package com.omaykan.storefront.core.network

import com.omaykan.storefront.core.network.dto.AccountEnvelopeDto
import com.omaykan.storefront.core.network.dto.AddressCreatedDto
import com.omaykan.storefront.core.network.dto.AppReleaseDto
import com.omaykan.storefront.core.network.dto.CatalogDto
import com.omaykan.storefront.core.network.dto.CustomerOrdersDto
import com.omaykan.storefront.core.network.dto.ForgotPasswordRequestDto
import com.omaykan.storefront.core.network.dto.GoogleSignInRequestDto
import com.omaykan.storefront.core.network.dto.LoginRequestDto
import com.omaykan.storefront.core.network.dto.MessageDto
import com.omaykan.storefront.core.network.dto.RegisterRequestDto
import com.omaykan.storefront.core.network.dto.RegistrationDto
import com.omaykan.storefront.core.network.dto.PlaceOrderRequestDto
import com.omaykan.storefront.core.network.dto.PushTokenRequestDto
import com.omaykan.storefront.core.network.dto.PlaceOrderResponseDto
import com.omaykan.storefront.core.network.dto.ResetPasswordRequestDto
import com.omaykan.storefront.core.network.dto.SavedAddressRequestDto
import com.omaykan.storefront.core.network.dto.SavedPaymentMethodRequestDto
import com.omaykan.storefront.core.network.dto.SessionDto
import com.omaykan.storefront.core.network.dto.StoreDirectoryDto
import com.omaykan.storefront.core.network.dto.TrackedOrderDto
import com.omaykan.storefront.core.network.dto.UpdateEmailRequestDto
import com.omaykan.storefront.core.network.dto.UpdatePasswordRequestDto
import com.omaykan.storefront.core.network.dto.UpdateProfileRequestDto
import retrofit2.http.Body
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.DELETE
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Every endpoint this app talks to, and no others.
 *
 * The server's rate limits are noted because they are tight enough to shape the
 * UI: a search box firing on every keystroke would exhaust the directory's
 * 60/min inside a minute of typing. Debounce; never retry-storm.
 */
interface OmaykanApi {

    /** 60/min. lat and lng are all-or-nothing, and only reorder the list. */
    @GET("api/stores")
    suspend fun stores(
        @Query("q") query: String? = null,
        @Query("lat") lat: Double? = null,
        @Query("lng") lng: Double? = null,
    ): StoreDirectoryDto

    /**
     * 60/min. An empty products list is a real answer — the shop has nothing on
     * the shelf — and must never be replaced by a cached one.
     */
    @GET("api/storefront/catalog")
    suspend fun catalog(
        @Query("orgSlug") orgSlug: String,
        @Query("storeCode") storeCode: String,
    ): CatalogDto

    /**
     * 20/min, and it writes — placing an order decrements real stock and mints
     * a ticket the merchant's till will see. No account required; a 422 on
     * `fulfillment.address` is a delivery outside the shop's range.
     */
    @POST("api/online-orders")
    suspend fun placeOrder(@Body request: PlaceOrderRequestDto): PlaceOrderResponseDto

    /** 60/min. Public, keyed on the order's UUID — the forwardable link. */
    @GET("api/online-orders/{orderId}")
    suspend fun trackOrder(@Path("orderId") orderId: String): TrackedOrderDto

    /**
     * 20/min, 204. Same capability as tracking. Asks the server to push to
     * this phone when a rider takes the order; delivery orders only (422).
     */
    @POST("api/online-orders/{orderId}/push-token")
    suspend fun registerPushToken(
        @Path("orderId") orderId: String,
        @Body request: PushTokenRequestDto,
    )

    /*
     * Customer accounts. Every one of these is a convenience and none of them
     * is a gate: `placeOrder` above still takes a guest order with no token at
     * all, and it must keep doing so (mobile-plan.md §5, fact 3).
     */

    /**
     * 10/min. Trades a Google ID token for one of ours. 201 the first time a
     * shopper signs in — the account was created — 200 every time after, and a
     * 422 on `credential` for a token the server would not believe.
     */
    @POST("api/customer/auth/google")
    suspend fun signInWithGoogle(@Body request: GoogleSignInRequestDto): SessionDto

    /**
     * 10/min. 422 on `email` for a wrong password, for an unknown address, and
     * for an unverified one — the server deliberately words the first two
     * identically so this endpoint cannot be used to test which addresses shop
     * here. Surface its sentence rather than inventing one.
     */
    @POST("api/customer/login")
    suspend fun login(@Body request: LoginRequestDto): SessionDto

    /**
     * 5/min. Answers 201 with no token: the account exists but cannot sign in
     * until the emailed link is clicked. 422 on `email` when the address is
     * already taken.
     */
    @POST("api/customer/register")
    suspend fun register(@Body request: RegisterRequestDto): RegistrationDto

    /** 5/min. Always the same reply, registered or not. Never a 404. */
    @POST("api/customer/forgot-password")
    suspend fun forgotPassword(@Body request: ForgotPasswordRequestDto): MessageDto

    /**
     * 5/min. Redeems the token from the reset email and signs in with it.
     *
     * 422 on `token` for a link that has expired or already been used — which
     * is a state worth its own sentence, not a generic failure, because the
     * answer is always "ask for another one".
     */
    @POST("api/customer/reset-password")
    suspend fun resetPassword(@Body request: ResetPasswordRequestDto): SessionDto

    /** Restores the session behind a stored token. A 401 means it is dead. */
    @GET("api/customer/me")
    suspend fun customerAccount(): AccountEnvelopeDto

    /**
     * The shopper's own orders, newest first, capped at 50 by the server.
     *
     * Only orders placed while signed in appear here — a guest order carries no
     * account id, and matching them up by email afterwards would hand someone
     * every order anyone ever placed with that address. Guest orders stay
     * reachable through their own id, which is what this app keeps on the
     * device (CheckoutPrefsStore).
     */
    @GET("api/customer/orders")
    suspend fun customerOrders(): CustomerOrdersDto

    /**
     * One of the shopper's own orders. Narrower than the public tracking
     * endpoint on purpose: asking for someone else's id gets a 404 here.
     */
    @GET("api/customer/orders/{orderId}")
    suspend fun customerOrder(@Path("orderId") orderId: String): TrackedOrderDto

    /** Revokes this device's token only. Other phones stay signed in. */
    @POST("api/customer/logout")
    suspend fun customerLogout()

    /*
     * Profile, credentials, addresses and payment methods.
     *
     * Every one of these answers with the *whole* account rather than the piece
     * that changed, because the pieces are not independent: saving an address
     * can move the default off another one, and deleting the default promotes
     * its neighbour. A reply carrying one row would leave the app patching up a
     * list whose shape it no longer knows.
     */

    /** Name, phone and the four preferences. Omitted fields are left alone. */
    @PATCH("api/customer/account")
    suspend fun updateProfile(@Body request: UpdateProfileRequestDto): AccountEnvelopeDto

    /**
     * 422 on `email` for an address already taken, for a wrong current
     * password, and — before it looks at the payload at all — for a
     * Google-only account, whose address is Google's to change.
     */
    @PATCH("api/customer/account/email")
    suspend fun updateEmail(@Body request: UpdateEmailRequestDto): AccountEnvelopeDto

    /** Succeeding signs out every other device. This one survives. */
    @PATCH("api/customer/account/password")
    suspend fun updatePassword(@Body request: UpdatePasswordRequestDto): AccountEnvelopeDto

    /** 201. The first address saved becomes the default whatever is sent. */
    @POST("api/customer/addresses")
    suspend fun createAddress(@Body request: SavedAddressRequestDto): AddressCreatedDto

    @PATCH("api/customer/addresses/{addressId}")
    suspend fun updateAddress(
        @Path("addressId") addressId: String,
        @Body request: SavedAddressRequestDto,
    ): AccountEnvelopeDto

    /** Deleting the default promotes the oldest of what is left. */
    @DELETE("api/customer/addresses/{addressId}")
    suspend fun deleteAddress(@Path("addressId") addressId: String): AccountEnvelopeDto

    /** 422 on `kind` for a second cash row — it would be an exact duplicate. */
    @POST("api/customer/payment-methods")
    suspend fun createPaymentMethod(@Body request: SavedPaymentMethodRequestDto): AccountEnvelopeDto

    @PATCH("api/customer/payment-methods/{methodId}")
    suspend fun updatePaymentMethod(
        @Path("methodId") methodId: String,
        @Body request: SavedPaymentMethodRequestDto,
    ): AccountEnvelopeDto

    @DELETE("api/customer/payment-methods/{methodId}")
    suspend fun deletePaymentMethod(@Path("methodId") methodId: String): AccountEnvelopeDto

    /** Not implemented server-side yet; a 404 means "nothing published". */
    @GET("api/app-releases/{slug}")
    suspend fun appRelease(@Path("slug") slug: String): AppReleaseDto
}
