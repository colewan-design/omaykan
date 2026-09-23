package com.omaykan.seller.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `isOpen` decides what the Live tab holds and what the count on it says, so
 * the two can never disagree — and getting it wrong in either direction is a
 * real failure: an order dropped off Live is an order nobody makes, and one
 * that never leaves it is a badge that stops meaning anything.
 */
class SellerOrderTest {

    private fun order(
        status: OrderStatus = OrderStatus.Preparing,
        paid: Boolean = true,
        method: FulfillmentMethod = FulfillmentMethod.Pickup,
        stage: DeliveryStage? = null,
    ) = SellerOrder(
        id = "o",
        ticketNumber = "1",
        customerName = "Ana",
        customerPhone = null,
        status = status,
        fulfillmentMethod = method,
        deliveryStage = stage,
        deliveryAddress = null,
        riderName = null,
        riderPhone = null,
        // No platform rider on these fixtures: none of them is about a map.
        riderId = null,
        riderPosition = null,
        route = null,
        paid = paid,
        paymentMethod = null,
        subtotalCents = 0,
        deliveryFeeCents = 0,
        totalCents = 0,
        placedAt = null,
        items = emptyList(),
    )

    @Test
    fun `a served and paid pickup is finished`() {
        assertFalse(order(status = OrderStatus.Served, paid = true).isOpen)
    }

    @Test
    fun `a served but unpaid order is still open`() {
        // Collected but not paid for is not finished, whatever the kitchen
        // thinks. This is the one that would otherwise quietly cost money.
        assertTrue(order(status = OrderStatus.Served, paid = false).isOpen)
    }

    @Test
    fun `a served and paid delivery still on the road is open`() {
        assertTrue(
            order(
                status = OrderStatus.Served,
                paid = true,
                method = FulfillmentMethod.Delivery,
                stage = DeliveryStage.PickedUp,
            ).isOpen,
        )
    }

    @Test
    fun `a delivered, served and paid delivery is finished`() {
        assertFalse(
            order(
                status = OrderStatus.Served,
                paid = true,
                method = FulfillmentMethod.Delivery,
                stage = DeliveryStage.Delivered,
            ).isOpen,
        )
    }

    @Test
    fun `only a delivery can need a rider`() {
        assertTrue(order(method = FulfillmentMethod.Delivery, stage = DeliveryStage.Pending).needsRider)
        assertFalse(order(method = FulfillmentMethod.Delivery, stage = DeliveryStage.Assigned).needsRider)
        assertFalse(order(method = FulfillmentMethod.Pickup, stage = null).needsRider)
    }

    @Test
    fun `the status button walks preparing to ready to done, then stops`() {
        assertEquals(OrderStatus.Ready, OrderStatus.Preparing.next)
        assertEquals(OrderStatus.Served, OrderStatus.Ready.next)
        assertNull(OrderStatus.Served.next)
    }

    @Test
    fun `pending has no next stage, because naming a rider is what moves it`() {
        // Advancing off Pending needs a name and a number — a different
        // endpoint and a different gesture — so the card offers "Assign rider"
        // there rather than a stage button that would 422.
        assertNull(DeliveryStage.Pending.next)
        assertEquals(DeliveryStage.PickedUp, DeliveryStage.Assigned.next)
        assertEquals(DeliveryStage.Delivered, DeliveryStage.PickedUp.next)
        assertNull(DeliveryStage.Delivered.next)
    }
}
