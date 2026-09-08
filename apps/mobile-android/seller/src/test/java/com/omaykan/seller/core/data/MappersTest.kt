package com.omaykan.seller.core.data

import com.omaykan.seller.core.model.DeliveryStage
import com.omaykan.seller.core.model.FulfillmentMethod
import com.omaykan.seller.core.model.OrderStatus
import com.omaykan.seller.core.network.dto.GuestContactDto
import com.omaykan.seller.core.network.dto.SellerOrderDto
import com.omaykan.seller.core.network.dto.SellerOrderItemDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The DTO boundary is where every assumption this app makes about the server's
 * shape is written down, so it is the boundary worth testing.
 */
class MappersTest {

    private fun dto(
        status: String? = "preparing",
        paymentStatus: String? = "unpaid",
        fulfillmentMethod: String? = "pickup",
        deliveryStage: String? = null,
        customerName: String? = "Ana",
        guestContact: GuestContactDto? = null,
        riderName: String? = null,
    ) = SellerOrderDto(
        id = "order-1",
        ticketNumber = "042",
        customerName = customerName,
        status = status,
        paymentStatus = paymentStatus,
        fulfillmentMethod = fulfillmentMethod,
        deliveryStage = deliveryStage,
        guestContact = guestContact,
        riderName = riderName,
        totalCents = 48_000,
        items = listOf(SellerOrderItemDto(productId = "p1", name = "Latte", quantity = 2.0)),
    )

    @Test
    fun `a pickup order has no delivery stage`() {
        // Even when the server sends one. A pickup order is not on a road, and
        // showing it a stage would offer a merchant controls that 422.
        val order = dto(fulfillmentMethod = "pickup", deliveryStage = "assigned").toModel()

        assertEquals(FulfillmentMethod.Pickup, order.fulfillmentMethod)
        assertNull(order.deliveryStage)
        assertFalse(order.needsRider)
    }

    @Test
    fun `a delivery with no stage yet reads as pending`() {
        val order = dto(fulfillmentMethod = "delivery", deliveryStage = null).toModel()

        assertEquals(DeliveryStage.Pending, order.deliveryStage)
        assertTrue(order.needsRider)
    }

    @Test
    fun `an unrecognised delivery stage reads as pending rather than vanishing`() {
        val order = dto(fulfillmentMethod = "delivery", deliveryStage = "teleported").toModel()

        assertEquals(DeliveryStage.Pending, order.deliveryStage)
    }

    @Test
    fun `an unrecognised status reads as preparing`() {
        // The safe reading of an unknown status is "still the shop's problem".
        assertEquals(OrderStatus.Preparing, dto(status = "quantum").toModel().status)
        assertEquals(OrderStatus.Preparing, dto(status = null).toModel().status)
    }

    @Test
    fun `only the exact string paid counts as paid`() {
        assertTrue(dto(paymentStatus = "paid").toModel().paid)
        assertFalse(dto(paymentStatus = "unpaid").toModel().paid)
        // Money nobody ever asks for is the failure mode this guards against.
        assertFalse(dto(paymentStatus = "refunded").toModel().paid)
        assertFalse(dto(paymentStatus = null).toModel().paid)
    }

    @Test
    fun `a nameless order still has a heading`() {
        val order = dto(customerName = "  ", guestContact = GuestContactDto(name = "Ben")).toModel()
        assertEquals("Ben", order.customerName)

        val anonymous = dto(customerName = null, guestContact = null).toModel()
        assertEquals("Customer", anonymous.customerName)
    }

    @Test
    fun `blank rider fields do not read as an assigned rider`() {
        assertNull(dto(fulfillmentMethod = "delivery", riderName = "  ").toModel().riderName)
    }

    @Test
    fun `item quantities survive as fractions`() {
        // A grocery sells 1.5 kg of something, and rounding it here would put a
        // wrong weight on the card a merchant packs from.
        val order = SellerOrderDto(
            id = "order-2",
            items = listOf(SellerOrderItemDto(productId = "p", name = "Rice", quantity = 1.5)),
        ).toModel()

        assertEquals(1.5, order.items.single().quantity, 0.0)
        assertEquals("1.5× Rice", order.itemSummary)
    }

    @Test
    fun `a whole quantity loses its decimal point on the card`() {
        val order = SellerOrderDto(
            id = "order-3",
            items = listOf(SellerOrderItemDto(productId = "p", name = "Latte", quantity = 2.0)),
        ).toModel()

        // "2× Latte", not "2.0× Latte" — the decimal is only there for the
        // things that are genuinely weighed.
        assertEquals("2× Latte", order.itemSummary)
    }
}
