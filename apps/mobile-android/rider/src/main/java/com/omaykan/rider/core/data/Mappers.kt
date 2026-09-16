package com.omaykan.rider.core.data

import com.omaykan.rider.core.model.DeliveryAssignment
import com.omaykan.rider.core.model.DeliveryItem
import com.omaykan.rider.core.model.DeliveryOffer
import com.omaykan.rider.core.model.DeliveryStage
import com.omaykan.rider.core.model.Earnings
import com.omaykan.rider.core.model.EarningsDay
import com.omaykan.rider.core.model.EarningsTotal
import com.omaykan.rider.core.model.Pickup
import com.omaykan.rider.core.model.RiderProfile
import com.omaykan.rider.core.model.RatingSummary
import com.omaykan.rider.core.model.RiderStatus
import com.omaykan.rider.core.model.Vehicle
import com.omaykan.rider.core.model.VehicleType
import com.omaykan.rider.core.network.dto.AssignmentDto
import com.omaykan.rider.core.network.dto.EarningsDayDto
import com.omaykan.rider.core.network.dto.EarningsDto
import com.omaykan.rider.core.network.dto.EarningsTotalDto
import com.omaykan.rider.core.network.dto.OfferDto
import com.omaykan.rider.core.network.dto.PickupDto
import com.omaykan.rider.core.network.dto.RatingSummaryDto
import com.omaykan.rider.core.network.dto.RiderDto
import com.omaykan.rider.core.network.dto.VehicleDto
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

/**
 * The wire payloads, read into the models.
 *
 * Internal rather than private so the mapping can be tested directly — it is
 * where every assumption this app makes about the server's shape is written
 * down, and those are the assumptions worth a test.
 */

internal fun RiderDto.toModel(): RiderProfile = RiderProfile(
    id = id,
    name = name,
    email = email,
    phone = phone,
    licenseNumber = licenseNumber,
    plateNumber = plateNumber,
    photoUrl = photoUrl?.takeIf { it.isNotBlank() },
    vehicle = vehicle.toModel(plateFallback = plateNumber),
    rating = rating.toModel(),
    status = RiderStatus.fromWire(status),
    reviewNote = reviewNote?.takeIf { it.isNotBlank() },
    reviewedAt = reviewedAt,
    createdAt = createdAt,
)

/**
 * @param plateFallback the account's own plate, used when the server is old
 *   enough not to send one inside the vehicle object. The two are the same
 *   column; only the nesting is new.
 */
internal fun VehicleDto.toModel(plateFallback: String): Vehicle = Vehicle(
    type = VehicleType.fromWire(type),
    make = make?.takeIf { it.isNotBlank() },
    model = model?.takeIf { it.isNotBlank() },
    color = color?.takeIf { it.isNotBlank() },
    // A server that sent no label leaves this app to say something rather than
    // nothing; the type's own name is what the server would have fallen back to.
    label = label.takeIf { it.isNotBlank() } ?: VehicleType.fromWire(type).label,
    plateNumber = plateNumber.takeIf { it.isNotBlank() } ?: plateFallback,
)

internal fun RatingSummaryDto.toModel(): RatingSummary = RatingSummary(
    // Zero ratings and a zero average are different facts. Guard the average
    // on the count rather than trusting it: a server that sends 0.0 alongside
    // a count of 0 must still read as "not rated yet".
    average = if (count <= 0) null else average,
    count = count,
)

internal fun PickupDto.toModel(): Pickup = Pickup(
    storeId = storeId,
    // The server already falls back to "Shop" for an order whose store row is
    // missing. Blank is still worth catching: it renders as a card with a
    // nameless heading, and a rider cannot go to a place with no name.
    storeName = storeName.takeIf { it.isNotBlank() } ?: "Shop",
    address = address?.takeIf { it.isNotBlank() },
    lat = lat,
    lng = lng,
)

internal fun OfferDto.toModel(): DeliveryOffer = DeliveryOffer(
    id = id,
    ticketNumber = ticketNumber.asDisplayText(),
    placedAt = placedAt,
    pickup = pickup.toModel(),
    dropoffArea = dropoffArea?.takeIf { it.isNotBlank() },
    distanceKm = distanceKm,
    deliveryFeeCents = deliveryFeeCents,
    itemCount = itemCount,
    /*
     * Only the exact string counts as paid.
     *
     * The column is an enum of unpaid/paid, and anything else the server grows
     * — "refunded", "partial" — must not read as money already received. The
     * asymmetry is the point: an order wrongly shown unpaid gets a question at
     * the door, and one wrongly shown paid is a rider out of pocket for the
     * whole basket.
     *
     * `collectCents` is the server's own arithmetic on the same column, so the
     * two agree by construction rather than by this app recomputing it.
     */
    paid = paymentStatus == "paid",
    collectCents = collectCents,
)

internal fun AssignmentDto.toModel(): DeliveryAssignment = DeliveryAssignment(
    offer = DeliveryOffer(
        id = id,
        ticketNumber = ticketNumber.asDisplayText(),
        placedAt = placedAt,
        pickup = pickup.toModel(),
        dropoffArea = dropoffArea?.takeIf { it.isNotBlank() },
        distanceKm = distanceKm,
        deliveryFeeCents = deliveryFeeCents,
        itemCount = itemCount,
        paid = paymentStatus == "paid",
        collectCents = collectCents,
    ),
    stage = DeliveryStage.fromWire(deliveryStage),
    acceptedAt = acceptedAt,
    deliveryAddress = deliveryAddress?.takeIf { it.isNotBlank() },
    deliveryLat = deliveryLat,
    deliveryLng = deliveryLng,
    customerName = customerName?.takeIf { it.isNotBlank() },
    customerPhone = customerPhone?.takeIf { it.isNotBlank() },
    items = items.map { DeliveryItem(name = it.name, quantity = it.quantity) },
)

/**
 * The ticket number as something printable.
 *
 * It arrives as a JSON number for most orders and a string for a few, and this
 * app only ever puts it on a card. `JsonPrimitive.content` gives the digits
 * without the quotes a naive `toString()` would leave on a string value —
 * `"A-12"` rather than `A-12` — which is the whole reason this is not a
 * one-liner.
 */
private fun JsonElement?.asDisplayText(): String? =
    (this as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() && it != "null" }

internal fun EarningsTotalDto.toModel(): EarningsTotal = EarningsTotal(
    jobs = jobs,
    feeCents = feeCents,
)

internal fun EarningsDayDto.toModel(): EarningsDay = EarningsDay(
    date = date,
    jobs = jobs,
    feeCents = feeCents,
)

/**
 * `currency` is read and dropped.
 *
 * It is PHP on every row the platform has ever written, and Money formats pesos
 * unconditionally — so carrying it into the model would be carrying a field
 * nothing reads, which is how a screen ends up quietly showing "₱" over a
 * number that is not pesos. When there is a second currency there will be a
 * formatter that takes one, and this is where it starts.
 */
internal fun EarningsDto.toModel(): Earnings = Earnings(
    today = today.toModel(),
    week = week.toModel(),
    month = month.toModel(),
    allTime = allTime.toModel(),
    days = days.map { it.toModel() },
)
