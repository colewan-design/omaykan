package com.omaykan.rider.core.model

/**
 * What the work has come to.
 *
 * Aggregated by the server, never by this app, and the distinction is the whole
 * reason the endpoint exists: `GET /api/rider/deliveries` returns the last
 * thirty finished jobs, so a total added up here was a total of whatever
 * happened to have been fetched. Both this app and the web portal were doing
 * exactly that, and both were quietly wrong from job thirty-one onwards.
 *
 * **The whole fee is the rider's.** There is nothing to subtract here and no
 * field for a platform cut, because there is no platform cut.
 */
data class Earnings(
    val today: EarningsTotal,
    val week: EarningsTotal,
    val month: EarningsTotal,
    val allTime: EarningsTotal,
    /**
     * The last fortnight, oldest first, **including the days with nothing on
     * them**. A chart with the quiet days missing is a chart that lies about
     * how busy a week was, so the server sends every date and this list is
     * always fourteen long.
     */
    val days: List<EarningsDay>,
) {
    /**
     * The mean fee across every finished job.
     *
     * Computed here rather than sent: it is one division of two numbers already
     * on the wire, and a field the server had to keep in step with them would be
     * a field that could disagree with them.
     */
    val averageFeeCents: Long
        get() = if (allTime.jobs == 0) 0 else allTime.feeCents / allTime.jobs

    companion object {
        /** What the screen shows before the first answer arrives. */
        val Empty = Earnings(
            today = EarningsTotal(),
            week = EarningsTotal(),
            month = EarningsTotal(),
            allTime = EarningsTotal(),
            days = emptyList(),
        )
    }
}

/** A count of finished jobs and what they paid, over some window. */
data class EarningsTotal(
    val jobs: Int = 0,
    val feeCents: Long = 0,
)

/**
 * One day's work.
 *
 * [date] is `YYYY-MM-DD` in **Asia/Manila** — the day the work was actually
 * done in, not the UTC day the server stores. Kept as the server's string
 * rather than parsed into a date: this app only ever renders a weekday initial
 * from it, and parsing it into a local `LocalDate` on a phone set to another
 * timezone would shift every label by one.
 */
data class EarningsDay(
    val date: String,
    val jobs: Int,
    val feeCents: Long,
)
