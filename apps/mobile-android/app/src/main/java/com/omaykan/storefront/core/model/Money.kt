package com.omaykan.storefront.core.model

import java.text.NumberFormat
import java.util.Locale

/**
 * Peso formatting, in one place.
 *
 * Prices cross the wire as integer centavos and are only ever divided for
 * display — the register and the storefront read one price field, and rounding
 * a shared price differently on the phone is how price parity quietly breaks.
 */
object Money {
    private val format: NumberFormat = NumberFormat.getNumberInstance(Locale("en", "PH")).apply {
        minimumFractionDigits = 2
        maximumFractionDigits = 2
    }

    fun peso(cents: Long): String = "₱" + format.format(cents / 100.0)
}
