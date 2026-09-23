package com.omaykan.seller.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/**
 * The takings strip is the only figure in this app a merchant might reconcile
 * something against, so what it counts — and what it refuses to count — is
 * worth pinning down.
 */
class TakingsTest {

    private val manila = ZoneId.of("Asia/Manila")
    private val today = LocalDate.of(2026, 8, 29)

    private fun order(
        id: String,
        placedAt: String?,
        totalCents: Long = 10_000,
        paid: Boolean = true,
    ) = SellerOrder(
        id = id,
        ticketNumber = null,
        customerName = "Ana",
        customerPhone = null,
        status = OrderStatus.Preparing,
        fulfillmentMethod = FulfillmentMethod.Pickup,
        deliveryStage = null,
        deliveryAddress = null,
        riderName = null,
        riderPhone = null,
        // No platform rider on these fixtures: none of them is about a map.
        riderId = null,
        riderPosition = null,
        route = null,
        paid = paid,
        paymentMethod = null,
        subtotalCents = totalCents,
        deliveryFeeCents = 0,
        totalCents = totalCents,
        placedAt = placedAt,
        items = emptyList(),
    )

    @Test
    fun `counts today and leaves yesterday alone`() {
        val orders = listOf(
            order("a", "2026-08-29T09:14:22+08:00", totalCents = 12_000),
            order("b", "2026-08-29T17:40:00+08:00", totalCents = 8_000),
            order("c", "2026-08-28T22:10:00+08:00", totalCents = 99_000),
        )

        val takings = orders.takingsFor(today = today, zone = manila)

        assertEquals(2, takings.orderCount)
        assertEquals(20_000, takings.grossCents)
    }

    @Test
    fun `unpaid is counted separately and is a subset of gross`() {
        val orders = listOf(
            order("a", "2026-08-29T09:00:00+08:00", totalCents = 12_000, paid = true),
            order("b", "2026-08-29T10:00:00+08:00", totalCents = 5_000, paid = false),
        )

        val takings = orders.takingsFor(today = today, zone = manila)

        assertEquals(17_000, takings.grossCents)
        assertEquals(1, takings.unpaidCount)
        assertEquals(5_000, takings.unpaidCents)
    }

    @Test
    fun `an order with an unreadable timestamp is left out rather than folded in`() {
        // A total that quietly includes last week is worse than one that is
        // visibly a little short, because nothing about the first looks wrong.
        val orders = listOf(
            order("a", "2026-08-29T09:00:00+08:00", totalCents = 12_000),
            order("b", "not a date", totalCents = 99_000),
            order("c", null, totalCents = 99_000),
        )

        val takings = orders.takingsFor(today = today, zone = manila)

        assertEquals(1, takings.orderCount)
        assertEquals(12_000, takings.grossCents)
    }

    @Test
    fun `a UTC timestamp is read into the phone's day`() {
        // Laravel sends an offset; a normalised UTC one must still work.
        // 2026-08-29T01:00Z is nine in the morning in Manila, the same day.
        val order = order("a", "2026-08-29T01:00:00Z")

        assertEquals(today, order.placedAtDate(manila))
    }

    @Test
    fun `the day boundary follows the zone, not the string`() {
        // Half past four UTC on the 28th is half past midnight on the 29th in
        // Manila — the shop's day, which is the day the question is about.
        val order = order("a", "2026-08-28T16:30:00Z")

        assertEquals(today, order.placedAtDate(manila))
        assertEquals(LocalDate.of(2026, 8, 28), order.placedAtDate(ZoneId.of("UTC")))
    }

    @Test
    fun `an unparseable timestamp has no date at all`() {
        assertNull(order("a", "").placedAtDate(manila))
        assertNull(order("a", null).placedAtDate(manila))
    }
}
