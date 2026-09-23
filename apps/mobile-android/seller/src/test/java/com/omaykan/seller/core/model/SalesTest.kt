package com.omaykan.seller.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class SalesTest {

    private val zone = ZoneId.of("Asia/Manila")
    private val today = LocalDate.of(2026, 9, 11)

    private fun order(
        id: String,
        date: LocalDate,
        totalCents: Long,
        items: List<SellerOrderItem> = emptyList(),
        time: String = "10:00:00",
    ) = SellerOrder(
        id = id,
        ticketNumber = id,
        customerName = "Customer",
        customerPhone = null,
        status = OrderStatus.Served,
        fulfillmentMethod = FulfillmentMethod.Pickup,
        deliveryStage = null,
        deliveryAddress = null,
        riderName = null,
        riderPhone = null,
        riderId = null,
        riderPosition = null,
        route = null,
        paid = true,
        paymentMethod = "cash",
        subtotalCents = totalCents,
        deliveryFeeCents = 0,
        totalCents = totalCents,
        placedAt = "${date}T$time+08:00",
        items = items,
    )

    private val threePm = today.atTime(15, 0).atZone(zone)

    @Test
    fun `compares today with yesterday up to the same hour`() {
        val orders = listOf(
            order("today", today, 12_000),
            order("yesterday-morning", today.minusDays(1), 10_000, time = "09:00:00"),
            // After three yesterday: not part of a fair comparison at three today.
            order("yesterday-evening", today.minusDays(1), 50_000, time = "18:00:00"),
        )

        assertEquals(20, orders.salesChangeVsYesterday(threePm))
    }

    @Test
    fun `says nothing when yesterday had no sales by this hour`() {
        val orders = listOf(
            order("today", today, 12_000),
            order("yesterday-evening", today.minusDays(1), 50_000, time = "18:00:00"),
        )

        assertNull(orders.salesChangeVsYesterday(threePm))
    }

    @Test
    fun `says nothing when a full list does not reach back to yesterday`() {
        val busy = (1..ORDER_LIST_CAP).map { order("o$it", today, 1_000) }

        assertNull(busy.salesChangeVsYesterday(threePm))
    }

    private fun line(productId: String, name: String, quantity: Double, totalCents: Long) =
        SellerOrderItem(productId, name, quantity, totalCents, totalCents)

    @Test
    fun `adds up each of the last seven days and nothing older`() {
        val week = listOf(
            order("a", today, 10_000),
            order("b", today.minusDays(6), 5_000),
            order("c", today.minusDays(7), 99_900),
        ).weekSales(today, zone)

        assertEquals(7, week.days.size)
        assertEquals(today.minusDays(6), week.days.first().date)
        assertEquals(today, week.days.last().date)
        assertEquals(15_000L, week.totalCents)
        assertEquals(2, week.orderCount)
        assertEquals(7_500L, week.averageCents)
        assertTrue(week.complete)
        assertEquals(99_900L, week.previousTotalCents)
    }

    @Test
    fun `a full list that stops inside the week says it is incomplete`() {
        val busy = (1..ORDER_LIST_CAP).map { order("o$it", today, 1_000) }

        val week = busy.weekSales(today, zone)

        assertFalse(week.complete)
        assertNull(week.previousTotalCents)
        assertNull(week.changePercent)
    }

    @Test
    fun `a full list that reaches past the week covers it`() {
        val orders = (1 until ORDER_LIST_CAP).map { order("o$it", today, 1_000) } +
            order("old", today.minusDays(7), 1_000)

        val week = orders.weekSales(today, zone)

        assertTrue(week.complete)
        // ...but not the week before, which the list only touches the edge of.
        assertNull(week.previousTotalCents)
    }

    @Test
    fun `compares with the week before when both are in hand`() {
        val week = listOf(
            order("now", today, 15_000),
            order("then", today.minusDays(8), 10_000),
        ).weekSales(today, zone)

        assertEquals(50, week.changePercent)
    }

    @Test
    fun `top products group by product and rank by quantity sold`() {
        val orders = listOf(
            order("a", today, 0, listOf(line("s", "Strawberries", 2.0, 36_000), line("c", "Coffee", 1.0, 35_000))),
            order("b", today.minusDays(1), 0, listOf(line("s", "Strawberries", 1.5, 27_000))),
            order("old", today.minusDays(9), 0, listOf(line("c", "Coffee", 10.0, 350_000))),
        )

        val top = orders.topProducts(since = today.minusDays(6), zone = zone)

        assertEquals(listOf("s", "c"), top.map { it.productId })
        assertEquals(3.5, top.first().quantity, 0.0)
        assertEquals(63_000L, top.first().revenueCents)
    }
}
