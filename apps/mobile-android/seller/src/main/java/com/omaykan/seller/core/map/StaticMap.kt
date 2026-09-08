package com.omaykan.seller.core.map

import com.omaykan.seller.BuildConfig
import com.omaykan.seller.core.model.RiderPosition
import com.omaykan.seller.core.model.RoutePoint
import java.util.Locale

/**
 * A picture of where the rider is, as a URL.
 *
 * ## Why a static image and not a map SDK
 *
 * The Mapbox Android SDK is the obvious answer and it is the wrong one here.
 * It adds several megabytes to an APK that a merchant installs on a counter
 * phone, it needs a secret download token wired into Gradle for every developer
 * and every CI run, and it draws a pannable, zoomable, rotatable map — none of
 * which anybody wants while standing at a counter deciding whether to ring a
 * rider. The question a merchant is asking is "is he close", and the honest
 * shape of that answer is one glanceable picture.
 *
 * The web dashboard *does* get a real interactive map (packages/core
 * LiveDeliveryMap), because that screen is where somebody sits down to run the
 * day. This one is the phone in an apron pocket.
 *
 * ## What it costs
 *
 * One HTTP GET per distinct picture. That is a metered Mapbox request, so the
 * caller only asks while the sheet is actually open — see RiderSheet — and
 * Coil's cache means a rider who has not moved does not cost a second one. A
 * map that refreshed in the background for every delivery on the list would
 * spend a month's free tier in a week.
 *
 * ## The token
 *
 * A public `pk.*` token, compiled in. Public tokens are meant to ship in
 * clients, but a native app cannot be protected by the URL restrictions that
 * guard the web build — so the sensible arrangement is a *separate* token for
 * the mobile builds, scoped to styles and static images and nothing else, which
 * can be rotated without touching the website. Set it with OMAYKAN_MAPBOX_TOKEN
 * at build time; blank means every screen below falls back to text, which is
 * exactly what it showed before there was a map.
 */
object StaticMap {

    private const val ENDPOINT = "https://api.mapbox.com/styles/v1/mapbox/streets-v12/static"

    /** Blank when no token was compiled in, which is a supported build. */
    val available: Boolean get() = BuildConfig.MAPBOX_TOKEN.isNotBlank()

    /**
     * The image URL, or null when there is nothing worth drawing.
     *
     * Null rather than a map of an empty ocean: a shop that never set its
     * coordinates and an address the geocoder could not place are both normal,
     * and the caller shows the address as text instead.
     *
     * [width] and [height] are logical pixels; `@2x` is appended so the image
     * is sharp on the kind of phone this runs on. Mapbox caps a static image at
     * 1280 in each dimension, so both are clamped well inside that.
     */
    fun url(
        rider: RiderPosition?,
        pickup: RoutePoint?,
        dropoff: RoutePoint?,
        width: Int = 640,
        height: Int = 320,
    ): String? {
        if (!available) return null

        val markers = buildList {
            // Drawn in this order so the rider ends up on top of the two fixed
            // pins when they arrive at one of them, which is the moment a
            // merchant is most likely to be looking.
            pickup?.takeIf { it.placed }?.let { add(pin("s-restaurant", "f59e0b", it.lat!!, it.lng!!)) }
            dropoff?.takeIf { it.placed }?.let { add(pin("s-home", "10b981", it.lat!!, it.lng!!)) }
            rider?.let {
                // Grey once the fix has gone stale, so a stopped rider never
                // looks the same as a moving one in a still picture.
                add(pin("s-bicycle", if (it.stale) "6b7280" else "2563eb", it.lat, it.lng))
            }
        }

        if (markers.isEmpty()) return null

        val overlay = markers.joinToString(",")

        // `auto` frames every marker without anybody computing a bounding box,
        // and it is the one piece of this that a hand-rolled version always
        // gets subtly wrong.
        //
        // Except with a single marker, where it has nothing to frame and zooms
        // to the maximum — a shop pin filling the card with one street either
        // side of it, which tells a merchant nothing. That case gets an
        // explicit centre and a neighbourhood zoom instead.
        val frame = if (markers.size == 1) {
            val only = onlyPoint(rider, pickup, dropoff)
            String.format(Locale.US, "%.5f,%.5f,%s", only.second, only.first, SOLO_ZOOM)
        } else {
            "auto"
        }

        return "$ENDPOINT/$overlay/$frame/${width}x$height@2x" +
            "?padding=48&access_token=${BuildConfig.MAPBOX_TOKEN}"
    }

    /** How far out a lone pin is drawn: a neighbourhood, not a doorstep. */
    private const val SOLO_ZOOM = "13.5"

    /**
     * The one point that made it onto the map, as (lat, lng).
     *
     * Only called when exactly one marker was drawn, so the order below is the
     * same order [url] adds them in and exactly one of the three matches.
     */
    private fun onlyPoint(
        rider: RiderPosition?,
        pickup: RoutePoint?,
        dropoff: RoutePoint?,
    ): Pair<Double, Double> = when {
        rider != null -> rider.lat to rider.lng
        pickup?.placed == true -> pickup.lat!! to pickup.lng!!
        else -> dropoff!!.lat!! to dropoff.lng!!
    }

    /** `pin-s-bicycle+2563eb(lng,lat)`, in the shape Mapbox's overlay syntax wants. */
    private fun pin(symbol: String, colour: String, lat: Double, lng: Double): String =
        String.format(Locale.US, "pin-%s+%s(%.5f,%.5f)", symbol, colour, lng, lat)
}
