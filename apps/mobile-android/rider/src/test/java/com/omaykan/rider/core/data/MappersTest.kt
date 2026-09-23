package com.omaykan.rider.core.data

import com.omaykan.rider.core.model.DeliveryStage
import com.omaykan.rider.core.model.RiderStatus
import com.omaykan.rider.core.network.dto.AssignmentDto
import com.omaykan.rider.core.network.dto.AssignmentItemDto
import com.omaykan.rider.core.network.dto.OfferDto
import com.omaykan.rider.core.network.dto.PickupDto
import com.omaykan.rider.core.network.dto.RiderDto
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Where every assumption this app makes about the server's shape is written
 * down — so these are the assumptions worth a test.
 *
 * The payloads below are copied from `RiderDeliveryController::asOffer` and
 * `asAssignment` rather than invented, including the fields this app ignores.
 */
class MappersTest {

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        coerceInputValues = true
    }

    @Test
    fun `an unpaid order says what there is to collect`() {
        val offer = OfferDto(
            id = "order-1",
            deliveryFeeCents = 6500,
            paymentStatus = "unpaid",
            collectCents = 41000,
        ).toModel()

        assertFalse(offer.paid)
        assertTrue(offer.collectsCash)
        assertEquals(41000L, offer.collectCents)
    }

    @Test
    fun `a paid order collects nothing`() {
        val offer = OfferDto(id = "order-1", paymentStatus = "paid", collectCents = 0).toModel()

        assertTrue(offer.paid)
        assertFalse(offer.collectsCash)
    }

    /**
     * The asymmetry that matters: an order wrongly shown unpaid gets a question
     * at the door, and one wrongly shown paid is a rider out of pocket for the
     * whole basket. So only the exact string counts.
     */
    @Test
    fun `an unrecognised payment status is not paid`() {
        listOf("refunded", "partial", "PAID", null).forEach { status ->
            val offer = OfferDto(id = "order-1", paymentStatus = status).toModel()
            assertFalse("payment_status=$status must not read as paid", offer.paid)
        }
    }

    @Test
    fun `a ticket number arrives as a number and reads as text`() {
        val offer = OfferDto(id = "o", ticketNumber = JsonPrimitive(42)).toModel()

        assertEquals("42", offer.ticketNumber)
    }

    /** A string ticket must not keep the quotes a naive toString would leave. */
    @Test
    fun `a ticket number arrives as a string and keeps no quotes`() {
        val offer = OfferDto(id = "o", ticketNumber = JsonPrimitive("A-12")).toModel()

        assertEquals("A-12", offer.ticketNumber)
    }

    @Test
    fun `an absent ticket number is null`() {
        assertNull(OfferDto(id = "o").toModel().ticketNumber)
    }

    @Test
    fun `an assignment carries what the offer withheld`() {
        val assignment = AssignmentDto(
            id = "order-1",
            pickup = PickupDto(storeId = "s1", storeName = "Baguio Fresh", lat = 16.4, lng = 120.6),
            deliveryStage = "picked_up",
            deliveryAddress = "12 Session Road, Baguio City",
            customerName = "Ana",
            customerPhone = "09171234567",
            items = listOf(AssignmentItemDto("Pandesal", 2.0)),
        ).toModel()

        assertEquals(DeliveryStage.PickedUp, assignment.stage)
        assertEquals("12 Session Road, Baguio City", assignment.deliveryAddress)
        assertEquals("Ana", assignment.customerName)
        assertEquals("09171234567", assignment.customerPhone)
        assertEquals("2× Pandesal", assignment.items.single().label)
        assertEquals("Baguio Fresh", assignment.offer.pickup.storeName)
    }

    /** A grocery sells 1.5 kg of something; the label must not say "1.5×" wrong. */
    @Test
    fun `a fractional quantity keeps its decimal and a whole one drops it`() {
        val assignment = AssignmentDto(
            id = "o",
            items = listOf(AssignmentItemDto("Rice", 1.5), AssignmentItemDto("Egg", 12.0)),
        ).toModel()

        assertEquals("1.5× Rice", assignment.items[0].label)
        assertEquals("12× Egg", assignment.items[1].label)
    }

    /**
     * Unknown reads as Assigned — the earliest stage a claimed job can be in. It
     * costs a rider one extra tap; the other fallback would hide a live job
     * from the person carrying it.
     */
    @Test
    fun `an unknown delivery stage reads as assigned`() {
        assertEquals(DeliveryStage.Assigned, DeliveryStage.fromWire("teleported"))
        assertEquals(DeliveryStage.Assigned, DeliveryStage.fromWire(null))
    }

    @Test
    fun `a released job is only releasable before pickup`() {
        val assigned = AssignmentDto(id = "o", deliveryStage = "assigned").toModel()
        val pickedUp = AssignmentDto(id = "o", deliveryStage = "picked_up").toModel()

        assertTrue(assigned.canRelease)
        assertFalse(pickedUp.canRelease)
    }

    @Test
    fun `an unknown rider status reads as pending, never approved`() {
        assertEquals(RiderStatus.Pending, RiderStatus.fromWire("under_appeal"))
        assertEquals(RiderStatus.Pending, RiderStatus.fromWire(null))
    }

    @Test
    fun `a blank store name falls back to something a rider can read`() {
        val offer = OfferDto(id = "o", pickup = PickupDto(storeName = "  ")).toModel()

        assertEquals("Shop", offer.pickup.storeName)
    }

    /**
     * The whole board payload, as the controller sends it, decoded by the same
     * Json the app builds. Guards the two settings that matter: unknown keys
     * are ignored, and nulls land on defaults rather than failing the list.
     */
    @Test
    fun `a board payload with unknown keys and nulls decodes whole`() {
        val body = """
            {"orders":[
              {"id":"a1","ticketNumber":7,"placedAt":"2026-08-29T10:00:00+08:00",
               "orderStatus":"preparing","somethingNew":{"nested":true},
               "pickup":{"storeId":"s1","storeName":"Session Road Grill",
                         "address":null,"lat":null,"lng":null},
               "dropoffArea":"Sto. Tomas, Baguio City","distanceKm":null,
               "deliveryFeeCents":6500,"itemCount":3,
               "paymentStatus":"unpaid","collectCents":41000}
            ]}
        """.trimIndent()

        val offers = json.decodeFromString<com.omaykan.rider.core.network.dto.BoardDto>(body)
            .orders
            .map { it.toModel() }

        assertEquals(1, offers.size)
        assertEquals("7", offers[0].ticketNumber)
        assertNull(offers[0].distanceKm)
        assertNull(offers[0].pickup.address)
        assertEquals("Sto. Tomas, Baguio City", offers[0].dropoffArea)
        assertTrue(offers[0].collectsCash)
    }

    @Test
    fun `a rider payload reads its status off the wire string`() {
        val rider = RiderDto(id = "r1", name = "Ana", status = "suspended").toModel()

        assertEquals(RiderStatus.Suspended, rider.status)
    }

    /** Blank is not a review note. An empty grey box explains nothing. */
    @Test
    fun `a blank review note is dropped`() {
        assertNull(RiderDto(id = "r1", reviewNote = "   ").toModel().reviewNote)
    }
}
