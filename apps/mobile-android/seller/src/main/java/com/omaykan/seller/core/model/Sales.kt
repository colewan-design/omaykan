package com.omaykan.seller.core.model

import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.math.roundToInt

/**
 * The most orders `GET /api/seller/online-orders` returns — newest first, and
 * nothing older. Everything in this file is arithmetic over that list, so it
 * has to know where the list stops.
 */
const val ORDER_LIST_CAP = 100

data class DaySales(val date: LocalDate, val cents: Long, val orders: Int)

/**
 * Seven days of storefront sales, ending today.
 *
 * Gross, like [Takings]: the order total, delivery fee included, paid or not.
 * That is "money that came through the shop's storefront", which is what a
 * merchant reconciles against — not profit, and not counter sales, which
 * never reach this API.
 */
data class WeekSales(
    val days: List<DaySales>,
    /** The seven days before, or null when the fetched list does not reach back that far. */
    val previousTotalCents: Long?,
    /**
     * False when the list hit [ORDER_LIST_CAP] before reaching the start of
     * the week — a busy shop's week can hold more than a hundred orders, and
     * a total that silently drops the oldest of them would be a lie that
     * looks exactly like the truth. The screen says so when this is false.
     */
    val complete: Boolean,
) {
    val totalCents: Long get() = days.sumOf { it.cents }
    val orderCount: Int get() = days.sumOf { it.orders }
    val averageCents: Long? get() = if (orderCount == 0) null else totalCents / orderCount

    /** Change on the week before, in whole percent. Null when there is nothing honest to compare. */
    val changePercent: Int?
        get() = previousTotalCents
            ?.takeIf { it > 0 && complete }
            ?.let { (((totalCents - it) * 100.0) / it).roundToInt() }
}

fun List<SellerOrder>.weekSales(
    today: LocalDate = LocalDate.now(),
    zone: ZoneId = ZoneId.systemDefault(),
    cap: Int = ORDER_LIST_CAP,
): WeekSales {
    val start = today.minusDays(6)
    val previousStart = start.minusDays(7)
    val dated = mapNotNull { order -> order.placedAtDate(zone)?.let { it to order } }
    val oldest = dated.minOfOrNull { it.first }

    // A short list is the whole of it. A full one only covers a day if it
    // reaches *past* it: orders on the oldest day may have been cut off.
    fun reaches(from: LocalDate) = size < cap || (oldest != null && oldest < from)

    val days = (0L..6L).map { offset ->
        val date = start.plusDays(offset)
        val on = dated.filter { it.first == date }.map { it.second }
        DaySales(date, on.sumOf { it.totalCents }, on.size)
    }

    val previous = if (reaches(previousStart)) {
        dated.filter { it.first >= previousStart && it.first < start }.sumOf { it.second.totalCents }
    } else {
        null
    }

    return WeekSales(days = days, previousTotalCents = previous, complete = reaches(start))
}

/**
 * Today's storefront sales against yesterday's *up to this time of day*, in
 * whole percent — the little "+12%" under the home screen's sales figure.
 *
 * Against the same hour rather than all of yesterday, because a whole day
 * against a morning is a comparison that says "−100%" at eight every morning
 * and means nothing. Null when there is nothing honest to compare: yesterday
 * had no sales by this hour, or the fetched list does not reach back to the
 * start of yesterday.
 */
fun List<SellerOrder>.salesChangeVsYesterday(
    now: ZonedDateTime = ZonedDateTime.now(),
    cap: Int = ORDER_LIST_CAP,
): Int? {
    val zone = now.zone
    val today = now.toLocalDate()
    val yesterdayStart = today.minusDays(1).atStartOfDay(zone)
    val sameTimeYesterday = now.minusDays(1)

    val placed = mapNotNull { order -> parseServerTime(order.placedAt, zone)?.let { it to order } }
    val oldest = placed.minOfOrNull { it.first }
    if (size >= cap && (oldest == null || !oldest.isBefore(yesterdayStart))) return null

    val todayCents = placed.filter { it.first.toLocalDate() == today }.sumOf { it.second.totalCents }
    val earlierCents = placed
        .filter { !it.first.isBefore(yesterdayStart) && !it.first.isAfter(sameTimeYesterday) }
        .sumOf { it.second.totalCents }

    if (earlierCents <= 0) return null
    return (((todayCents - earlierCents) * 100.0) / earlierCents).roundToInt()
}

data class TopProduct(
    val productId: String,
    val name: String,
    val quantity: Double,
    val revenueCents: Long,
)

/**
 * What sold most since [since], by quantity — the home screen's "Top Products".
 *
 * Grouped by product id where the line has one, by name where it does not, so
 * a product renamed mid-week still counts as one thing.
 */
fun List<SellerOrder>.topProducts(
    since: LocalDate,
    zone: ZoneId = ZoneId.systemDefault(),
    limit: Int = 3,
): List<TopProduct> =
    filter { order -> order.placedAtDate(zone)?.let { it >= since } == true }
        .flatMap { it.items }
        .groupBy { it.productId.ifBlank { it.name } }
        .map { (_, lines) ->
            TopProduct(
                productId = lines.first().productId,
                name = lines.last().name,
                quantity = lines.sumOf { it.quantity },
                revenueCents = lines.sumOf { it.lineTotalCents },
            )
        }
        .sortedWith(compareByDescending<TopProduct> { it.quantity }.thenByDescending { it.revenueCents })
        .take(limit)
