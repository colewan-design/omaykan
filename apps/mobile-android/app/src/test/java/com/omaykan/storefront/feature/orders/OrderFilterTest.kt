package com.omaykan.storefront.feature.orders

import com.omaykan.storefront.core.model.FulfillmentMethod
import com.omaykan.storefront.core.model.OrderStage
import com.omaykan.storefront.core.model.TrackedOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What each chip shows.
 *
 * The stage chips have to select exactly what the account page counted under
 * the same name — a shopper who taps "On the way" expecting one order and gets
 * an empty list has been lied to by one screen or the other.
 */
class OrderFilterTest {

    private fun order(
        id: String,
        status: String,
        deliveryStage: String? = null,
    ) = TrackedOrder(
        orderId = id,
        ticketNumber = id,
        status = status,
        paymentStatus = "unpaid",
        paymentMethod = "cash",
        subtotalCents = 10_000,
        taxCents = 0,
        deliveryFeeCents = 4_900,
        totalCents = 14_900,
        fulfillmentMethod = FulfillmentMethod.Delivery.wire,
        deliveryAddress = "12 Session Road",
        deliveryStage = deliveryStage,
        riderName = null,
        riderPhone = null,
        placedAt = "2026-08-28T09:00:00+08:00",
        items = emptyList(),
    )

    private val orders = listOf(
        order("prep", "preparing"),
        order("ready", "ready"),
        order("rider", "ready", deliveryStage = "picked_up"),
        order("done", "completed"),
        order("gone", "cancelled"),
    )

    private fun visible(filter: OrderFilter) =
        OrdersUiState(orders = orders, filter = filter).visible.map { it.orderId }

    @Test
    fun `all shows everything, cancelled included`() {
        assertEquals(listOf("prep", "ready", "rider", "done", "gone"), visible(OrderFilter.All))
    }

    @Test
    fun `each stage chip selects its own bucket`() {
        assertEquals(listOf("prep"), visible(OrderFilter.Preparing))
        assertEquals(listOf("ready"), visible(OrderFilter.Ready))
        assertEquals(listOf("rider"), visible(OrderFilter.OnTheWay))
        assertEquals(listOf("done"), visible(OrderFilter.Completed))
    }

    @Test
    fun `active is every stage but completed`() {
        assertEquals(listOf("prep", "ready", "rider"), visible(OrderFilter.Active))
    }

    /**
     * A cancelled order is in no stage, so All is the only chip that finds it.
     * Worth pinning: it must not quietly vanish from the app entirely.
     */
    @Test
    fun `a cancelled order is reachable only under all`() {
        val chipsShowingIt = OrderFilter.entries.filter { "gone" in visible(it) }

        assertEquals(listOf(OrderFilter.All), chipsShowingIt)
    }

    /** The chips partition the list: every order is under All exactly once. */
    @Test
    fun `the stage chips together cover everything except cancelled`() {
        val staged = OrderFilter.entries
            .filter { it.stage != null }
            .flatMap { visible(it) }

        assertEquals(listOf("prep", "ready", "rider", "done").sorted(), staged.sorted())
        assertTrue(orders.filter { it.stage == null }.map { it.orderId } == listOf("gone"))
    }

    @Test
    fun `every stage has a chip, so an account tile can always land somewhere`() {
        OrderStage.entries.forEach { stage ->
            assertTrue(
                "no chip for $stage",
                OrderFilter.entries.any { it.stage == stage },
            )
        }
    }
}
