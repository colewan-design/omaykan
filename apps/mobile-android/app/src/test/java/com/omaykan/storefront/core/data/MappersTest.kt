package com.omaykan.storefront.core.data

import com.omaykan.storefront.core.network.dto.StoreDirectoryEntryDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MappersTest {

    @Test
    fun `a shop photo path is resolved against the API origin`() {
        // What StoreImageController::urlFor actually returns.
        assertEquals(
            "https://omaykan.com/api/stores/abc/image?v=1a2b3c4d",
            absoluteUrl("https://omaykan.com", "/api/stores/abc/image?v=1a2b3c4d"),
        )
    }

    @Test
    fun `an absolute product image is left alone`() {
        val url = "https://cdn.example.com/coffee.jpg"
        assertEquals(url, absoluteUrl("https://omaykan.com", url))
    }

    @Test
    fun `a blank image is null rather than the bare origin`() {
        assertNull(absoluteUrl("https://omaykan.com", ""))
        assertNull(absoluteUrl("https://omaykan.com", "   "))
        assertNull(absoluteUrl("https://omaykan.com", null))
    }

    @Test
    fun `a directory row with no org slug is dropped rather than shown`() {
        // The organization relation is nullable on the wire, and a card with no
        // slug is a card that opens nothing.
        val orphan = StoreDirectoryEntryDto(orgSlug = null, storeCode = "main", name = "Ghost")

        assertNull(orphan.toModelOrNull("https://omaykan.com"))
    }

    @Test
    fun `a complete directory row keeps its distance and count`() {
        val dto = StoreDirectoryEntryDto(
            orgSlug = "demo-coffee",
            storeCode = "main",
            name = "Demo Coffee",
            businessMode = "coffee-shop",
            productCount = 12,
            distanceKm = 1.4,
        )

        val shop = dto.toModelOrNull("https://omaykan.com")

        assertEquals("demo-coffee/main", shop?.ref?.key)
        assertEquals(12, shop?.productCount)
        assertEquals(1.4, shop?.distanceKm)
    }
}
