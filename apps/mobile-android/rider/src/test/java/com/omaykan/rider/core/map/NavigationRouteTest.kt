package com.omaykan.rider.core.map

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * How a route is written down for somebody driving.
 *
 * The numbers themselves come from Mapbox and are not this app's to check. What
 * is this app's is the rounding, and rounding is where a navigation screen
 * either reads like an instrument or like a spreadsheet.
 */
class NavigationRouteTest {

    private fun step(metres: Double) = RouteStep(
        instruction = "Turn right onto Santa Cruz Ave",
        distanceMetres = metres,
        lat = 16.4123,
        lng = 120.5960,
        type = "turn",
        modifier = "right",
    )

    /**
     * A rider cannot act on "247 m", and a number that changes with every
     * stride is a number nobody reads. Tens, below a kilometre.
     */
    @Test
    fun `metres are rounded to something a person can act on`() {
        assertEquals("250 m", step(247.0).distanceLabel)
        assertEquals("250 m", step(252.4).distanceLabel)
        assertEquals("0 m", step(4.0).distanceLabel)
    }

    @Test
    fun `past a kilometre it switches unit rather than counting to four figures`() {
        assertEquals("1.2 km", step(1240.0).distanceLabel)
        assertEquals("1.0 km", step(1000.0).distanceLabel)
        assertEquals("990 m", step(994.0).distanceLabel)
    }

    /** The real figures from a Session Road → Sto. Tomas request. */
    @Test
    fun `distance and duration are converted, not reported raw`() {
        val route = NavigationRoute(
            polyline = "abc",
            distanceMetres = 4048.886,
            durationSeconds = 819.788,
            steps = emptyList(),
        )

        assertEquals(4.048886, route.distanceKm, 0.000001)
        assertEquals(14, route.durationMinutes)
    }

    /**
     * Arrival is computed from the clock at read time, not stored — a screen
     * left open for ten minutes must not still promise the time it loaded with.
     */
    @Test
    fun `arrival is the duration ahead of whatever now is`() {
        val route = NavigationRoute("abc", 4048.886, 900.0, emptyList())

        assertEquals(1_000_000L + 900_000L, route.arrivalAt(nowMillis = 1_000_000L))
    }
}
