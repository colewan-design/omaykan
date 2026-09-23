package com.omaykan.storefront.feature.home

import androidx.annotation.DrawableRes
import com.omaykan.storefront.R

/**
 * A glyph per aisle, looked up by the category's *name*.
 *
 * The web's apps/web/src/landing/categories.ts keys the same artwork by
 * category id, but it can: the landing page reads a client-side demo dataset
 * whose ids are the seed slugs. This app reads the API, and
 * StorefrontCatalogController serves `categories.id` straight from a uuid
 * column — the slugs in demo-catalog.json exist only while seeding and never
 * reach a device. Keying by id here matched nothing at all.
 *
 * The name is what survives the wire, and it is also the thing the merchant
 * actually authored, so a shop that files its own aisle under "Coffee" gets
 * the cup without being in anyone's taxonomy. Matching is on the whole
 * normalised name, never a substring: "Frozen" is a freezer aisle, "Frozen
 * Yogurt" is a dessert, and guessing between them is exactly the mislabel
 * [CategoryRail]'s letter fallback exists to avoid.
 */
@DrawableRes
internal fun categoryIcon(categoryName: String): Int? =
    when (categoryName.trim().lowercase().replace(WHITESPACE, " ")) {
        // Grocery aisles
        "groceries" -> R.drawable.ic_cat_groceries
        "produce" -> R.drawable.ic_cat_produce
        "dairy" -> R.drawable.ic_cat_dairy
        "snacks" -> R.drawable.ic_cat_snacks
        "meat & seafood", "meat and seafood" -> R.drawable.ic_cat_meat_seafood
        "bakery" -> R.drawable.ic_cat_bakery
        "frozen" -> R.drawable.ic_cat_frozen
        "international" -> R.drawable.ic_cat_international
        "ready to cook" -> R.drawable.ic_cat_ready_to_cook
        "ready to eat" -> R.drawable.ic_cat_ready_to_eat
        // Coffee shop
        "coffee" -> R.drawable.ic_cat_coffee
        "tea" -> R.drawable.ic_cat_tea
        "pastry" -> R.drawable.ic_cat_pastry
        "cold drinks" -> R.drawable.ic_cat_cold_drinks
        // Restaurant
        "starters" -> R.drawable.ic_cat_starters
        "mains" -> R.drawable.ic_cat_mains
        "desserts" -> R.drawable.ic_cat_desserts
        "beverages" -> R.drawable.ic_cat_beverages
        // Nail salon. "Enhancements", "Add-ons" and "Retail" are the seeded
        // salon names but generic words on their own, so a grocery with a
        // "Retail" aisle would draw a polish bottle. They are here because the
        // salon is a real tenant and the miss costs more than the collision;
        // drop these three if that trade stops being worth it.
        "manicures" -> R.drawable.ic_cat_manicures
        "pedicures" -> R.drawable.ic_cat_pedicures
        "enhancements" -> R.drawable.ic_cat_nail_enhancements
        "add-ons", "add ons", "addons" -> R.drawable.ic_cat_nail_addons
        "retail" -> R.drawable.ic_cat_salon_retail
        else -> null
    }

private val WHITESPACE = Regex("""\s+""")
