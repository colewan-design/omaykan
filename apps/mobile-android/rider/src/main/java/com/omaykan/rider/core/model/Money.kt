package com.omaykan.rider.core.model

import java.text.NumberFormat
import java.util.Locale

/**
 * Peso formatting, in one place.
 *
 * Money crosses the wire as integer centavos and is only ever divided for
 * display — the register, the storefront and this app read one field, and
 * rounding it differently on a phone is how a rider and a shop end up
 * disagreeing about what was collected at the door.
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
     * A fee on the board, where the digits matter more than the centavos.
     *
     * What a job pays is read at a glance while scrolling, so it drops the
     * decimals — "₱65" rather than "₱65.00". What a rider has to *collect*
     * never does: that number is counted out in notes and coins at a door, and
     * it is the one figure on the screen that has to be exact.
     */
    fun pesoRounded(cents: Long): String {
        val whole = NumberFormat.getIntegerInstance(PH)
        return "₱" + whole.format(cents / 100)
    }
}
