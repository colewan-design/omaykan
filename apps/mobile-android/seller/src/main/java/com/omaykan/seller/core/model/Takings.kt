package com.omaykan.seller.core.model

import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * What the shop has taken today, from the list already on screen.
 *
 * No endpoint of its own, deliberately. `GET /api/seller/online-orders` returns
 * the last hundred orders newest-first, which on any shop this product is built
 * for covers today and then some, so the day's figures are arithmetic over a
 * list the app has already fetched rather than a second call that could
 * disagree with it.
 *
 * The consequence is worth stating plainly, and the strip says so on screen:
 * **this counts storefront orders only.** In-person sales ride the till's
 * offline-first outbox and never appear here. A merchant reading this as "the
 * day's takings" would be reading it as something it is not.
 */
data class Takings(
    val orderCount: Int,
    val grossCents: Long,
    /** Orders still owed money — the number worth chasing before closing. */
    val unpaidCount: Int,
    val unpaidCents: Long,
)

/**
 * Today's orders, in the phone's own time zone.
 *
 * The phone's zone rather than the server's: a shop owner asking "how did today
 * go" means the day they are standing in. The two agree for every Philippine
 * shop this is built for, and where they would not — a merchant travelling —
 * the local reading is the one that matches the question.
 *
 * An order whose timestamp will not parse is counted out rather than in. A
 * total that quietly includes last week is worse than one that is visibly a
 * little short, because nothing about the first one looks wrong.
 */
fun List<SellerOrder>.takingsFor(
    today: LocalDate = LocalDate.now(),
    zone: ZoneId = ZoneId.systemDefault(),
): Takings {
    val todays = filter { it.placedAtDate(zone) == today }

    return Takings(
        orderCount = todays.size,
        // Gross, and the delivery fee is inside it because the order total the
        // server sends already includes one. That fee is the rider's, not the
        // shop's — 100% of it, by the promise this whole product is built on —
        // so this figure is "money that came through the shop today", which is
        // what a merchant reconciles against, not shop profit.
        grossCents = todays.sumOf { it.totalCents },
        unpaidCount = todays.count { !it.paid },
        unpaidCents = todays.filter { !it.paid }.sumOf { it.totalCents },
    )
}

/** The order's placement date in [zone], or null if the server sent nothing usable. */
fun SellerOrder.placedAtDate(zone: ZoneId = ZoneId.systemDefault()): LocalDate? =
    placedAtZoned(zone)?.toLocalDate()

/**
 * When the order came in, as a clock time — "9:14 am".
 *
 * The card shows this and not a date. Every order on that screen arrived
 * today, so a date would be the same string twenty times over; what a shop
 * actually wants to know, glancing down the list, is which ticket has been
 * sitting there longest.
 *
 * Locale-formatted rather than hand-built, so a phone set to 24-hour time gets
 * 24-hour time.
 */
fun SellerOrder.placedAtTimeLabel(zone: ZoneId = ZoneId.systemDefault()): String? =
    placedAtZoned(zone)
        ?.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))

private fun SellerOrder.placedAtZoned(zone: ZoneId): ZonedDateTime? {
    val raw = placedAt ?: return null

    /*
     * The offset parser first, because that is the shape Laravel actually
     * sends: `toIso8601String()` produces "2026-08-29T09:14:22+08:00", and
     * Instant.parse on this API level rejects any offset that is not Z.
     * Instant is kept as the fallback for a server — or a fixture — that
     * normalises to UTC.
     */
    return runCatching { OffsetDateTime.parse(raw).atZoneSameInstant(zone) }
        .recoverCatching { Instant.parse(raw).atZone(zone) }
        .getOrNull()
}
