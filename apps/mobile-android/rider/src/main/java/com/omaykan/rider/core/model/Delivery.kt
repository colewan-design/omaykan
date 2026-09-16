package com.omaykan.rider.core.model

/**
 * How far along the road a delivery is.
 *
 * The same four values the seller app and the customer's tracking page read, so
 * a stage this app writes is the stage the shop sees. What differs is the verb:
 * to a shop `assigned` means "a rider has it", and to the rider holding it, it
 * means "go to the shop".
 */
enum class DeliveryStage(val wire: String) {
    /** On the board. Nobody has claimed it. */
    Pending("pending"),

    /** Claimed. The food is still at the shop. */
    Assigned("assigned"),

    /** In the bag, on the bike. */
    PickedUp("picked_up"),

    Delivered("delivered"),
    ;

    /** What the rider does next, written as the instruction it is. */
    val nextLabel: String?
        get() = when (this) {
            Pending -> null
            Assigned -> "Picked up"
            PickedUp -> "Delivered"
            Delivered -> null
        }

    /** The stage that button moves to. Null where there is no button. */
    val next: DeliveryStage?
        get() = when (this) {
            Pending -> null
            Assigned -> PickedUp
            PickedUp -> Delivered
            Delivered -> null
        }

    /** Where the rider is headed right now — what the map button opens. */
    val heading: String
        get() = when (this) {
            Pending, Assigned -> "Head to the shop"
            PickedUp -> "On the way to the customer"
            Delivered -> "Delivered"
        }

    companion object {
        /**
         * Unknown reads as Assigned, the earliest stage a claimed job can be
         * in. It costs a rider one extra tap; the alternative fallback,
         * Delivered, would hide a live job from the person carrying it.
         */
        fun fromWire(value: String?): DeliveryStage =
            entries.firstOrNull { it.wire == value } ?: Assigned
    }
}

/** Where a job starts: the shop, and where on a map it is. */
data class Pickup(
    val storeId: String,
    val storeName: String,
    val address: String?,
    val lat: Double?,
    val lng: Double?,
)

/**
 * An unclaimed job, as `GET /api/rider/board` offers it.
 *
 * Note what is *not* here: no customer name, no phone number, no street
 * address. `RiderDeliveryController::asOffer` withholds all three on purpose —
 * a rider deciding whether to take a job needs to know where it is going, what
 * it weighs and what it pays, not who lives there. Those arrive with the claim.
 * If a field you want is missing from this class, check the controller before
 * adding it: the omission is probably the point.
 */
data class DeliveryOffer(
    val id: String,
    val ticketNumber: String?,
    val placedAt: String?,
    val pickup: Pickup,
    /** The last one or two parts of the address — "Sto. Tomas, Baguio City". */
    val dropoffArea: String?,
    val distanceKm: Double?,
    val deliveryFeeCents: Long,
    val itemCount: Int,
    val paid: Boolean,
    /**
     * What the rider hands the shop back, in centavos. Zero on a prepaid order.
     *
     * This is the order total, not the fee: on an unpaid order the rider
     * collects the whole thing at the door and settles with the shop. It is the
     * single number on this screen that decides whether a rider wants the job.
     */
    val collectCents: Long,
) {
    val collectsCash: Boolean get() = collectCents > 0
}

/**
 * A claimed job: the offer, plus everything it takes to actually finish it.
 *
 * Composition rather than inheritance. The two payloads are one endpoint's two
 * shapes — `asAssignment()` is literally `asOffer() + [...]` — and holding the
 * offer whole means the board card and the top half of the assignment card can
 * be the same composable, given the same data, with no risk of the two drifting.
 */
data class DeliveryAssignment(
    val offer: DeliveryOffer,
    val stage: DeliveryStage,
    val acceptedAt: String?,
    val deliveryAddress: String?,
    val deliveryLat: Double?,
    val deliveryLng: Double?,
    val customerName: String?,
    val customerPhone: String?,
    val items: List<DeliveryItem>,
) {
    val id: String get() = offer.id

    /**
     * Releasable only before pickup.
     *
     * The server enforces this — `release` refuses anything past `assigned`
     * with a 422 — and the rule is physical, not procedural: once the food is
     * in the bag it is with the rider, and handing it back is a phone call, not
     * a button. The button is hidden here so a rider does not tap something
     * that was never going to work.
     */
    val canRelease: Boolean get() = stage == DeliveryStage.Assigned
}

data class DeliveryItem(
    val name: String,
    /** A float on the wire: a grocery sells 1.5 kg of something. */
    val quantity: Double,
) {
    /** "2", or "1.5" — the trailing `.0` dropped off a whole number. */
    val quantityLabel: String
        get() = if (quantity % 1.0 == 0.0) quantity.toInt().toString() else quantity.toString()

    /** "2× Pandesal". */
    val label: String
        get() = "$quantityLabel× $name"
}
