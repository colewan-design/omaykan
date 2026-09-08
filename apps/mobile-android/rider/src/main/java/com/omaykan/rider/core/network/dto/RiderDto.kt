package com.omaykan.rider.core.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * Laravel's error envelope: `{message, errors: {field: [msg, …]}}`.
 *
 * With two fields the approval gate adds to it. `EnsureRiderIsApproved` answers
 * a 403 with `riderStatus` and `reviewNote` alongside the message, and those
 * two are what let the app show a pending rider a different screen from a
 * suspended one instead of one generic "forbidden".
 */
@Serializable
data class ApiErrorDto(
    val message: String? = null,
    val errors: Map<String, List<String>> = emptyMap(),
    val riderStatus: String? = null,
    val reviewNote: String? = null,
)

// -- The account -----------------------------------------------------------

/** `Rider::toPortalArray`. */
@Serializable
data class RiderDto(
    val id: String,
    val name: String = "",
    val email: String = "",
    val phone: String = "",
    val licenseNumber: String = "",
    val plateNumber: String = "",
    val status: String? = null,
    val reviewNote: String? = null,
    val reviewedAt: String? = null,
    val createdAt: String? = null,
)

/** What register and login both answer with. */
@Serializable
data class RiderSessionDto(
    val rider: RiderDto,
    val token: String,
)

/** What `GET /api/rider/me` answers with — no token, the same rider. */
@Serializable
data class RiderEnvelopeDto(
    val rider: RiderDto,
)

@Serializable
data class LoginRequestDto(
    val email: String,
    val password: String,
)

// -- Work ------------------------------------------------------------------

@Serializable
data class BoardDto(
    val orders: List<OfferDto> = emptyList(),
)

@Serializable
data class MyDeliveriesDto(
    val active: List<AssignmentDto> = emptyList(),
    val completed: List<AssignmentDto> = emptyList(),
)

/** The envelope `accept` and `stage` answer with. */
@Serializable
data class AssignmentEnvelopeDto(
    val order: AssignmentDto,
)

@Serializable
data class ReleasedDto(
    val released: Boolean = false,
)

@Serializable
data class PickupDto(
    val storeId: String = "",
    val storeName: String = "Shop",
    val address: String? = null,
    val lat: Double? = null,
    val lng: Double? = null,
)

/**
 * `RiderDeliveryController::asOffer`.
 *
 * `ticketNumber` is a JsonElement rather than a String because the column is an
 * integer on the wire for most orders and a string for a few — the web client
 * types it `number | string | null` for the same reason. Decoding it as either
 * and printing the text is cheaper than a custom serializer for a field this
 * app only ever displays.
 *
 * Every other field is optional or defaulted. A build of this app will meet a
 * server older or newer than itself, and one unrecognised order must never cost
 * a rider sight of the other nine.
 */
@Serializable
data class OfferDto(
    val id: String,
    val ticketNumber: JsonElement? = null,
    val placedAt: String? = null,
    val orderStatus: String? = null,
    val pickup: PickupDto = PickupDto(),
    val dropoffArea: String? = null,
    val distanceKm: Double? = null,
    val deliveryFeeCents: Long = 0,
    val itemCount: Int = 0,
    val paymentStatus: String? = null,
    val collectCents: Long = 0,
)

/**
 * `RiderDeliveryController::asAssignment` — literally the offer's fields plus
 * these, which is why this repeats them rather than nesting one inside the
 * other.
 *
 * kotlinx.serialization has no flattening, and the alternative — a custom
 * serializer reading one flat object into two nested classes — is more
 * machinery than the duplication costs. The *model* nests; only the wire type
 * is flat, and Mappers.kt is the one place that knows it.
 */
@Serializable
data class AssignmentDto(
    val id: String,
    val ticketNumber: JsonElement? = null,
    val placedAt: String? = null,
    val orderStatus: String? = null,
    val pickup: PickupDto = PickupDto(),
    val dropoffArea: String? = null,
    val distanceKm: Double? = null,
    val deliveryFeeCents: Long = 0,
    val itemCount: Int = 0,
    val paymentStatus: String? = null,
    val collectCents: Long = 0,
    val deliveryStage: String? = null,
    val acceptedAt: String? = null,
    val deliveryAddress: String? = null,
    val deliveryLat: Double? = null,
    val deliveryLng: Double? = null,
    val customerName: String? = null,
    val customerPhone: String? = null,
    val items: List<AssignmentItemDto> = emptyList(),
)

@Serializable
data class AssignmentItemDto(
    val name: String = "",
    val quantity: Double = 0.0,
)

@Serializable
data class AdvanceRequestDto(val stage: String)

/**
 * One fix, on its way to `RiderPositionController::store`.
 *
 * No order id. The rider posts where *they* are and the server works out who is
 * entitled to know from the orders they are actually carrying — so this app
 * cannot broadcast itself onto a delivery it does not hold, because it never
 * gets to name one.
 */
@Serializable
data class PositionRequestDto(
    val lat: Double,
    val lng: Double,
    val headingDeg: Float? = null,
    val speedKph: Float? = null,
    val accuracyM: Float? = null,
)

/**
 * What the server says back.
 *
 * `nextPingSeconds` is the interesting field: the cadence is the server's
 * decision, not this app's, so it can be slowed for a large fleet without
 * shipping an APK. It comes back fast while the rider is carrying something and
 * slow while they are not — there is nobody to tell, so there is no reason to
 * spend the battery.
 */
@Serializable
data class PositionAckDto(
    val recorded: Boolean = false,
    val activeDeliveries: Int = 0,
    val nextPingSeconds: Int = 60,
)

// -- Changing the account --------------------------------------------------

/**
 * A partial write: only the fields present are touched.
 *
 * The server validates each with `sometimes`, so an omitted one is left alone
 * rather than blanked — which is what lets the account screen send a single
 * changed field instead of the whole profile.
 *
 * There is no email and no licence number here on purpose. The API refuses
 * both: the email is the login handle and the address a reset link goes to, and
 * the licence number is the thing an operator actually approved. See
 * RiderAccountController.
 */
@Serializable
data class ProfileUpdateRequestDto(
    val name: String? = null,
    val phone: String? = null,
    val plateNumber: String? = null,
)

/**
 * `@SerialName` rather than a snake_cased property: Laravel's `confirmed` rule
 * looks for exactly `password_confirmation`, and the register call makes the
 * same exception with its multipart part name.
 */
@Serializable
data class PasswordChangeRequestDto(
    val currentPassword: String,
    val password: String,
    @SerialName("password_confirmation") val passwordConfirmation: String,
)

@Serializable
data class ForgotPasswordRequestDto(val email: String)

/** The one-line answer from forgot-password. */
@Serializable
data class MessageDto(val message: String = "")

// -- Earnings --------------------------------------------------------------

@Serializable
data class EarningsTotalDto(
    val jobs: Int = 0,
    val feeCents: Long = 0,
)

@Serializable
data class EarningsDayDto(
    /** `YYYY-MM-DD`, in Asia/Manila — the day the work was done in. */
    val date: String = "",
    val jobs: Int = 0,
    val feeCents: Long = 0,
)

/**
 * `RiderEarningsController::summary`.
 *
 * Every field defaulted, like the board's: a build of this app will meet a
 * server older than itself, and an earnings screen that fails to decode is
 * worse than one showing zeroes.
 */
@Serializable
data class EarningsDto(
    val currency: String = "PHP",
    val today: EarningsTotalDto = EarningsTotalDto(),
    val week: EarningsTotalDto = EarningsTotalDto(),
    val month: EarningsTotalDto = EarningsTotalDto(),
    val allTime: EarningsTotalDto = EarningsTotalDto(),
    val days: List<EarningsDayDto> = emptyList(),
)
