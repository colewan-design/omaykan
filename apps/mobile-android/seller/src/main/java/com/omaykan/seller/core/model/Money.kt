package com.omaykan.seller.core.model

import java.text.NumberFormat
import java.util.Locale

/**
 * Peso formatting, in one place.
 *
 * Prices cross the wire as integer centavos and are only ever divided for
 * display — the register, the storefront and this app read one price field, and
 * rounding a shared price differently on the phone is how price parity quietly
 * breaks.
 */
object Money {
    // forLanguageTag rather than the two-argument Locale constructor, which is
    // deprecated on current JDKs. Same locale, no warning.
    private val PH: Locale = Locale.forLanguageTag("en-PH")

    private val format: NumberFormat = NumberFormat.getNumberInstance(PH).apply {
        minimumFractionDigits = 2
        maximumFractionDigits = 2
    }

    fun peso(cents: Long): String = "₱" + format.format(cents / 100.0)

    /**
     * The takings strip, where the digits matter more than the centavos.
     *
     * A day's total is read at a glance and never typed into anything, so it
     * drops the decimals — "₱12,480" rather than "₱12,480.00". Anything a
     * merchant might reconcile against, an order total included, keeps them.
     */
    fun pesoRounded(cents: Long): String {
        val whole = NumberFormat.getIntegerInstance(PH)
        return "₱" + whole.format(cents / 100)
    }
}
