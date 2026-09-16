package com.omaykan.rider.core.model

/**
 * Where an account stands with the platform.
 *
 * The values are `Rider::STATUSES` on the backend, and the distinction that
 * matters here is not four-way but two-way: [Approved] can see the board, and
 * the other three can see nothing but their own status. That gate is a
 * middleware server-side (EnsureRiderIsApproved), so this enum is what the app
 * uses to pick a *screen*, never what it uses to decide whether a call is
 * allowed. The server decides that.
 */
enum class RiderStatus(val wire: String) {
    /** Signed up, waiting on a human to look at the licence. */
    Pending("pending"),

    /** Cleared to take work. */
    Approved("approved"),

    /** Turned down at review. Terminal unless an operator reopens it. */
    Rejected("rejected"),

    /** Was approved, then stopped. Same access as rejected, different story. */
    Suspended("suspended"),
    ;

    companion object {
        /**
         * Anything unrecognised reads as Pending.
         *
         * The safe fallback is the one that shows a waiting screen rather than
         * a job board: a status this build has never heard of is a status whose
         * privileges it cannot possibly know, and the server would refuse the
         * board anyway. Better a rider who is told to wait than one who taps
         * Accept forty times into a 403.
         */
        fun fromWire(value: String?): RiderStatus =
            entries.firstOrNull { it.wire == value } ?: Pending
    }
}

/**
 * The rider's own account, as `GET /api/rider/me` describes it.
 *
 * `Rider::toPortalArray` deliberately omits the document paths — the rider
 * knows what they uploaded, and nothing in the app has any reason to be able to
 * address an identity document on the private disk.
 */
data class RiderProfile(
    val id: String,
    val name: String,
    val email: String,
    val phone: String,
    val licenseNumber: String,
    val plateNumber: String,
    /**
     * The path to this rider's photograph, or null if they have not set one.
     *
     * A path rather than a full URL — see RiderDto.photoUrl. Null is the
     * ordinary case and never an error: every screen that draws a rider has an
     * initial-letter avatar to fall back to, and a rider who does not want
     * their face on a stranger's phone is entitled to keep it that way.
     */
    val photoUrl: String?,
    val vehicle: Vehicle,
    val rating: RatingSummary,
    val status: RiderStatus,
    /**
     * What the operator wrote when they decided.
     *
     * The whole reason a rejected rider is allowed to sign in at all: without
     * this sentence, "your application was not approved" is a dead end with no
     * route back to a human.
     */
    val reviewNote: String?,
    val reviewedAt: String?,
    val createdAt: String?,
)

/**
 * What the rider rides.
 *
 * [type] is a closed set the app draws an icon from; the rest is free text,
 * because the long tail of what people actually ride here — a rebuilt
 * tricycle, an unbadged e-bike — does not fit a dropdown.
 *
 * [label] is the server's own sentence rather than one assembled here, so the
 * shop's dashboard, the customer's tracking page and this app all describe the
 * same bike with the same words.
 */
data class Vehicle(
    val type: VehicleType,
    val make: String?,
    val model: String?,
    val color: String?,
    val label: String,
    val plateNumber: String,
)

/**
 * The six shapes a job can arrive on.
 *
 * [Ebike] is separate from [Bicycle] deliberately, and the distinction is the
 * shop's rather than the rider's: to somebody deciding what fits in a top box
 * and how far it will go before it stops, a pedal bicycle and an electric one
 * are different vehicles.
 */
enum class VehicleType(val wire: String, val label: String) {
    Motorcycle("motorcycle", "Motorcycle"),
    Scooter("scooter", "Scooter"),
    Tricycle("tricycle", "Tricycle"),
    Bicycle("bicycle", "Bicycle"),
    Ebike("ebike", "E-bike"),
    Car("car", "Car"),
    ;

    companion object {
        /**
         * Anything unrecognised reads as a motorcycle.
         *
         * The same argument RiderStatus.fromWire makes, with less at stake: a
         * type this build has never heard of should draw *a* vehicle rather
         * than leave a hole in the card, and motorcycle is what the column
         * defaults to server-side anyway.
         */
        fun fromWire(value: String?): VehicleType =
            entries.firstOrNull { it.wire == value } ?: Motorcycle
    }
}

/**
 * What customers have scored this rider.
 *
 * [average] is null for a rider nobody has rated yet — *not* zero. Rendering an
 * unrated rider as 0.0 out of 5 would put the worst possible number on the
 * screen of the person least able to have earned it, which is why the null
 * survives all the way from the server to this field.
 */
data class RatingSummary(
    val average: Double?,
    val count: Int,
) {
    /** Below this many, an average is an anecdote. `Rider::RATING_CONFIDENCE_THRESHOLD`. */
    val isConfident: Boolean get() = count >= CONFIDENCE_THRESHOLD

    companion object {
        const val CONFIDENCE_THRESHOLD = 5

        val None = RatingSummary(average = null, count = 0)
    }
}

/**
 * Which screen the app is on, resolved at launch and then kept current.
 *
 * [Restoring] is a real state and not a detail: the token lives in
 * EncryptedSharedPreferences, which is a disk read behind an Android keystore
 * unlock. Treating "not loaded yet" as "signed out" is what makes an app show
 * its sign-in screen for one frame on every cold start.
 *
 * The signed-in half splits on approval rather than carrying a flag, because
 * the two are genuinely different apps to the person holding the phone: one is
 * a job board and one is a letter about an application. A boolean on a single
 * state would leave every screen re-asking the same question.
 */
sealed interface SessionState {
    data object Restoring : SessionState

    data object SignedOut : SessionState

    /** Signed in, and the server has cleared them to work. */
    data class Working(val rider: RiderProfile) : SessionState

    /** Signed in, and pending, rejected or suspended. */
    data class Gated(val rider: RiderProfile) : SessionState
}
