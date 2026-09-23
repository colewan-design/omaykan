package com.omaykan.seller.core.data

import com.omaykan.seller.core.model.DeliveryRoute
import com.omaykan.seller.core.model.DeliveryStage
import com.omaykan.seller.core.model.FulfillmentMethod
import com.omaykan.seller.core.model.OrderStatus
import com.omaykan.seller.core.model.RecentRider
import com.omaykan.seller.core.model.RiderDirectory
import com.omaykan.seller.core.model.RiderPosition
import com.omaykan.seller.core.model.RoutePoint
import com.omaykan.seller.core.model.SavedRider
import com.omaykan.seller.core.model.SellerOrder
import com.omaykan.seller.core.model.SellerOrderItem
import com.omaykan.seller.core.network.dto.RecentRiderDto
import com.omaykan.seller.core.network.dto.RiderPositionDto
import com.omaykan.seller.core.network.dto.RouteDto
import com.omaykan.seller.core.network.dto.RoutePointDto
import com.omaykan.seller.core.network.dto.SavedRiderDirectoryDto
import com.omaykan.seller.core.network.dto.SavedRiderDto
import com.omaykan.seller.core.network.dto.SellerOrderDto

/**
 * The wire payload, read into the model.
 *
 * Internal rather than private so the mapping can be tested directly — it is
 * where every assumption this app makes about the server's shape is written
 * down, and those are the assumptions worth a test.
 */
internal fun SellerOrderDto.toModel(): SellerOrder {
    val method = FulfillmentMethod.fromWire(fulfillmentMethod)

    return SellerOrder(
        id = id,
        ticketNumber = ticketNumber?.takeIf { it.isNotBlank() },
        // The server already falls back to "Walk-in" for an order with no guest
        // name. Blank is still worth catching: it renders as a card with a
        // nameless heading, and "Customer" at least reads as a person.
        customerName = customerName?.takeIf { it.isNotBlank() }
            ?: guestContact?.name?.takeIf { it.isNotBlank() }
            ?: "Customer",
        customerPhone = guestContact?.phone?.takeIf { it.isNotBlank() },
        status = OrderStatus.fromWire(status),
        fulfillmentMethod = method,
        // Null for pickup by design — the column is only written for a
        // delivery — and an unknown value reads the same way. Neither is a
        // reason to invent a stage for an order that has no road to be on.
        deliveryStage = if (method == FulfillmentMethod.Delivery) {
            DeliveryStage.fromWire(deliveryStage) ?: DeliveryStage.Pending
        } else {
            null
        },
        deliveryAddress = deliveryAddress?.takeIf { it.isNotBlank() },
        riderName = riderName?.takeIf { it.isNotBlank() },
        riderPhone = riderPhone?.takeIf { it.isNotBlank() },
        riderId = riderId?.takeIf { it.isNotBlank() },
        // A position with no rider_id behind it is not something the server
        // sends, but a defensive null here keeps a future payload from putting
        // a marker on a map for a rider the shop cannot identify.
        riderPosition = riderId?.let { riderPosition?.toModel() },
        route = route?.toModel(),
        /*
         * Only the exact string counts as paid.
         *
         * The column is an enum of unpaid/paid, and anything else the server
         * grows — "refunded", "partial" — must not read as money received. An
         * order wrongly shown unpaid gets a second look at the counter; one
         * wrongly shown paid is money nobody ever asks for.
         */
        paid = paymentStatus == "paid",
        paymentMethod = paymentMethod?.takeIf { it.isNotBlank() },
        subtotalCents = subtotalCents,
        discountCents = discountCents,
        discountLabel = discountLabel?.takeIf { it.isNotBlank() },
        deliveryFeeCents = deliveryFeeCents,
        totalCents = totalCents,
        placedAt = createdAt,
        items = items.map {
            SellerOrderItem(
                productId = it.productId,
                name = it.name,
                quantity = it.quantity,
                unitPriceCents = it.unitPriceCents,
                lineTotalCents = it.lineTotalCents,
            )
        },
    )
}

/** The rider's last fix, as the map and the line under it need it. */
private fun RiderPositionDto.toModel() = RiderPosition(
    lat = lat,
    lng = lng,
    headingDeg = headingDeg,
    speedKph = speedKph,
    ageSeconds = ageSeconds,
    stale = stale,
)

private fun RouteDto.toModel() = DeliveryRoute(
    pickup = pickup.toModel(),
    dropoff = dropoff.toModel(),
)

private fun RoutePointDto.toModel() = RoutePoint(
    name = name?.takeIf { it.isNotBlank() },
    address = address?.takeIf { it.isNotBlank() },
    lat = lat,
    lng = lng,
)

/**
 * The shop's own riders.
 *
 * `name` is what *this shop* calls them and is never replaced with the account's
 * own name — "Kuya Jun" beats the legal name on the licence when you are
 * shouting across a kitchen. The server holds that rule; this just carries it.
 */
fun SavedRiderDto.toModel() = SavedRider(
    id = id,
    riderId = riderId?.takeIf { it.isNotBlank() },
    name = name.ifBlank { "Rider" },
    phone = phone?.takeIf { it.isNotBlank() },
    note = note?.takeIf { it.isNotBlank() },
    onPlatform = onPlatform,
    status = status,
    online = online,
)

fun RecentRiderDto.toModel() = RecentRider(
    riderId = riderId,
    name = name.ifBlank { "Rider" },
    phone = phone?.takeIf { it.isNotBlank() },
    online = online,
)

fun SavedRiderDirectoryDto.toModel() = RiderDirectory(
    saved = saved.map { it.toModel() },
    recent = recent.map { it.toModel() },
)
