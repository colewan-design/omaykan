package com.omaykan.storefront.feature.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * These names are the `name` column of every category in
 * backend/database/seeders/data/demo-catalog.json — the strings the API
 * actually serves, as opposed to that file's `id` slugs, which stop at the
 * seeder. An earlier cut of [categoryIcon] keyed on the slugs and so matched
 * nothing on a real device while still compiling and shipping every asset.
 * That is what this guards.
 */
private val SEEDED_NAMES = listOf(
    "Coffee", "Tea", "Pastry", "Cold Drinks",
    "Groceries", "Produce", "Dairy", "Snacks",
    "Meat & Seafood", "Bakery", "Frozen", "International",
    "Ready to Cook", "Ready to Eat",
    "Starters", "Mains", "Desserts", "Beverages",
    "Manicures", "Pedicures", "Enhancements", "Add-ons", "Retail",
)

class CategoryIconsTest {

    @Test
    fun `every seeded aisle name has a glyph`() {
        val missing = SEEDED_NAMES.filter { categoryIcon(it) == null }
        assertEquals("aisles left without a glyph", emptyList<String>(), missing)
    }

    @Test
    fun `each seeded aisle gets its own glyph`() {
        val icons = SEEDED_NAMES.mapNotNull { categoryIcon(it) }
        assertEquals("two aisles share one glyph", icons.size, icons.toSet().size)
    }

    @Test
    fun `casing and stray whitespace still match`() {
        assertEquals(categoryIcon("Cold Drinks"), categoryIcon("  cold   drinks "))
        assertEquals(categoryIcon("Coffee"), categoryIcon("COFFEE"))
    }

    @Test
    fun `a merchant's own aisle falls through to the letter`() {
        assertNull(categoryIcon("Vape"))
        assertNull(categoryIcon("Hardware"))
        // Substrings must not match: a freezer aisle and a dessert are not the
        // same shelf, and the letter is better than confidently drawing the
        // wrong one.
        assertNotNull(categoryIcon("Frozen"))
        assertNull(categoryIcon("Frozen Yogurt"))
    }
}
