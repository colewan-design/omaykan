package com.omaykan.seller.core.notify

import com.omaykan.seller.core.model.FulfillmentMethod
import com.omaykan.seller.core.model.OrderStatus
import com.omaykan.seller.core.model.SellerOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The rules behind the sound a shop hears. Both of them are the kind that are
 * only obviously right once somebody has got them wrong on a busy morning.
 */
class NewOrderWatchTest {

    private fun order(id: String, status: OrderStatus = OrderStatus.Preparing) = SellerOrder(
        id = id,
        ticketNumber = id,
        customerName = "Ana",
        customerPhone = null,
        status = status,
        fulfillmentMethod = FulfillmentMethod.Pickup,
        deliveryStage = null,
        deliveryAddress = null,
        riderName = null,
        riderPhone = null,
        // No platform rider on these fixtures: none of them is about a map.
        riderId = null,
        riderPosition = null,
        route = null,
        paid = false,
        paymentMethod = null,
        subtotalCents = 0,
        deliveryFeeCents = 0,
        totalCents = 0,
        placedAt = null,
        items = emptyList(),
    )

    @Test
    fun `the first list is a baseline, not news`() {
        // Opening the app to a shop's last hundred orders must not fire a
        // hundred alerts for work that was done hours ago.
        val watch = NewOrderWatch()

        val arrivals = watch.arrivals(listOf(order("a"), order("b"), order("c")))

        assertTrue(arrivals.isEmpty())
    }

    @Test
    fun `only ids never seen before are announced`() {
        val watch = NewOrderWatch()
        watch.arrivals(listOf(order("a"), order("b")))

        val arrivals = watch.arrivals(listOf(order("c"), order("a"), order("b")))

        assertEquals(listOf("c"), arrivals.map { it.id })
    }

    @Test
    fun `an order that merely changed is not a new order`() {
        // The merchant usually caused the change themselves, by tapping the
        // button on its card a second earlier.
        val watch = NewOrderWatch()
        watch.arrivals(listOf(order("a", OrderStatus.Preparing)))

        val arrivals = watch.arrivals(listOf(order("a", OrderStatus.Ready)))

        assertTrue(arrivals.isEmpty())
    }

    @Test
    fun `an order that disappears and comes back is announced again`() {
        // Rare, and the honest reading: the app was not told what happened in
        // between, so it treats the reappearance as news rather than silently
        // assuming it knows.
        val watch = NewOrderWatch()
        watch.arrivals(listOf(order("a")))
        watch.arrivals(emptyList())

        assertEquals(listOf("a"), watch.arrivals(listOf(order("a"))).map { it.id })
    }

    @Test
    fun `resetting makes the next list a fresh baseline`() {
        // What happens when the phone is paired with a different branch. Without
        // it, the new shop's whole order list would arrive as alerts.
        val watch = NewOrderWatch()
        watch.arrivals(listOf(order("a")))

        watch.reset()

        assertTrue(watch.arrivals(listOf(order("x"), order("y"))).isEmpty())
    }
}
