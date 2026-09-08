package com.omaykan.seller.core.network

import com.omaykan.seller.core.network.dto.AssignRiderRequestDto
import com.omaykan.seller.core.network.dto.DeletedDto
import com.omaykan.seller.core.network.dto.SaveRiderRequestDto
import com.omaykan.seller.core.network.dto.SavedRiderDirectoryDto
import com.omaykan.seller.core.network.dto.SavedRiderEnvelopeDto
import com.omaykan.seller.core.network.dto.SellerOrderEnvelopeDto
import com.omaykan.seller.core.network.dto.SellerOrdersDto
import com.omaykan.seller.core.network.dto.SelectStoreRequestDto
import com.omaykan.seller.core.network.dto.SettlePaymentRequestDto
import com.omaykan.seller.core.network.dto.StaffGoogleRequestDto
import com.omaykan.seller.core.network.dto.StaffSessionDto
import com.omaykan.seller.core.network.dto.StaffSignInDto
import com.omaykan.seller.core.network.dto.StaffSignInRequestDto
import com.omaykan.seller.core.network.dto.UpdateDeliveryStageRequestDto
import com.omaykan.seller.core.network.dto.UpdateStatusRequestDto
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path

/**
 * Every endpoint this app talks to, and no others.
 *
 * All of the order routes sit behind `auth:sanctum` + `merchant.token` and are
 * scoped, server-side, to the store named in the session token's own abilities.
 * That is the whole authorization model here: this app never sends a store id
 * on an order call, and could not reach another shop's orders if it tried.
 *
 * The rate limits are noted where they shape the UI. The two sign-in doors are
 * the tight ones — 10/min each — which is why the sign-in screen submits on a
 * button press and never on a keystroke.
 */
interface SellerApi {

    /**
     * 10/min. Username or email, plus a password.
     *
     * 422 for a wrong password *and* for an account that does not exist — the
     * server refuses to say which, and this app must not guess either. 403 when
     * the account is real but cannot sign in: an unverified email address, a
     * disabled account, or no membership at any shop. Those messages are worth
     * showing verbatim; they each name a different thing to go and do.
     */
    @POST("api/staff/sign-in")
    suspend fun signIn(@Body request: StaffSignInRequestDto): StaffSignInDto

    /**
     * 10/min. The same reply, from a Google ID token.
     *
     * Never creates an account, unlike the shopper equivalent: a staff account
     * is a claim on somebody else's shop, so it is made by that shop. A valid
     * Google account with no membership gets a 403 telling them to ask an admin.
     */
    @POST("api/staff/auth/google")
    suspend fun signInWithGoogle(@Body request: StaffGoogleRequestDto): StaffSignInDto

    /**
     * Chooses the shop this session acts for, and mints the token it acts with.
     *
     * The bearer is passed explicitly because the token from sign-in is not the
     * stored one yet — it reaches this endpoint and nothing else, and the
     * interceptor leaves a request that already carries an Authorization header
     * alone. 403 for a store this account has no membership for.
     */
    @POST("api/staff/session-store")
    suspend fun selectStore(
        @Header("Authorization") bearer: String,
        @Body request: SelectStoreRequestDto,
    ): StaffSessionDto

    /**
     * Retires the token this call arrives on.
     *
     * The device era had no such endpoint: nothing revoked a device token, so a
     * lost phone meant rotating the shop's code and re-pairing every till.
     */
    @POST("api/staff/sign-out")
    suspend fun signOut()

    /**
     * The store's storefront orders, newest first, capped at 100 by the server.
     *
     * In-person sales are deliberately not here: those ride the till's
     * offline-first outbox, and a phone that showed them would be showing a
     * partial, stale copy of the register's own ledger.
     */
    @GET("api/seller/online-orders")
    suspend fun orders(): SellerOrdersDto

    /** preparing → ready → served. */
    @POST("api/seller/online-orders/{orderId}/status")
    suspend fun updateStatus(
        @Path("orderId") orderId: String,
        @Body request: UpdateStatusRequestDto,
    ): SellerOrderEnvelopeDto

    /**
     * Names the rider carrying this order, which is itself the assignment.
     *
     * 422 for an order that is for pickup. An order already out on the road
     * keeps the stage it has — re-assigning mid-delivery must not walk the
     * customer's tracking backwards — so this is also how a shop records that
     * the first rider broke down and a second one took over.
     */
    @POST("api/seller/online-orders/{orderId}/rider")
    suspend fun assignRider(
        @Path("orderId") orderId: String,
        @Body request: AssignRiderRequestDto,
    ): SellerOrderEnvelopeDto

    /**
     * Takes the rider off and puts the order back on the platform board.
     *
     * The undo for the above. 422 once the order has been picked up: the food
     * is physically with the rider by then, and handing it back is a phone
     * call, not a button on a dashboard.
     */
    @DELETE("api/seller/online-orders/{orderId}/rider")
    suspend fun unassignRider(@Path("orderId") orderId: String): SellerOrderEnvelopeDto

    /**
     * The shop's own riders, and the ones who have delivered for it before.
     *
     * Not a directory of the platform's riders, and there deliberately isn't
     * one — that endpoint would be a phone book of every rider in the city,
     * readable by anyone who works at any shop. See SellerRiderController.
     */
    @GET("api/seller/riders")
    suspend fun savedRiders(): SavedRiderDirectoryDto

    /** Keep a rider on file. Links a platform account when the number matches one. */
    @POST("api/seller/riders")
    suspend fun saveRider(@Body request: SaveRiderRequestDto): SavedRiderEnvelopeDto

    /** Forget one. Orders they already carried keep the name recorded on them. */
    @DELETE("api/seller/riders/{savedRiderId}")
    suspend fun deleteSavedRider(@Path("savedRiderId") savedRiderId: String): DeletedDto

    /** assigned → picked_up → delivered. 422 for a pickup order. */
    @POST("api/seller/online-orders/{orderId}/delivery-stage")
    suspend fun updateDeliveryStage(
        @Path("orderId") orderId: String,
        @Body request: UpdateDeliveryStageRequestDto,
    ): SellerOrderEnvelopeDto

    /**
     * Records that the money turned up. Not a charge — there is no gateway.
     *
     * 422 on an order already settled, and that refusal is load-bearing: the
     * payment row this writes is summed into the cash a drawer is expected to
     * hold at shift close, so a duplicate makes an honest till reconcile short.
     * Never retried automatically, at any layer.
     */
    @POST("api/seller/online-orders/{orderId}/settle-payment")
    suspend fun settlePayment(
        @Path("orderId") orderId: String,
        @Body request: SettlePaymentRequestDto,
    ): SellerOrderEnvelopeDto
}
