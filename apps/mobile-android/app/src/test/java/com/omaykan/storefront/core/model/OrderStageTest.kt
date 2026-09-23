package com.omaykan.storefront.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Which bucket an order falls in on the account page.
 *
 * Worth its own tests because the numbers are read as a promise: a shopper who
 * sees "1" beside On the way believes a rider is holding their food, and the
 * only thing standing behind that belief is this `when`.
 */
class OrderStageTest {

    private fun order(
        status: String = "preparing",
        deliveryStage: String? = null,
        fulfillment: String = FulfillmentMethod.Delivery.wire,
    ) = TrackedOrder(
        orderId = "order-1",
        ticketNumber = "1234",
        status = status,
        paymentStatus = "unpaid",
        paymentMethod = "cash",
        subtotalCents = 50_000,
        taxCents = 0,
        deliveryFeeCents = 4_900,
        totalCents = 54_900,
        fulfillmentMethod = fulfillment,
        deliveryAddress = "12 Session Road",
        deliveryStage = deliveryStage,
        riderName = null,
        riderPhone = null,
        placedAt = "2026-08-28T09:00:00+08:00",
        items = emptyList(),
    )

    @Test
    fun `a fresh order is preparing`() {
        assertEquals(OrderStage.Preparing, order(status = "preparing").stage)
    }

    @Test
    fun `ready means ready`() {
        assertEquals(OrderStage.Ready, order(status = "ready").stage)
    }

    /**
     * `served` is the till's word for handed over, and the app's TERMINAL set
     * deliberately does not include it — so it must not read as Completed here
     * either, or the account page and the Orders tab would disagree about the
     * same order.
     */
    @Test
    fun `served is ready, not completed`() {
        assertEquals(OrderStage.Ready, order(status = "served").stage)
    }

    @Test
    fun `completed is completed`() {
        assertEquals(OrderStage.Completed, order(status = "completed").stage)
    }

    /** A cancelled order is real, but it is not a stage on the way to anything. */
    @Test
    fun `cancelled and voided are in no bucket`() {
        assertNull(order(status = "cancelled").stage)
        assertNull(order(status = "voided").stage)
    }

    @Test
    fun `a rider holding the order outranks the kitchen status`() {
        assertEquals(OrderStage.OnTheWay, order(status = "ready", deliveryStage = "assigned").stage)
        assertEquals(OrderStage.OnTheWay, order(status = "ready", deliveryStage = "picked_up").stage)
    }

    /** Delivery not yet handed to anyone is still the kitchen's. */
    /** Counted as on the way, but the row itself says what is actually true. */
    @Test
    fun `an accepted order is labelled rider assigned`() {
        assertEquals("Rider assigned", order(deliveryStage = "assigned").stageLabel)
        assertEquals("On the way", order(deliveryStage = "picked_up").stageLabel)
        assertEquals("Ready", order(status = "ready").stageLabel)
        assertNull(order(status = "cancelled", deliveryStage = "assigned").stageLabel)
    }

    @Test
    fun `a pending delivery stage is not on the way`() {
        assertEquals(OrderStage.Preparing, order(status = "preparing", deliveryStage = "pending").stage)
    }

    /**
     * A delivered order whose till status has not caught up is not "on the
     * way": the rider is done with it.
     */
    @Test
    fun `a delivered stage falls back to the till status`() {
        assertEquals(OrderStage.Ready, order(status = "ready", deliveryStage = "delivered").stage)
        assertEquals(OrderStage.Completed, order(status = "completed", deliveryStage = "delivered").stage)
    }

    /** Cancelling wins over everything, including a rider mid-run. */
    @Test
    fun `a cancelled order with a rider is still in no bucket`() {
        assertNull(order(status = "cancelled", deliveryStage = "picked_up").stage)
    }

    /** A pickup order never has a delivery stage, and must still bucket. */
    @Test
    fun `pickup orders bucket on the till status alone`() {
        val pickup = order(status = "ready", fulfillment = FulfillmentMethod.Pickup.wire)

        assertEquals(OrderStage.Ready, pickup.stage)
    }

    /**
     * The partition the account page's counts depend on: Preparing + Ready +
     * OnTheWay is exactly the Orders tab's Active, and Completed is its Done.
     */
    @Test
    fun `the buckets agree with the orders tab filter`() {
        val orders = listOf(
            order(status = "preparing"),
            order(status = "ready"),
            order(status = "ready", deliveryStage = "picked_up"),
            order(status = "completed"),
            order(status = "cancelled"),
        )

        val active = orders.filter { it.stage != null && it.stage != OrderStage.Completed }
        val tabActive = orders.filterNot { it.status in setOf("completed", "cancelled", "voided") }

        assertEquals(tabActive.size, active.size)
        assertEquals(1, orders.count { it.stage == OrderStage.Completed })
    }
}
