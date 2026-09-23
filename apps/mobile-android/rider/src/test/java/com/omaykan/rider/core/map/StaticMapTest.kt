package com.omaykan.rider.core.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The rules that decide what a job card's picture shows.
 *
 * Worth testing because every one of them fails *silently* in production: a
 * malformed overlay, a frame that zooms to a doorstep, or a pin drawn from a
 * shop that never set its coordinates all come back from Mapbox as an image.
 * A wrong picture on a card looks exactly like a right one until a rider goes
 * to the wrong place.
 */
class StaticMapTest {

    private val token = "pk.test"
    private val shop = MapPoint(16.4123, 120.5960)
    private val door = MapPoint(16.4145, 120.5931)

    @Test
    fun `no token means no map, not a broken one`() {
        assertNull(StaticMap.url(pickup = shop, token = ""))
    }

    @Test
    fun `a shop that never set coordinates draws nothing`() {
        assertNull(StaticMap.url(pickup = MapPoint(null, null), token = token))
    }

    /** An unset column reads as 0,0 once it has been through a Double cast. */
    @Test
    fun `the null island is not a location`() {
        assertFalse(MapPoint(0.0, 0.0).placed)
        assertNull(StaticMap.url(pickup = MapPoint(0.0, 0.0), token = token))
    }

    @Test
    fun `a lone pin gets a neighbourhood, not the maximum zoom auto would pick`() {
        val url = StaticMap.url(pickup = shop, token = token)!!

        assertTrue(url, url.contains("120.59600,16.41230,13.5"))
        assertFalse(url, url.contains("/auto/"))
    }

    @Test
    fun `two pins are framed automatically`() {
        val url = StaticMap.url(pickup = shop, dropoff = door, token = token)!!

        assertTrue(url, url.contains("/auto/"))
    }

    /**
     * Longitude first inside the parentheses. Mapbox's overlay syntax is the
     * opposite way round from every other coordinate in this codebase, which is
     * exactly the kind of thing that is wrong for a month before anyone notices
     * the pin is in the sea.
     */
    @Test
    fun `pins are written longitude first`() {
        val url = StaticMap.url(pickup = shop, token = token)!!

        assertTrue(url, url.contains("pin-s-restaurant+f59e0b(120.59600,16.41230)"))
    }

    @Test
    fun `the rider is drawn last so they sit on top of the shop they are standing at`() {
        val url = StaticMap.url(pickup = shop, dropoff = door, rider = shop, token = token)!!

        val pickup = url.indexOf("pin-s-restaurant")
        val dropoff = url.indexOf("pin-s-home")
        val rider = url.indexOf("pin-s-bicycle")

        assertTrue(url, pickup in 0 until dropoff)
        assertTrue(url, dropoff < rider)
    }

    /**
     * A polyline is full of backslashes and other characters that would end the
     * overlay segment early. Unescaped, the request comes back as a picture of
     * the wrong place rather than as an error.
     */
    @Test
    fun `the route is url-encoded into the path overlay`() {
        val polyline = "a~l~Fjk~uOwHJy@P"

        val url = StaticMap.url(pickup = shop, dropoff = door, route = polyline, token = token)!!

        assertTrue(url, url.contains("path-5+2563eb-0.9(a%7El%7EFjk%7EuOwHJy%40P)"))
        // The line is drawn before the pins, so a pin is never hidden under it.
        assertTrue(url, url.indexOf("path-") < url.indexOf("pin-"))
    }

    @Test
    fun `a route keeps auto framing even with one pin`() {
        val url = StaticMap.url(pickup = shop, route = "abc", token = token)!!

        assertTrue(url, url.contains("/auto/"))
    }

    /**
     * Mapbox answers 422 for `padding` sent with a centre and a zoom — it is
     * only legal alongside `auto` or an explicit bounding box. That arrives as
     * a card with no picture and nothing anywhere saying why, and the board's
     * card is exactly the single-pin case.
     */
    @Test
    fun `padding is sent only with auto framing`() {
        val solo = StaticMap.url(pickup = shop, token = token)!!
        val framed = StaticMap.url(pickup = shop, dropoff = door, token = token)!!

        assertFalse(solo, solo.contains("padding="))
        assertTrue(framed, framed.contains("padding=40"))
    }

    @Test
    fun `the token is the last thing on the query, not part of the path`() {
        val url = StaticMap.url(pickup = shop, token = token)!!

        assertEquals(1, url.split("access_token=").size - 1)
        assertTrue(url, url.endsWith("access_token=pk.test"))
    }
}
