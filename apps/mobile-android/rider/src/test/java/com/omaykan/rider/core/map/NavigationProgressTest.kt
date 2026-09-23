package com.omaykan.rider.core.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The arithmetic behind a moving car.
 *
 * Every one of these is a bug you can only see by staring at a phone in a
 * moving vehicle, which is the worst possible place to debug: a car spinning
 * the long way round a corner, an instruction for a turn already taken, a
 * heading that twitches while the rider stands at a light.
 */
class NavigationProgressTest {

    private val shop = MapPoint(16.4123, 120.5960)
    private val door = MapPoint(16.3921, 120.6083)

    // -- heading -------------------------------------------------------------

    @Test
    fun `the platform's own heading wins when it has one`() {
        val heading = NavigationProgress.heading(reported = 142f, previous = shop, current = door)

        assertEquals(142.0, heading!!, 0.001)
    }

    @Test
    fun `without one it is derived from the last two fixes`() {
        val heading = NavigationProgress.heading(reported = null, previous = shop, current = door)

        // Shop to door is south and a little east.
        assertTrue("$heading", heading!! in 140.0..170.0)
    }

    /**
     * A phone at a red light produces fixes a metre or two apart in random
     * directions. Deriving an angle from those makes the car spin on the spot.
     */
    @Test
    fun `jitter does not count as movement`() {
        val nudged = MapPoint(shop.lat!! + 0.00001, shop.lng!! + 0.00001)

        assertNull(NavigationProgress.heading(reported = null, previous = shop, current = nudged))
    }

    @Test
    fun `no heading and no previous fix is no answer, not north`() {
        assertNull(NavigationProgress.heading(reported = null, previous = null, current = door))
    }

    // -- the shortest way round ----------------------------------------------

    /**
     * The single most noticeable bug in any rotating map: turning 350° the
     * wrong way because the road crossed north.
     */
    @Test
    fun `a turn through north goes the short way`() {
        assertEquals(20.0, NavigationProgress.shortestTurn(350.0, 10.0), 0.001)
        assertEquals(-20.0, NavigationProgress.shortestTurn(10.0, 350.0), 0.001)
    }

    @Test
    fun `an ordinary turn is just the difference`() {
        assertEquals(45.0, NavigationProgress.shortestTurn(90.0, 135.0), 0.001)
        assertEquals(-45.0, NavigationProgress.shortestTurn(135.0, 90.0), 0.001)
    }

    // -- which instruction ---------------------------------------------------

    private fun step(name: String, lat: Double, lng: Double, metres: Double = 300.0) = RouteStep(
        instruction = name,
        distanceMetres = metres,
        lat = lat,
        lng = lng,
        type = "turn",
        modifier = "right",
    )

    private val steps = listOf(
        step("Head south on Session Road", 16.4123, 120.5960),
        step("Turn left onto Harrison Road", 16.4050, 120.5992),
        step("You have arrived", 16.3921, 120.6083),
    )

    @Test
    fun `with no fix the first instruction is shown at its own length`() {
        val progress = NavigationProgress.currentStep(steps, at = null)!!

        assertEquals("Head south on Session Road", progress.step.instruction)
        assertEquals("300 m", progress.distanceLabel)
    }

    @Test
    fun `the nearest manoeuvre is the one being driven towards`() {
        val nearHarrison = MapPoint(16.4055, 120.5990)

        val progress = NavigationProgress.currentStep(steps, at = nearHarrison)!!

        assertEquals("Turn left onto Harrison Road", progress.step.instruction)
    }

    /**
     * Standing on the last manoeuvre, the honest banner is no banner. A screen
     * that still says "turn right in 0 m" after arriving is a screen a rider
     * stops believing.
     */
    @Test
    fun `arriving ends the instructions`() {
        assertNull(NavigationProgress.currentStep(steps, at = door))
    }

    @Test
    fun `an empty route has nothing to say`() {
        assertNull(NavigationProgress.currentStep(emptyList(), at = shop))
    }

    /** The distance in the banner counts down, and rounds the same way. */
    @Test
    fun `the banner counts down to the manoeuvre, not the step's own length`() {
        val approaching = MapPoint(16.4070, 120.5985)

        val progress = NavigationProgress.currentStep(steps, at = approaching)!!

        assertEquals("Turn left onto Harrison Road", progress.step.instruction)
        // Roughly 230 m out — well under the step's own declared 300 m.
        assertTrue(progress.distanceLabel, progress.metresToManeuver < 300.0)
        assertTrue(progress.distanceLabel, progress.distanceLabel.endsWith(" m"))
    }

    /**
     * The bug a real delivery showed: Mapbox's first step is `depart`, and its
     * manoeuvre is at the start of the route — so the instant a rider sets off,
     * the nearest manoeuvre is one about where they already are. The banner
     * read "0 m - Drive northwest on Harrison Road" for the whole first leg.
     */
    @Test
    fun `a manoeuvre you are standing on is behind you, not ahead`() {
        val departure = listOf(
            step("Drive northwest on Harrison Road", 16.4123, 120.5960).copy(type = "depart"),
            step("Turn left onto Hillside Road", 16.4050, 120.5992),
            step("You have arrived", 16.3921, 120.6083),
        )

        // Standing exactly on the departure point, as a rider does at pickup.
        val progress = NavigationProgress.currentStep(departure, at = MapPoint(16.4123, 120.5960))!!

        assertEquals("Turn left onto Hillside Road", progress.step.instruction)
        assertTrue(progress.distanceLabel, progress.metresToManeuver > 20.0)
    }

    /**
     * A turn just taken is the same case, and it is why the rule is about
     * distance rather than about the `depart` type specifically.
     */
    @Test
    fun `a turn just taken gives way to the next one`() {
        val onTheCorner = MapPoint(16.4050, 120.5992)

        val progress = NavigationProgress.currentStep(steps, at = onTheCorner)!!

        assertEquals("You have arrived", progress.step.instruction)
    }

    /** With nothing far enough ahead, a stale banner still beats a blank one. */
    @Test
    fun `standing among every manoeuvre falls back rather than going blank`() {
        val huddled = listOf(
            step("Turn right", 16.4123, 120.5960),
            step("Turn left", 16.41231, 120.59601),
        )

        assertNotNull(NavigationProgress.currentStep(huddled, at = MapPoint(16.4123, 120.5960)))
    }
    // -- what is left of the road ---------------------------------------------

    /** Three points up a meridian, about 1.1 km apart. */
    private val road = listOf(
        MapPoint(16.40, 120.60),
        MapPoint(16.41, 120.60),
        MapPoint(16.42, 120.60),
    )

    private val roadLength = NavigationProgress.cumulativeMetres(road).last()

    @Test
    fun `at the start the whole road is left`() {
        val left = NavigationProgress.metresRemaining(road, road.first())

        assertEquals(roadLength, left!!, 5.0)
    }

    @Test
    fun `halfway along, half of it is`() {
        val left = NavigationProgress.metresRemaining(road, MapPoint(16.41, 120.60))

        assertEquals(roadLength / 2, left!!, 5.0)
    }

    /** The case this exists for: the leg to the door must count down. */
    @Test
    fun `it shrinks as the rider moves along`() {
        val early = NavigationProgress.metresRemaining(road, MapPoint(16.403, 120.60))!!
        val later = NavigationProgress.metresRemaining(road, MapPoint(16.415, 120.60))!!

        assertTrue("$early then $later", later < early)
    }

    @Test
    fun `at the end nothing is left`() {
        val left = NavigationProgress.metresRemaining(road, road.last())

        assertEquals(0.0, left!!, 1.0)
    }

    @Test
    fun `no fix is no answer`() {
        assertNull(NavigationProgress.metresRemaining(road, null))
    }
}
