package com.omaykan.seller.core.network

import com.omaykan.seller.core.network.dto.AssignRiderRequestDto
import com.omaykan.seller.core.network.dto.BootstrapDto
import com.omaykan.seller.core.network.dto.ConversationThreadDto
import com.omaykan.seller.core.network.dto.ConversationsDto
import com.omaykan.seller.core.network.dto.CreatePromoCodeRequestDto
import com.omaykan.seller.core.network.dto.DeletedDto
import com.omaykan.seller.core.network.dto.PromoCodeEnvelopeDto
import com.omaykan.seller.core.network.dto.PromoCodesDto
import com.omaykan.seller.core.network.dto.UpdatePromoCodeRequestDto
import com.omaykan.seller.core.network.dto.OrderingRequestDto
import com.omaykan.seller.core.network.dto.ProductImageDto
import com.omaykan.seller.core.network.dto.ProductImageRequestDto
import com.omaykan.seller.core.network.dto.OrderingStateDto
import com.omaykan.seller.core.network.dto.ReplyRequestDto
import com.omaykan.seller.core.network.dto.SyncPushRequestDto
import com.omaykan.seller.core.network.dto.SyncPushResponseDto
import com.omaykan.seller.core.network.dto.UnreadDto
import com.omaykan.seller.core.network.dto.SaveRiderRequestDto
import com.omaykan.seller.core.network.dto.SavedRiderDirectoryDto
import com.omaykan.seller.core.network.dto.SavedRiderEnvelopeDto
import com.omaykan.seller.core.network.dto.SellerOrderEnvelopeDto
import com.omaykan.seller.core.network.dto.SellerOrdersDto
import com.omaykan.seller.core.network.dto.SelectStoreRequestDto
import com.omaykan.seller.core.network.dto.SettlePaymentRequestDto
import com.omaykan.seller.core.network.dto.StaffGoogleRequestDto
import com.omaykan.seller.core.network.dto.StoreDirectoryDto
import com.omaykan.seller.core.network.dto.StoreImageDto
import com.omaykan.seller.core.network.dto.StoreImageRequestDto
import com.omaykan.seller.core.network.dto.StaffSessionDto
import com.omaykan.seller.core.network.dto.StaffSignInDto
import com.omaykan.seller.core.network.dto.StaffSignInRequestDto
import com.omaykan.seller.core.network.dto.UpdateDeliveryStageRequestDto
import com.omaykan.seller.core.network.dto.UpdateStatusRequestDto
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.PUT
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

    /**
     * The shop's catalog, as the register bootstraps it: every product in the
     * organization, this branch's overrides and stock, and who is signed in.
     *
     * The same call the till makes on a cold start. There is no smaller
     * product endpoint for staff, and a second one would be a second copy of
     * the rules about what a branch sells.
     */
    @GET("api/sync/bootstrap")
    suspend fun bootstrap(): BootstrapDto

    /**
     * The till's outbox, and the only way a product is written.
     *
     * Answers 200 with a result per event; a failed event is inside the body,
     * not the status line. See CatalogRepository.save.
     */
    @POST("api/sync/push")
    suspend fun push(@Body request: SyncPushRequestDto): SyncPushResponseDto

    /** Customer threads, most recent first, capped at 100 by the server. */
    @GET("api/seller/conversations")
    suspend fun conversations(): ConversationsDto

    /** The number the home screen's message icon wears. */
    @GET("api/seller/conversations/unread")
    suspend fun unreadMessages(): UnreadDto

    /** One thread. Reading it marks it read for the shop. */
    @GET("api/seller/conversations/{conversationId}")
    suspend fun conversation(@Path("conversationId") conversationId: String): ConversationThreadDto

    @POST("api/seller/conversations/{conversationId}/messages")
    suspend fun reply(
        @Path("conversationId") conversationId: String,
        @Body request: ReplyRequestDto,
    ): ConversationThreadDto

    /**
     * The public shop directory, and the only place a shop's address, map pin
     * and business type are published to anybody but the register.
     *
     * Listed shops only: one with nothing sellable on its shelf yet is left
     * out, so the store profile has to cope with not finding itself.
     */
    @GET("api/stores")
    suspend fun storeDirectory(): StoreDirectoryDto

    /**
     * Replace the shop's photo. Admins and managers only — 403 for anyone
     * else — and JPEG, PNG or WebP under 3MB decoded. Answers with the new,
     * versioned URL, so a cached copy of the old photo is never shown again.
     */
    @PUT("api/seller/store-image")
    suspend fun updateStoreImage(@Body request: StoreImageRequestDto): StoreImageDto

    /**
     * Keep one product photo and get its URL back, to put in the product event.
     * Whoever may change products; JPEG, PNG or WebP under 3MB decoded.
     */
    @POST("api/seller/product-images")
    suspend fun uploadProductImage(@Body request: ProductImageRequestDto): ProductImageDto

    /** The shop's promo codes, newest first. Roles with the Products page; 403 otherwise. */
    @GET("api/seller/promo-codes")
    suspend fun promoCodes(): PromoCodesDto

    /** 422 for a code name already used, retired ones included. */
    @POST("api/seller/promo-codes")
    suspend fun createPromoCode(@Body request: CreatePromoCodeRequestDto): PromoCodeEnvelopeDto

    @PATCH("api/seller/promo-codes/{id}")
    suspend fun updatePromoCode(
        @Path("id") id: String,
        @Body request: UpdatePromoCodeRequestDto,
    ): PromoCodeEnvelopeDto

    /** Retired, not erased: past orders keep naming the code they used. */
    @DELETE("api/seller/promo-codes/{id}")
    suspend fun deletePromoCode(@Path("id") id: String): DeletedDto

    /** Whether the shop is taking online orders right now. */
    @GET("api/seller/ordering")
    suspend fun ordering(): OrderingStateDto

    /**
     * Pause (optionally until a time) or reopen. Any role with the Orders
     * page; 403 otherwise. A pause ends by itself at its resume time.
     */
    @PUT("api/seller/ordering")
    suspend fun setOrdering(@Body request: OrderingRequestDto): OrderingStateDto
}
