package com.omaykan.rider.core.alerts

import com.omaykan.rider.core.model.DeliveryOffer
import com.omaykan.rider.core.model.Pickup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The one rule in the job watcher worth a test: what counts as new.
 *
 * Everything else about the feature is Android — a service, two channels, a
 * notification — and none of it decides anything. This does, and it has two
 * failure modes that both end with the rider switching alerts off: announcing
 * the whole board the first time, or announcing the same job every minute until
 * somebody takes it.
 */
class NewJobFilterTest {

    private fun offer(id: String) = DeliveryOffer(
        id = id,
        ticketNumber = null,
        placedAt = null,
        pickup = Pickup(
            storeId = "s1",
            storeName = "Aling Nena's Kitchen",
            address = null,
            lat = null,
            lng = null,
        ),
        dropoffArea = "Sto. Tomas, Baguio City",
        distanceKm = 2.4,
        deliveryFeeCents = 6500,
        itemCount = 3,
        paid = true,
        collectCents = 0,
    )

    @Test
    fun `the first board is learned, not announced`() {
        val filter = NewJobFilter()

        // Forty notifications the moment somebody switches this on is how a
        // rider learns to switch it off again.
        assertTrue(filter.newIn(listOf(offer("a"), offer("b"))).isEmpty())
    }

    @Test
    fun `only a job that was not there last time is announced`() {
        val filter = NewJobFilter()
        filter.newIn(listOf(offer("a")))

        val appeared = filter.newIn(listOf(offer("a"), offer("b")))

        assertEquals(listOf("b"), appeared.map { it.id })
    }

    @Test
    fun `a job still sitting on the board is not announced twice`() {
        val filter = NewJobFilter()
        filter.newIn(listOf(offer("a")))
        filter.newIn(listOf(offer("a"), offer("b")))

        assertTrue(filter.newIn(listOf(offer("a"), offer("b"))).isEmpty())
    }

    @Test
    fun `a job that left the board and came back is news again`() {
        val filter = NewJobFilter()
        filter.newIn(listOf(offer("a"), offer("b")))

        // Somebody took b, then handed it back.
        filter.newIn(listOf(offer("a")))
        val appeared = filter.newIn(listOf(offer("a"), offer("b")))

        assertEquals(listOf("b"), appeared.map { it.id })
    }

    @Test
    fun `an empty board announces nothing and forgets nothing it needs`() {
        val filter = NewJobFilter()
        filter.newIn(listOf(offer("a")))

        assertTrue(filter.newIn(emptyList()).isEmpty())

        // a is genuinely gone now, so its return is news.
        assertEquals(listOf("a"), filter.newIn(listOf(offer("a"))).map { it.id })
    }
}
