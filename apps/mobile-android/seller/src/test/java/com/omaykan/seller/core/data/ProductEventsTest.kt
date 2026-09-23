package com.omaykan.seller.core.data

import com.omaykan.seller.core.model.Catalog
import com.omaykan.seller.core.model.Category
import com.omaykan.seller.core.model.Product
import com.omaykan.seller.core.model.ProductDraft
import com.omaykan.seller.core.model.StaffRole
import com.omaykan.seller.core.model.StorefrontFields
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProductEventsTest {

    private val strawberries = Product(
        id = "p1",
        name = "Strawberries",
        categoryId = "c1",
        sku = "STR-1",
        barcode = "4800000000017",
        productType = "weighted",
        taxRate = 0.0,
        priceCents = 18_000,
        trackInventory = true,
        active = true,
        businessModes = listOf("grocery"),
        unitLabel = "kg",
        imageUrl = null,
        stockQty = 50.0,
        lowStockThreshold = 8.0,
        branchPriceCents = null,
        branchAvailable = null,
    )

    private val bag = strawberries.copy(id = "p2", name = "Woven bag", trackInventory = false, stockQty = null)

    private val catalog = Catalog(
        organizationId = "org",
        storeId = "store",
        userName = "Rosa",
        role = StaffRole.Admin,
        categories = listOf(Category("c1", "Farm Produce", 0)),
        products = listOf(strawberries, bag),
    )

    private var counter = 0
    private val ids = { "id-${++counter}" }

    private fun draft(
        productId: String? = "p1",
        stock: Double? = 50.0,
        lowStock: Double? = 8.0,
    ) = ProductDraft(
        productId = productId,
        name = "  Baguio Strawberries ",
        categoryId = "c1",
        priceCents = 20_000,
        stockQty = stock,
        lowStockThreshold = lowStock,
        active = true,
    )

    @Test
    fun `an edit sends back every field the form never showed`() {
        val events = productEvents(catalog, draft(), ids, "now")

        val event = events.single()
        assertEquals("product", event.entityType)
        assertEquals("p1", event.entityId)

        val payload = event.payload
        assertEquals("Baguio Strawberries", payload["name"]!!.jsonPrimitive.content)
        assertEquals(20_000L, payload["priceCents"]!!.jsonPrimitive.long)
        // The ones a partial payload would have reset to the server's defaults.
        assertEquals("STR-1", payload["sku"]!!.jsonPrimitive.content)
        assertEquals("4800000000017", payload["barcode"]!!.jsonPrimitive.content)
        assertEquals("weighted", payload["productType"]!!.jsonPrimitive.content)
        assertEquals(0.0, payload["taxRate"]!!.jsonPrimitive.double, 0.0)
        assertEquals(listOf("grocery"), payload["businessModes"]!!.jsonArray.map { it.jsonPrimitive.content })
        // Ignored by the server for an existing product; not sent, so nobody
        // reads the payload and thinks it moved stock.
        assertFalse("stockQty" in payload)
    }

    /** An edit that never showed the photo must not be able to remove it. */
    @Test
    fun `without storefront fields the photo unit and description keys are not sent`() {
        val payload = productEvents(catalog, draft(), ids, "now").single().payload

        assertFalse("imageUrl" in payload)
        assertFalse("unitLabel" in payload)
        assertFalse("description" in payload)
    }

    @Test
    fun `storefront fields are sent as given and a cleared one is sent as null`() {
        val payload = productEvents(
            catalog,
            draft().copy(
                storefront = StorefrontFields(
                    imageUrl = "https://api.test/api/product-images/x.jpg",
                    unitLabel = "  kg ",
                    description = "   ",
                ),
            ),
            ids,
            "now",
        ).single().payload

        assertEquals("https://api.test/api/product-images/x.jpg", payload["imageUrl"]!!.jsonPrimitive.content)
        assertEquals("kg", payload["unitLabel"]!!.jsonPrimitive.content)
        // Blank is the merchant clearing it: present, and null.
        assertEquals(JsonNull, payload["description"])
    }

    @Test
    fun `a new count on the shelf becomes an adjustment by the difference`() {
        val events = productEvents(catalog, draft(stock = 45.0), ids, "now")

        assertEquals(2, events.size)
        val adjustment = events[1]
        assertEquals("inventory_adjustment", adjustment.entityType)
        assertEquals("p1", adjustment.payload["productId"]!!.jsonPrimitive.content)
        assertEquals(-5.0, adjustment.payload["quantityDelta"]!!.jsonPrimitive.double, 1e-9)
        assertEquals("manual_correction", adjustment.payload["adjustmentType"]!!.jsonPrimitive.content)
    }

    @Test
    fun `an unchanged count sends no adjustment`() {
        assertEquals(1, productEvents(catalog, draft(stock = 50.0), ids, "now").size)
    }

    @Test
    fun `a new product carries its opening stock and leaves business modes to the server`() {
        val events = productEvents(catalog, draft(productId = null, stock = 12.0), ids, "now")

        val event = events.single()
        assertEquals("create", event.operation)
        // Its own fresh id, distinct from the event's.
        assertTrue(event.entityId != event.id)
        assertEquals(12.0, event.payload["stockQty"]!!.jsonPrimitive.double, 0.0)
        assertFalse("businessModes" in event.payload)
        assertFalse("sku" in event.payload)
    }

    @Test
    fun `an emptied low-stock field is sent as an explicit null`() {
        val payload = productEvents(catalog, draft(lowStock = null), ids, "now").single().payload

        assertTrue("lowStockThreshold" in payload)
        assertEquals(JsonNull, payload["lowStockThreshold"])
    }

    @Test
    fun `an untracked product sends no threshold and no adjustment`() {
        val events = productEvents(catalog, draft(productId = "p2", stock = null), ids, "now")

        assertEquals(1, events.size)
        assertFalse("lowStockThreshold" in events.single().payload)
        assertEquals(false, events.single().payload["trackInventory"]!!.jsonPrimitive.content.toBoolean())
    }
}
