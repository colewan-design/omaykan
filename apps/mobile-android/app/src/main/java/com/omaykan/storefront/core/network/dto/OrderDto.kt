package com.omaykan.storefront.core.network.dto

import kotlinx.serialization.Serializable

/*
 * POST /api/online-orders and GET /api/online-orders/{uuid}.
 *
 * Three facts about this endpoint shape everything below:
 *
 *  - It takes no money. Prices, tax and the delivery fee are recomputed
 *    server-side from the merchant's own records, so the request carries ids
 *    and quantities and nothing else about cost.
 *  - It does not need an account. A bearer token names who is ordering when
 *    there is one; without it a guest order is a perfectly good order.
 *  - fulfillment.lat/lng are all-or-nothing, and a delivery outside the quoted
 *    area comes back as a 422 on `fulfillment.address`.
 */

@Serializable
data class PlaceOrderRequestDto(
    val orgSlug: String,
    val storeCode: String,
    val businessMode: String,
    val items: List<PlaceOrderItemDto>,
    val guest: GuestDto,
    val fulfillment: FulfillmentDto,
    val paymentMethod: String,
)

@Serializable
data class PlaceOrderItemDto(
    val productId: String,
    val quantity: Double,
)

/** Name is required; a phone or an email, at least one of the two. */
@Serializable
data class GuestDto(
    val name: String,
    val phone: String? = null,
    val email: String? = null,
)

@Serializable
data class FulfillmentDto(
    /** "pickup" or "delivery". */
    val method: String,
    val address: String? = null,
    val lat: Double? = null,
    val lng: Double? = null,
)

/** POST /api/online-orders/{uuid}/push-token. The phone's FCM token. */
@Serializable
data class PushTokenRequestDto(
    val token: String,
)

/** 201. The only numbers that matter — the server's, not the phone's. */
@Serializable
data class PlaceOrderResponseDto(
    val orderId: String,
    val ticketNumber: String? = null,
    val totalCents: Long = 0,
    val deliveryFeeCents: Long = 0,
)

/** GET /api/customer/orders — the signed-in shopper's own history. */
@Serializable
data class CustomerOrdersDto(
    val orders: List<TrackedOrderDto> = emptyList(),
)

@Serializable
data class TrackedOrderDto(
    val orderId: String,
    val ticketNumber: String? = null,
    val status: String = "",
    val paymentStatus: String = "",
    val paymentMethod: String? = null,
    val subtotalCents: Long = 0,
    val taxCents: Long = 0,
    val deliveryFeeCents: Long = 0,
    val totalCents: Long = 0,
    val fulfillmentMethod: String = "",
    val deliveryAddress: String? = null,
    val deliveryStage: String? = null,
    val riderName: String? = null,
    val riderPhone: String? = null,
    /**
     * Where the rider is, but only while they are carrying this order.
     *
     * The server withholds it after handover, and it is null altogether for a
     * rider the shop typed in at the counter — that person has no account to
     * report from. Both of those are normal, and the screen falls back to the
     * stage in words. See Order::riderPositionForCustomer.
     */
    val riderPosition: RiderPositionDto? = null,
    val route: RouteDto? = null,
    val placedAt: String? = null,
    val items: List<TrackedOrderItemDto> = emptyList(),
)

@Serializable
data class TrackedOrderItemDto(
    val productId: String = "",
    val name: String = "",
    val quantity: Double = 0.0,
    val unitPriceCents: Long = 0,
    val lineTotalCents: Long = 0,
)

/** A rider's last known fix, as the tracking endpoint serves it. */
@Serializable
data class RiderPositionDto(
    val lat: Double,
    val lng: Double,
    val headingDeg: Double? = null,
    val speedKph: Double? = null,
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
