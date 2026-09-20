package com.omaykan.seller.core.model

import java.time.OffsetDateTime

/**
 * Whether the shop is taking online orders, as the server last said.
 *
 * A pause ends by itself at [resumesAt]; the server does not need anything to
 * run at that moment, and neither does this app — the next read says open.
 */
data class OrderingState(
    val paused: Boolean,
    val resumesAt: OffsetDateTime?,
    val message: String?,
)

/** How long a pause from the home screen lasts. */
enum class PauseLength(val label: String) {
    OneHour("1 hour"),
    TwoHours("2 hours"),
    TomorrowMorning("Until 7 AM tomorrow"),
    UntilReopened("Until I reopen"),
    ;

    /** When a pause of this length, started now, should end. Null is "until reopened". */
    fun resumesAt(now: OffsetDateTime = OffsetDateTime.now()): OffsetDateTime? = when (this) {
        OneHour -> now.plusHours(1)
        TwoHours -> now.plusHours(2)
        TomorrowMorning -> now.plusDays(1).withHour(7).withMinute(0).withSecond(0).withNano(0)
        UntilReopened -> null
    }
}
