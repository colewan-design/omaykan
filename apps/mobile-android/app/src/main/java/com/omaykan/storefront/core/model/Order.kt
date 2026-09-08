package com.omaykan.storefront.core.model

enum class FulfillmentMethod(val wire: String) {
    Pickup("pickup"),
    Delivery("delivery"),
}

/**
 * How the shopper intends to settle, not how they have settled.
 *
 * Both are cash on handover — the platform collects nothing and processes
 * nothing. GCash here means "I will transfer at the door instead of handing
 * over notes", which saves the merchant breaking a large note and the rider
 * carrying change. The register settles it either way.
 */
enum class PaymentPreference(val wire: String, val label: String) {
    Cash("cash", "Cash"),
    Ewallet("ewallet", "GCash"),
}

/** What the shopper typed, plus a pin when the device could supply one. */
data class DeliveryDestination(
    val address: String,
    val lat: Double? = null,
    val lng: Double? = null,
) {
    /**
     * The API rejects half a coordinate, so a pin travels whole or not at all.
     * Without one the fee falls back to the shop's flat base rate.
     */
    val pinned: Boolean get() = lat != null && lng != null
}

data class Contact(
    val name: String,
    val phone: String,
    val email: String,
)

/** The server's answer to a placed order. These are the numbers to render. */
data class PlacedOrder(
    val orderId: String,
    val ticketNumber: String?,
    val totalCents: Long,
    val deliveryFeeCents: Long,
)

data class TrackedOrderItem(
    /** Blank when the product has since been deleted; used to find a photo. */
    val productId: String,
    val name: String,
    val quantity: Double,
    val unitPriceCents: Long,
    val lineTotalCents: Long,
)

data class TrackedOrder(
    val orderId: String,
    val ticketNumber: String?,
    val status: String,
    val paymentStatus: String,
    val paymentMethod: String?,
    val subtotalCents: Long,
    val taxCents: Long,
    val deliveryFeeCents: Long,
    val totalCents: Long,
    val fulfillmentMethod: String,
    val deliveryAddress: String?,
    val deliveryStage: String?,
    val riderName: String?,
    val riderPhone: String?,
    /** Null unless a platform rider is carrying this right now. */
    val riderPosition: RiderPosition? = null,
    /** Shop and door. Either half may be missing coordinates and often is. */
    val route: DeliveryRoute? = null,
    val placedAt: String?,
    val items: List<TrackedOrderItem>,
) {
    val isDelivery: Boolean get() = fulfillmentMethod == FulfillmentMethod.Delivery.wire
    val settled: Boolean get() = paymentStatus == "paid"

    /**
     * Called off rather than finished. Two wire words mean it — a shopper's
     * cancellation and a merchant voiding the ticket — and screens that colour
     * or word this differently should not each keep their own copy of that.
     */
    val cancelled: Boolean get() = status in CANCELLED

    /** Sentence-cased for display: the wire values are lower-case slugs. */
    val statusLabel: String
        get() = status.replace('_', ' ').replaceFirstChar { it.uppercase() }

    /**
     * Which bucket of the account's order row this belongs in.
     *
     * Null for a cancelled order: it is a real thing that happened, but it is
     * not a stage on the way to receiving anything, and a permanent "1" beside
     * Cancelled is not a number anybody wants on their own account page. The
     * Orders tab still lists them.
     *
     * The buckets partition exactly the same way the Orders tab's filter does —
     * Preparing + Ready + OnTheWay is its Active, Completed is its Done — so
     * the counts here and the list there can never disagree.
     */
    val stage: OrderStage?
        get() = when {
            status in CANCELLED -> null
            status == "completed" -> OrderStage.Completed
            // The rider has it. Asked before the kitchen status, because "a
            // rider is holding your food" is the more useful of the two
            // answers once both are true.
            deliveryStage in RIDER_HOLDING -> OrderStage.OnTheWay
            status == "ready" || status == "served" -> OrderStage.Ready
            else -> OrderStage.Preparing
        }

    private companion object {
        val CANCELLED = setOf("cancelled", "voided")
        val RIDER_HOLDING = setOf("assigned", "picked_up")
    }
}

/**
 * The four answers to "where is my order", as the account page counts them.
 *
 * Derived from the real status vocabulary and nothing else: `order_status` is
 * preparing → ready → served with completed/cancelled/voided as ends, and
 * `delivery_stage` is pending → assigned → picked_up → delivered. There is no
 * "to pay" bucket because payment is settled at the door, and no "to review"
 * bucket because this platform has no reviews.
 */
enum class OrderStage(val label: String) {
    Preparing("Preparing"),
    Ready("Ready"),
    OnTheWay("On the way"),
    Completed("Completed"),
}

/** Where the rider was, the last time their phone said. */
data class RiderPosition(
    val lat: Double,
    val lng: Double,
    val headingDeg: Double?,
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
