package com.omaykan.seller.core.model

/**
 * The kitchen's side of an order: what the shop still owes the customer.
 *
 * These are the three values `POST /api/seller/online-orders/{order}/status`
 * accepts, and nothing else. An online order is born `preparing` — the shop has
 * it — so there is no "new" here; whether a merchant has *looked* at one is a
 * separate question, and one the phone answers for itself rather than asking
 * the server. See NewOrderWatch.
 */
enum class OrderStatus(val wire: String, val label: String) {
    Preparing("preparing", "Preparing"),
    Ready("ready", "Ready"),
    Served("served", "Completed"),
    ;

    /** The button on the card: what tapping once would do next. */
    val next: OrderStatus?
        get() = when (this) {
            Preparing -> Ready
            Ready -> Served
            Served -> null
        }

    /** The word on that button — an instruction, not a state. */
    val advanceLabel: String?
        get() = when (this) {
            Preparing -> "Mark ready"
            Ready -> "Complete"
            Served -> null
        }

    companion object {
        /**
         * Anything unrecognised reads as Preparing.
         *
         * The server can grow a status this build has never heard of, and the
         * safe reading of an unknown one is "still the shop's problem" — an
         * order wrongly shown as outstanding gets looked at, whereas one
         * wrongly shown as finished gets forgotten.
         */
        fun fromWire(value: String?): OrderStatus =
            entries.firstOrNull { it.wire == value } ?: Preparing
    }
}

/**
 * How far along the road a delivery is.
 *
 * Distinct from [OrderStatus] on purpose, and both move independently: the
 * kitchen can have an order `ready` while it still sits at `assigned` waiting
 * for a rider. This is the sequence the customer's tracking page reads.
 */
enum class DeliveryStage(val wire: String, val label: String) {
    Pending("pending", "Needs a rider"),
    Assigned("assigned", "Rider assigned"),
    PickedUp("picked_up", "On the way"),
    Delivered("delivered", "Delivered"),
    ;

    val next: DeliveryStage?
        get() = when (this) {
            // Deliberately absent. Moving off Pending is naming a rider, which
            // takes a name and a number — a different endpoint and a different
            // gesture from advancing a stage.
            Pending -> null
            Assigned -> PickedUp
            PickedUp -> Delivered
            Delivered -> null
        }

    val advanceLabel: String?
        get() = when (this) {
            Pending -> null
            Assigned -> "Picked up"
            PickedUp -> "Delivered"
            Delivered -> null
        }

    companion object {
        fun fromWire(value: String?): DeliveryStage? =
            entries.firstOrNull { it.wire == value }
    }
}

enum class FulfillmentMethod(val wire: String, val label: String) {
    Pickup("pickup", "Pickup"),
    Delivery("delivery", "Delivery"),
    ;

    companion object {
        /**
         * Pickup is the fallback, and it is the safe one: it claims nothing
         * about a rider, and the delivery controls stay hidden rather than
         * offering to dispatch someone for an order that is being collected.
         */
        fun fromWire(value: String?): FulfillmentMethod =
            entries.firstOrNull { it.wire == value } ?: Pickup
    }
}

/** What the seller may record when the money arrives. Mirrors the API's enum. */
enum class PaymentMethod(val wire: String, val label: String) {
    Cash("cash", "Cash"),
    EWallet("ewallet", "GCash / e-wallet"),
    Card("card", "Card"),
}

/**
 * One storefront order, as `GET /api/seller/online-orders` describes it.
 *
 * A flattened, typed reading of that payload rather than the whole of it: the
 * fields this app has no screen for — tax, table number, the void columns — are
 * dropped at the DTO boundary rather than carried around unused.
 */
data class SellerOrder(
    val id: String,
    val ticketNumber: String?,
    val customerName: String,
    val customerPhone: String?,
    val status: OrderStatus,
    val fulfillmentMethod: FulfillmentMethod,
    val deliveryStage: DeliveryStage?,
    val deliveryAddress: String?,
    val riderName: String?,
    val riderPhone: String?,
    /**
     * Set only when the rider is a platform account rather than a name typed in
     * at the counter. It is what separates "our nephew on a tricycle", who has
     * no app and no map, from somebody whose position can actually be drawn.
     */
    val riderId: String?,
    /** The last fix, or null when nobody is reporting one. */
    val riderPosition: RiderPosition?,
    /** Shop and door, for the map. Either half may be missing coordinates. */
    val route: DeliveryRoute?,
    val paid: Boolean,
    val paymentMethod: String?,
    val subtotalCents: Long,
    /** Off the subtotal, before tax — a promo code, online. */
    val discountCents: Long = 0,
    val discountLabel: String? = null,
    val deliveryFeeCents: Long,
    val totalCents: Long,
    /** ISO-8601, as the server sent it. Parsed only for the day boundary. */
    val placedAt: String?,
    val items: List<SellerOrderItem>,
) {
    val isDelivery: Boolean get() = fulfillmentMethod == FulfillmentMethod.Delivery

    /** A delivery with nobody carrying it — the thing a seller must act on. */
    val needsRider: Boolean
        get() = isDelivery && (deliveryStage == null || deliveryStage == DeliveryStage.Pending)

    /**
     * Still the shop's problem.
     *
     * Payment is part of it: an order the customer has collected but not paid
     * for is not finished, whatever the kitchen thinks. So is a delivery still
     * on the road. This is the predicate the "Live" tab and the unpaid count
     * both read, so the two can never disagree about what is outstanding.
     */
    val isOpen: Boolean
        get() = status != OrderStatus.Served ||
            !paid ||
            (isDelivery && deliveryStage != DeliveryStage.Delivered)

    val itemCount: Int get() = items.sumOf { it.quantity.toInt() }

    /** A one-line summary of the basket, for the collapsed card. */
    val itemSummary: String
        get() = items.joinToString(", ") { line ->
            val quantity = line.quantity
            val shown = if (quantity % 1.0 == 0.0) quantity.toInt().toString() else quantity.toString()
            "$shown× ${line.name}"
        }
}

data class SellerOrderItem(
    val productId: String,
    val name: String,
    /** A float on the wire: a grocery sells 1.5 kg of something. */
    val quantity: Double,
    val unitPriceCents: Long,
    val lineTotalCents: Long,
)

/** Where the rider was, the last time their phone said. */
data class RiderPosition(
    val lat: Double,
    val lng: Double,
    val headingDeg: Double?,
    val speedKph: Double?,
    val ageSeconds: Int,
    /** True once the fix is too old to draw as a live position. */
    val stale: Boolean,
) {
    /** "2 minutes ago", for the line under the map. */
    val ageLabel: String
        get() = when {
            ageSeconds < 45 -> "just now"
            ageSeconds < 90 -> "a minute ago"
            ageSeconds < 3600 -> "${ageSeconds / 60} minutes ago"
            else -> "over an hour ago"
        }
}

/** A point on the map that is not the rider. */
data class RoutePoint(val name: String?, val address: String?, val lat: Double?, val lng: Double?) {
    val placed: Boolean get() = lat != null && lng != null && !(lat == 0.0 && lng == 0.0)
}

data class DeliveryRoute(val pickup: RoutePoint, val dropoff: RoutePoint)

/**
 * One rider the shop keeps on file.
 *
 * [onPlatform] is the distinction that matters wherever this is shown. False
 * means a name and a number — assigning records who is carrying the order and
 * the shop rings them, which is how deliveries worked before there was a rider
 * app and is still how most of them work here. True means a real account: the
 * order lands in that person's app and their position feeds the map.
 */
data class SavedRider(
    val id: String,
    val riderId: String?,
    val name: String,
    val phone: String?,
    val note: String?,
    val onPlatform: Boolean,
    val status: String?,
    val online: Boolean,
) {
    /** A suspended account still has a number to ring, but cannot take a job. */
    val assignable: Boolean get() = !onPlatform || status == "approved"
}

/** Somebody who has delivered for this shop but is not on its list yet. */
data class RecentRider(
    val riderId: String,
    val name: String,
    val phone: String?,
    val online: Boolean,
)

/** Both halves of the assign-a-rider picker. */
data class RiderDirectory(
    val saved: List<SavedRider> = emptyList(),
    val recent: List<RecentRider> = emptyList(),
)
