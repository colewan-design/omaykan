package com.omaykan.seller.core.model

import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * A server timestamp in [zone], or null if there was nothing usable.
 *
 * The offset parser first, because that is the shape Laravel sends —
 * "2026-08-29T09:14:22+08:00" — and Instant.parse on this API level rejects
 * any offset that is not Z. Instant stays as the fallback for a payload that
 * normalises to UTC.
 */
fun parseServerTime(raw: String?, zone: ZoneId = ZoneId.systemDefault()): ZonedDateTime? {
    if (raw.isNullOrBlank()) return null
    return runCatching { OffsetDateTime.parse(raw).atZoneSameInstant(zone) }
        .recoverCatching { Instant.parse(raw).atZone(zone) }
        .getOrNull()
}

/**
 * "10:24 AM" today, "Yesterday", then "Mar 14" — the inbox's and the payout
 * list's way of saying when.
 *
 * The clock time is locale-formatted, so a phone set to 24-hour time gets
 * 24-hour time.
 */
fun relativeStamp(
    raw: String?,
    today: LocalDate = LocalDate.now(),
    zone: ZoneId = ZoneId.systemDefault(),
): String? {
    val at = parseServerTime(raw, zone) ?: return null
    return when (at.toLocalDate()) {
        today -> at.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))
        today.minusDays(1) -> "Yesterday"
        else -> if (at.year == today.year) {
            at.format(DateTimeFormatter.ofPattern("MMM d"))
        } else {
            at.format(DateTimeFormatter.ofPattern("MMM d, yyyy"))
        }
    }
}
