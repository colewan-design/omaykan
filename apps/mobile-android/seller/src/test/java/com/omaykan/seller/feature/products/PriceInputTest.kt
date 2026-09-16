package com.omaykan.seller.feature.products

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PriceInputTest {

    @Test
    fun `reads prices the way a merchant types them`() {
        assertEquals(18_000L, parsePriceCents("180"))
        assertEquals(18_050L, parsePriceCents("180.5"))
        assertEquals(18_050L, parsePriceCents(" 180.50 "))
        assertEquals(125_000L, parsePriceCents("1,250.00"))
        assertEquals(0L, parsePriceCents("0"))
    }

    @Test
    fun `refuses anything that is not a price rather than rounding it`() {
        assertNull(parsePriceCents("180.555"))
        assertNull(parsePriceCents("-1"))
        assertNull(parsePriceCents("abc"))
        assertNull(parsePriceCents(""))
    }

    @Test
    fun `writes a stored price back the way it would be typed`() {
        assertEquals("180", centsToInput(18_000))
        assertEquals("180.50", centsToInput(18_050))
        assertEquals("0.05", centsToInput(5))
    }
}
