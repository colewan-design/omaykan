package com.omaykan.storefront.core.data

import com.omaykan.storefront.core.model.Cart
import com.omaykan.storefront.core.model.Contact
import com.omaykan.storefront.core.model.DeliveryDestination
import com.omaykan.storefront.core.model.DeliveryRoute
import com.omaykan.storefront.core.model.FulfillmentMethod
import com.omaykan.storefront.core.model.PaymentPreference
import com.omaykan.storefront.core.model.PlacedOrder
import com.omaykan.storefront.core.model.RiderPosition
import com.omaykan.storefront.core.model.RoutePoint
import com.omaykan.storefront.core.model.TrackedOrder
import com.omaykan.storefront.core.model.TrackedOrderItem
import com.omaykan.storefront.core.network.ApiCaller
import com.omaykan.storefront.core.network.OmaykanApi
import com.omaykan.storefront.core.network.dto.FulfillmentDto
import com.omaykan.storefront.core.network.dto.GuestDto
import com.omaykan.storefront.core.network.dto.PlaceOrderItemDto
import com.omaykan.storefront.core.network.dto.PlaceOrderRequestDto
import com.omaykan.storefront.core.network.dto.RoutePointDto
import com.omaykan.storefront.core.network.dto.TrackedOrderDto
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OrderRepository @Inject constructor(
    private val api: OmaykanApi,
    private val caller: ApiCaller,
) {
    /**
     * Place the basket.
     *
     * Never retried automatically, at any layer: OkHttp has connection retries
     * off, and nothing above catches a timeout and tries again. A retried POST
     * here is a second real order, against real stock, that a merchant will
     * prepare and nobody will collect. If it fails, the shopper decides.
     */
    suspend fun place(
        cart: Cart,
        businessMode: String,
        contact: Contact,
        method: FulfillmentMethod,
        destination: DeliveryDestination?,
        payment: PaymentPreference,
    ): PlacedOrder {
        val dto = caller.call {
            api.placeOrder(
                PlaceOrderRequestDto(
                    orgSlug = cart.ref.orgSlug,
                    storeCode = cart.ref.storeCode,
                    businessMode = businessMode,
                    items = cart.lines.map {
                        PlaceOrderItemDto(productId = it.product.id, quantity = it.quantity)
                    },
                    guest = GuestDto(
                        name = contact.name.trim(),
                        phone = contact.phone.trim().takeIf { it.isNotEmpty() },
                        email = contact.email.trim().takeIf { it.isNotEmpty() },
                    ),
                    fulfillment = FulfillmentDto(
                        method = method.wire,
                        address = destination?.address?.trim()?.takeIf { it.isNotEmpty() },
                        // All-or-nothing: sending one coordinate is a 422, and
                        // would also be a way to dodge the distance surcharge.
                        lat = destination?.takeIf { it.pinned }?.lat,
                        lng = destination?.takeIf { it.pinned }?.lng,
                    ),
                    paymentMethod = payment.wire,
                ),
            )
        }

        return PlacedOrder(
            orderId = dto.orderId,
            ticketNumber = dto.ticketNumber,
            totalCents = dto.totalCents,
            deliveryFeeCents = dto.deliveryFeeCents,
        )
    }

    suspend fun track(orderId: String): TrackedOrder =
        caller.call { api.trackOrder(orderId) }.toModel()

    /**
     * The signed-in shopper's own orders. Requires a token; a 401 means the
     * session died and the interceptor has already cleared it.
     */
    suspend fun history(): List<TrackedOrder> =
        caller.call { api.customerOrders() }.orders.map { it.toModel() }

    /**
     * The orders this device placed, signed in or not.
     *
     * Each is fetched by its own public id — the same unguessable capability
     * the tracking link uses — so a guest's history works with no account at
     * all. Anything that will not load is dropped rather than failing the list:
     * an order the server has since forgotten should not cost the shopper
     * sight of the four that are still real.
     */
    /**
     * Everything this shopper can see, newest first.
     *
     * Two sources, deliberately both. Signed in, the server answers and the
     * history follows the person to a new phone. Whether or not they are, this
     * device's own order ids are fetched too — a guest order carries no account
     * id and is findable only by its unguessable id, so without this a shopper
     * who did not sign in loses sight of an order the moment they leave the
     * confirmation screen.
     *
     * Signing in does not retroactively claim the orders placed before it: the
     * server will not match them up by email, for good reason (see
     * CustomerOrderController), so the only place the two meet is here. Ids the
     * account already returned are dropped from the device list, because an
     * order placed while signed in appears in both and showing it twice reads
     * as two orders.
     */
    suspend fun mine(signedIn: Boolean, deviceIds: List<String>): List<TrackedOrder> {
        val account = if (signedIn) history() else emptyList()
        val known = account.map { it.orderId }.toSet()
        val guest = trackAll(deviceIds.filterNot { it in known })

        return (account + guest).sortedByDescending { it.placedAt.orEmpty() }
    }

    suspend fun trackAll(orderIds: List<String>): List<TrackedOrder> = coroutineScope {
        orderIds
            .map { id -> async { runCatching { track(id) }.getOrNull() } }
            .awaitAll()
            .filterNotNull()
    }
}

private fun TrackedOrderDto.toModel() = TrackedOrder(
    orderId = orderId,
    ticketNumber = ticketNumber,
    status = status,
    paymentStatus = paymentStatus,
    paymentMethod = paymentMethod,
    subtotalCents = subtotalCents,
    taxCents = taxCents,
    deliveryFeeCents = deliveryFeeCents,
    totalCents = totalCents,
    fulfillmentMethod = fulfillmentMethod,
    deliveryAddress = deliveryAddress,
    deliveryStage = deliveryStage,
    riderName = riderName,
    riderPhone = riderPhone,
    riderPosition = riderPosition?.let {
        RiderPosition(
            lat = it.lat,
            lng = it.lng,
            headingDeg = it.headingDeg,
            ageSeconds = it.ageSeconds,
            stale = it.stale,
        )
    },
    route = route?.let {
        DeliveryRoute(
            pickup = it.pickup.toModel(),
            dropoff = it.dropoff.toModel(),
        )
    },
    placedAt = placedAt,
    items = items.map {
        TrackedOrderItem(
            productId = it.productId,
            name = it.name,
            quantity = it.quantity,
            unitPriceCents = it.unitPriceCents,
            lineTotalCents = it.lineTotalCents,
        )
    },
)

private fun RoutePointDto.toModel() = RoutePoint(
    name = name?.takeIf { it.isNotBlank() },
    address = address?.takeIf { it.isNotBlank() },
    lat = lat,
    lng = lng,
)
