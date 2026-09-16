package com.omaykan.seller.core.data

import com.omaykan.seller.core.model.StaffRole
import com.omaykan.seller.core.network.dto.BootstrapDto
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `/sync/bootstrap` serialises Eloquent models as they are: snake_case keys,
 * and decimal casts as strings. These pin the reading of that shape.
 */
class CatalogMappersTest {

    // The same configuration NetworkModule gives Retrofit.
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        coerceInputValues = true
    }

    private val payload = """
        {
          "organization": {"id": "org-1", "name": "Rosa's Farm", "slug": "rosas-farm"},
          "store": {"id": "store-1", "name": "Main", "code": "MAIN", "timezone": "Asia/Manila"},
          "user": {"id": "u1", "fullName": "Rosa Dela Cruz", "roleId": "manager"},
          "catalog": {
            "categories": [
              {"id": "c2", "name": "Souvenirs", "sort_order": 2},
              {"id": "c1", "name": "Farm Produce", "sort_order": "1"}
            ],
            "products": [
              {"id": "p1", "name": "Strawberries", "category_id": "c1", "sku": null,
               "product_type": "weighted", "tax_rate": "12.00", "price_cents": 18000,
               "track_inventory": true, "is_active": true, "business_modes": ["grocery"],
               "image_url": "/storage/p1.jpg", "unit_label": "kg", "low_stock_threshold": null},
              {"id": "p2", "name": "Coffee", "category_id": "c1", "tax_rate": "12.00",
               "price_cents": "35000", "track_inventory": true, "is_active": false,
               "business_modes": null},
              {"id": "p3", "name": "Woven Bag", "track_inventory": false, "is_active": true,
               "price_cents": 125000, "image_url": "https://cdn.example/bag.png"}
            ],
            "overrides": [{"product_id": "p1", "price_cents": 17000, "is_available": null}],
            "inventoryLevels": [{"product_id": "p1", "qty_on_hand": "5.000", "reorder_level": "8.000"}]
          },
          "cursor": "2026-09-11T08:00:00+08:00"
        }
    """.trimIndent()

    private val catalog = json.decodeFromString<BootstrapDto>(payload).toCatalog("https://omaykan.com/")

    @Test
    fun `reads who is signed in and what they may do`() {
        assertEquals("org-1", catalog.organizationId)
        assertEquals("store-1", catalog.storeId)
        assertEquals("Rosa Dela Cruz", catalog.userName)
        assertEquals(StaffRole.Manager, catalog.role)
        assertTrue(catalog.role.managesCatalog)
    }

    @Test
    fun `orders categories by their sort order, number or string`() {
        assertEquals(listOf("c1", "c2"), catalog.categories.map { it.id })
    }

    @Test
    fun `reads decimal strings, branch overrides and relative images`() {
        val p1 = catalog.products.first { it.id == "p1" }

        assertEquals(12.0, p1.taxRate!!, 0.0)
        assertEquals(5.0, p1.stockQty!!, 0.0)
        assertEquals(8.0, p1.lowStockThreshold!!, 0.0)
        assertTrue(p1.lowStock)
        assertEquals(18_000L, p1.priceCents)
        assertEquals(17_000L, p1.shownPriceCents)
        assertNull(p1.branchAvailable)
        assertEquals("https://omaykan.com/storage/p1.jpg", p1.imageUrl)
        assertEquals("5 kg", p1.stockLabel)
    }

    @Test
    fun `a tracked product with no inventory row has nothing on the shelf`() {
        val p2 = catalog.products.first { it.id == "p2" }

        assertEquals(35_000L, p2.priceCents)
        assertEquals(0.0, p2.stockQty!!, 0.0)
        assertTrue(p2.soldOut)
        assertTrue(p2.businessModes.isEmpty())
    }

    @Test
    fun `an untracked product has no stock and is never low`() {
        val p3 = catalog.products.first { it.id == "p3" }

        assertNull(p3.stockQty)
        assertFalse(p3.lowStock)
        assertEquals("https://cdn.example/bag.png", p3.imageUrl)
    }

    @Test
    fun `counts low stock among active products only`() {
        // p1 is low and active; p2 is empty but inactive; p3 is untracked.
        assertEquals(1, catalog.lowStockCount)
    }
}
