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
