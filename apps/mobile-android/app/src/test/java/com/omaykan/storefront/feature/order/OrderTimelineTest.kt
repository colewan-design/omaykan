package com.omaykan.storefront.feature.order

import com.omaykan.storefront.core.model.FulfillmentMethod
import com.omaykan.storefront.core.model.TrackedOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The rail on the tracking screen.
 *
 * Worth its own tests for the same reason OrderStageTest is: the lit step is
 * read as a promise. Somebody who sees "On the way" believes a rider is holding
 * their order, and the only thing standing behind that belief is this function.
 */
class OrderTimelineTest {

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

    private fun current(order: TrackedOrder) = trackSteps(order).single {
        it.state == StepState.Current
    }

    /** Exactly one step lit, whatever the pair of wire fields says. */
    @Test
    fun `every order has one step in force`() {
        listOf(
            order(status = "preparing"),
            order(status = "ready"),
            order(status = "served"),
            order(status = "completed"),
            order(status = "preparing", deliveryStage = "assigned"),
            order(status = "ready", deliveryStage = "picked_up"),
            order(status = "cancelled"),
            order(status = "preparing", fulfillment = FulfillmentMethod.Pickup.wire),
            order(status = "completed", fulfillment = FulfillmentMethod.Pickup.wire),
        ).forEach { assertEquals(1, trackSteps(it).count { step -> step.state == StepState.Current }) }
    }

    @Test
    fun `a fresh order is being prepared, with the placing behind it`() {
        val steps = trackSteps(order(status = "preparing"))

        assertEquals(StepState.Done, steps[0].state)
        assertEquals("Preparing", current(order(status = "preparing")).label)
        assertEquals(StepState.Pending, steps[2].state)
    }

    /**
     * `served` is the till's word for handed over, and the app's terminal set
     * deliberately does not include it — so it reads as Ready here too, exactly
     * as it does on the account page and in the Orders tab.
     */
    @Test
    fun `served reads as ready, not delivered`() {
        assertEquals("Ready", current(order(status = "served")).label)
    }

    /**
     * A rider attached to the order outranks the kitchen status, matching
     * TrackedOrder.stage. Both wire words for it count.
     */
    @Test
    fun `a rider holding the order lights the rider step`() {
        val assigned = trackSteps(order(status = "preparing", deliveryStage = "assigned"))
        val pickedUp = trackSteps(order(status = "ready", deliveryStage = "picked_up"))

        assertEquals(3, assigned.indexOfFirst { it.state == StepState.Current })
        assertEquals(3, pickedUp.indexOfFirst { it.state == StepState.Current })
    }

    /** Accepted is not collected, and the headline says which. */
    @Test
    fun `an accepted order reads rider assigned until it is picked up`() {
        assertEquals("Rider assigned", current(order(status = "preparing", deliveryStage = "assigned")).label)
        assertEquals("On the way", current(order(status = "ready", deliveryStage = "picked_up")).label)
    }

    /** And the two are told apart in the line underneath. */
    @Test
    fun `the rider line says whether the order has been collected yet`() {
        assertEquals(
            "The rider has your order.",
            current(order(status = "ready", deliveryStage = "picked_up")).detail,
        )
        assertEquals(
            "A rider is on their way to the shop.",
            current(order(status = "ready", deliveryStage = "assigned")).detail,
        )
    }

    /** A delivered run the till has not closed out yet is still delivered. */
    @Test
    fun `a delivered stage lights the end of the rail`() {
        val steps = trackSteps(order(status = "ready", deliveryStage = "delivered"))

        assertEquals("Delivered", steps.last().label)
        assertEquals(StepState.Current, steps.last().state)
        assertEquals(emptyList<TrackStep>(), steps.filter { it.state == StepState.Pending })
    }

    /** And so is a completed order whose delivery stage never moved. */
    @Test
    fun `a completed status lights the end of the rail`() {
        assertEquals("Delivered", current(order(status = "completed")).label)
    }

    /** The rail never goes backwards: the furthest of the two fields wins. */
    @Test
    fun `the further of the two fields decides`() {
        assertEquals("Ready", current(order(status = "ready", deliveryStage = "pending")).label)
        assertEquals("On the way", current(order(status = "preparing", deliveryStage = "picked_up")).label)
    }

    @Test
    fun `a pickup order runs on the till status alone`() {
        val pickup = { status: String ->
            order(status = status, fulfillment = FulfillmentMethod.Pickup.wire)
        }

        assertEquals(4, trackSteps(pickup("preparing")).size)
        assertEquals("Preparing", current(pickup("preparing")).label)
        assertEquals("Ready for pick-up", current(pickup("ready")).label)
        assertEquals("Picked up", current(pickup("completed")).label)
    }

    /** A pickup order has no rail step that could mention a rider. */
    @Test
    fun `a pickup order never mentions a rider`() {
        val labels = trackSteps(
            order(status = "ready", fulfillment = FulfillmentMethod.Pickup.wire),
        ).map { it.label }

        assertEquals(listOf("Order placed", "Preparing", "Ready for pick-up", "Picked up"), labels)
    }

    /**
     * Cancelling wins over everything, including a rider mid-run. A rail still
     * showing an order on its way somewhere would be the worst thing this
     * screen could say.
     */
    @Test
    fun `a cancelled order is one step and nothing else`() {
        listOf("cancelled", "voided").forEach { status ->
            val steps = trackSteps(order(status = status, deliveryStage = "picked_up"))

            assertEquals(1, steps.size)
            assertEquals("Cancelled", steps.single().label)
            assertEquals(StepState.Current, steps.single().state)
        }
    }

    /** Commentary belongs to the step in force; a finished one needs none. */
    @Test
    fun `only the step in force carries a detail line`() {
        val steps = trackSteps(order(status = "ready", deliveryStage = "picked_up"))

        steps.filterNot { it.state == StepState.Current }.forEach { assertNull(it.detail) }
    }
}
