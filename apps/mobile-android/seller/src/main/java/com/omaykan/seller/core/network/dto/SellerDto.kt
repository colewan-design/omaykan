package com.omaykan.seller.core.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Laravel's error envelope: `{message, errors: {field: [msg, …]}}`. */
@Serializable
data class ApiErrorDto(
    val message: String? = null,
    val errors: Map<String, List<String>> = emptyMap(),
)

// -- Signing in ------------------------------------------------------------

@Serializable
data class StaffSignInRequestDto(
    /** Username or email. Accounts made from a username alone still exist. */
    val identifier: String,
    val password: String,
)

@Serializable
data class StaffGoogleRequestDto(
    /** A Google ID token. Verified server-side; nothing here is trusted. */
    val credential: String,
)

/**
 * `POST /api/staff/sign-in` and `/api/staff/auth/google`.
 *
 * The token here reaches only the store picker. What everything else runs on
 * comes back from [StaffSessionDto], and the two are not interchangeable.
 */
@Serializable
data class StaffSignInDto(
    val token: String,
    val user: StaffUserDto,
    val stores: List<StaffStoreDto> = emptyList(),
)

@Serializable
data class StaffUserDto(
    val id: String,
    val fullName: String,
    val username: String? = null,
    val email: String? = null,
)

@Serializable
data class StaffStoreDto(
    val id: String,
    val name: String,
    val code: String,
    val organizationSlug: String? = null,
    val role: String? = null,
)

@Serializable
data class SelectStoreRequestDto(
    val storeId: String,
)

/** `POST /api/staff/session-store`. The token the rest of the app uses. */
@Serializable
data class StaffSessionDto(
    val token: String,
    val user: StaffUserDto,
    val store: StaffStoreDto,
)

/**
 * Google's token endpoint reply.
 *
 * Only the `id_token`, because that is the only field this app sends on to
 * `/api/staff/auth/google` — the same artefact the web register's button
 * produces. One payload, one verification path.
 *
 * There is no client secret in this exchange. The app is a public OAuth client
 * and the `code_verifier` is what proves the code is being redeemed by whoever
 * requested it — see GoogleAuthFlow.
 */
@Serializable
data class GoogleTokenResponseDto(
    @SerialName("id_token")
    val idToken: String = "",
)

// -- Orders ----------------------------------------------------------------

@Serializable
data class SellerOrdersDto(
    val orders: List<SellerOrderDto> = emptyList(),
)

/** The envelope every write endpoint answers with. */
@Serializable
data class SellerOrderEnvelopeDto(
    val order: SellerOrderDto,
)

/**
 * `SellerOrderController::asSummary`, as far as this app reads it.
 *
 * Every field is optional or defaulted. That is not defensiveness for its own
 * sake: this payload is deliberately shaped like the till's OrderSummary and
 * carries columns that only make sense there (table number, the void audit,
 * tendered cash), and a build of this app will meet a server older or newer
 * than itself. One unrecognised order must never cost the merchant sight of the
 * other nine.
 */
@Serializable
data class SellerOrderDto(
    val id: String,
    val ticketNumber: String? = null,
    val customerName: String? = null,
    val status: String? = null,
    val paymentStatus: String? = null,
    val paymentMethod: String? = null,
    val subtotalCents: Long = 0,
    /** What a promo code took off, before tax. */
    val discountCents: Long = 0,
    /** "Promo WELCOME10" — null when nothing came off. */
    val discountLabel: String? = null,
    val totalCents: Long = 0,
    val deliveryFeeCents: Long = 0,
    val createdAt: String? = null,
    val fulfillmentMethod: String? = null,
    val deliveryAddress: String? = null,
    val deliveryStage: String? = null,
    val riderName: String? = null,
    val riderId: String? = null,
    val riderPosition: RiderPositionDto? = null,
    val route: RouteDto? = null,
    val riderPhone: String? = null,
    val guestContact: GuestContactDto? = null,
    val items: List<SellerOrderItemDto> = emptyList(),
)

@Serializable
data class GuestContactDto(
    val name: String? = null,
    val phone: String? = null,
    val email: String? = null,
)

@Serializable
data class SellerOrderItemDto(
    val productId: String = "",
    val name: String = "",
    val quantity: Double = 0.0,
    val unitPriceCents: Long = 0,
    val lineTotalCents: Long = 0,
)

// -- Writes ----------------------------------------------------------------

@Serializable
data class UpdateStatusRequestDto(val status: String)

@Serializable
data class UpdateDeliveryStageRequestDto(val stage: String)

@Serializable
data class AssignRiderRequestDto(
    /**
     * One of two ways to say who is carrying this.
     *
     * Null when the rider came off the shop's own list — [savedRiderId] names
     * them instead, and the server reads the name and number off that row so a
     * saved platform rider gets a real `rider_id` and the order lands in their
     * app. See SellerOrderController::assignRider.
     */
    val riderName: String? = null,
    val savedRiderId: String? = null,
    /** Remember a typed-in rider for next time. Ignored when picking a saved one. */
    val saveRider: Boolean = false,
    val riderPhone: String? = null,
)

/**
 * Settling an unpaid order.
 *
 * `tenderedCents` and `changeCents` are omitted rather than sent as zero. The
 * server reads an absent tender as "the exact total", which is the truth for a
 * GCash transfer and for a card; sending 0 would write a payment row claiming
 * the customer handed over nothing, and that row is summed into what a drawer
 * is expected to hold at shift close.
 */
@Serializable
data class SettlePaymentRequestDto(
    @SerialName("paymentMethod") val paymentMethod: String,
    @SerialName("tenderedCents") val tenderedCents: Long? = null,
    @SerialName("changeCents") val changeCents: Long? = null,
)

/**
 * The rider's last known fix, as `SellerOrderController::asSummary` serves it.
 *
 * Ungated for the shop, unlike the customer's copy: the merchant is the other
 * party to this delivery for its whole life, and somebody chasing a late order
 * needs the last position even after it is marked delivered. There is no
 * ongoing exposure in that — a rider only reports while carrying something, so
 * "after delivery" is a frozen fix, not a live trace.
 */
@Serializable
data class RiderPositionDto(
    val lat: Double,
    val lng: Double,
    val headingDeg: Double? = null,
    val speedKph: Double? = null,
    val accuracyM: Double? = null,
    val at: String? = null,
    val ageSeconds: Int = 0,
    /** Computed server-side, so a phone with the wrong clock cannot disagree. */
    val stale: Boolean = false,
)

/** The two fixed ends of the trip: the shop, and the door. */
@Serializable
data class RouteDto(
    val pickup: RoutePointDto = RoutePointDto(),
    val dropoff: RoutePointDto = RoutePointDto(),
)

@Serializable
data class RoutePointDto(
    val name: String? = null,
    val address: String? = null,
    val lat: Double? = null,
    val lng: Double? = null,
)

/** What the shop's own rider list looks like — `GET /api/seller/riders`. */
@Serializable
data class SavedRiderDirectoryDto(
    val saved: List<SavedRiderDto> = emptyList(),
    val recent: List<RecentRiderDto> = emptyList(),
)

@Serializable
data class SavedRiderDto(
    val id: String,
    val riderId: String? = null,
    val name: String = "",
    val phone: String? = null,
    val note: String? = null,
    val onPlatform: Boolean = false,
    val timesUsed: Int = 0,
    val lastUsedAt: String? = null,
    val status: String? = null,
    val online: Boolean = false,
)

@Serializable
data class RecentRiderDto(
    val riderId: String,
    val name: String = "",
    val phone: String? = null,
    val status: String? = null,
    val online: Boolean = false,
)

@Serializable
data class SavedRiderEnvelopeDto(val savedRider: SavedRiderDto)

/** What the shop sends to keep a rider on file. */
@Serializable
data class SaveRiderRequestDto(
    /**
     * Accepted only for a rider this shop has already worked with — the
     * "recent" list. Everyone else is found by the phone number the shop
     * already has, which is the property that stops this being a way to bind
     * an arbitrary account by guessing UUIDs.
     */
    val riderId: String? = null,
    val name: String,
    val phone: String? = null,
    val note: String? = null,
)

/** `{deleted: true}` — there is no row left to describe. */
@Serializable
data class DeletedDto(val deleted: Boolean = false)
