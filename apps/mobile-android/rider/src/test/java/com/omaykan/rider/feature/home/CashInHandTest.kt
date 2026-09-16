package com.omaykan.rider.feature.home

import com.omaykan.rider.core.data.WorkState
import com.omaykan.rider.core.model.DeliveryAssignment
import com.omaykan.rider.core.model.DeliveryOffer
import com.omaykan.rider.core.model.DeliveryStage
import com.omaykan.rider.core.model.Pickup
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The one figure the home screen works out for itself.
 *
 * Everything else on that screen is either the server's aggregate or a list
 * length. This is a sum, it is money, and getting it wrong sends a rider to a
 * counter with the wrong number in their head — so the three things it must not
 * count are pinned here rather than left to the reading of a one-line
 * `sumOf`.
 */
class CashInHandTest {

    private fun offer(id: String, collectCents: Long) = DeliveryOffer(
        id = id,
        ticketNumber = null,
        placedAt = null,
        pickup = Pickup("s1", "Aling Nena's Kitchen", null, null, null),
        dropoffArea = null,
        distanceKm = null,
        deliveryFeeCents = 6500,
        itemCount = 1,
        paid = collectCents == 0L,
        collectCents = collectCents,
    )

    private fun job(id: String, collectCents: Long, stage: DeliveryStage) = DeliveryAssignment(
        offer = offer(id, collectCents),
        stage = stage,
        acceptedAt = null,
        deliveryAddress = null,
        deliveryLat = null,
        deliveryLng = null,
        customerName = null,
        customerPhone = null,
        items = emptyList(),
    )

    @Test
    fun `adds up the cash on every job in hand`() {
        val state = WorkState(
            active = listOf(
                job("a", 54_000, DeliveryStage.Assigned),
                job("b", 21_500, DeliveryStage.PickedUp),
            ),
        )

        assertEquals(75_500L, state.cashInHand)
    }

    @Test
    fun `prepaid jobs in hand add nothing`() {
        val state = WorkState(
            active = listOf(
                job("a", 54_000, DeliveryStage.Assigned),
                job("b", 0, DeliveryStage.PickedUp),
            ),
        )

        assertEquals(54_000L, state.cashInHand)
    }

    /**
     * The board is other people's work until somebody claims it, and a finished
     * job's cash was handed over at the counter. Counting either would put a
     * number on the home screen that no amount of notes in a pocket matches.
     */
    @Test
    fun `the board and the finished list are not cash in hand`() {
        val state = WorkState(
            board = listOf(offer("x", 90_000)),
            active = emptyList(),
            completed = listOf(job("y", 33_000, DeliveryStage.Delivered)),
        )

        assertEquals(0L, state.cashInHand)
    }

    @Test
    fun `nothing in hand is zero, not empty`() {
        assertEquals(0L, WorkState().cashInHand)
    }
}
