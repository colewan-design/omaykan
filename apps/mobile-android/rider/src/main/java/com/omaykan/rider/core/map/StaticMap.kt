package com.omaykan.rider.core.map

import com.omaykan.rider.BuildConfig
import java.util.Locale

/** A point worth drawing. Nullable halves, because that is how the API serves them. */
data class MapPoint(val lat: Double?, val lng: Double?) {
    val placed: Boolean get() = lat != null && lng != null && !(lat == 0.0 && lng == 0.0)
}

/**
 * A picture of the trip, as a URL.
 *
 * ## Why a static image and not a map SDK
 *
 * The third copy of this reasoning in the repo, and the same conclusion each
 * time. A map SDK adds several megabytes to an APK, needs a secret download
 * token wired into Gradle for every developer and every CI run, and draws a
 * pannable, zoomable, rotatable map — and a rider does not need one of those
 * here, because they are one tap from the navigation app they already use and
 * trust (see MapHandoff for why this app deliberately does not compete with
 * it). What a job card needs is a picture: does this trip go somewhere I want
 * to go.
 *
 * ## What the two cards draw, and why they differ
 *
 * A **claimed** job gets the whole thing — the shop, the door, and the driving
 * route between them. An **offer** on the board gets the shop alone, because
 * the server withholds the drop-off coordinates until a rider claims the job
 * (`RiderDeliveryController::asOffer`) so that the exact address of every
 * pending order is not readable by every rider on the platform. That omission
 * is the point, and drawing a line to an invented point would quietly undo it.
 *
 * ## What it costs
 *
 * One HTTP GET per distinct picture, plus at most one Directions call per
 * endpoint pair (cached in RouteRepository). Both are metered Mapbox requests,
 * so the card asks once and Coil's cache answers every repaint after.
 *
 * ## The token
 *
 * A public `pk.*` token, compiled in, set with OMAYKAN_MAPBOX_TOKEN at build
 * time. Blank means every card below falls back to text, which is exactly what
 * it showed before there was a map.
 */
object StaticMap {

    private const val ENDPOINT = "https://api.mapbox.com/styles/v1/mapbox/streets-v12/static"

    /** Blank when no token was compiled in, which is a supported build. */
    val available: Boolean get() = BuildConfig.MAPBOX_TOKEN.isNotBlank()

    /**
     * The image URL, or null when there is nothing worth drawing.
     *
     * Null rather than a map of an empty ocean: a shop whose owner never set
     * coordinates is normal, and the caller shows the address as text instead.
     *
     * @param route an encoded polyline from [RouteRepository], drawn under the
     *   pins. Null on the board, and on a claimed job whose route has not come
     *   back yet — the pins alone are still a useful picture, and waiting for
     *   the line before showing anything would leave a grey box on the card for
     *   as long as the Directions call takes.
     * @param token defaulted from the build, and a parameter only so the rules
     *   below can be tested. Unit tests see BuildConfig's empty fallback, and a
     *   function that always returned null under test would be a function
     *   nothing could check.
     */
    fun url(
        pickup: MapPoint?,
        dropoff: MapPoint? = null,
        rider: MapPoint? = null,
        route: String? = null,
        width: Int = 640,
        height: Int = 260,
        token: String = BuildConfig.MAPBOX_TOKEN,
    ): String? {
        if (token.isBlank()) return null

        val overlays = buildList {
            // The line first, so the pins sit on top of it where they meet.
            route?.takeIf { it.isNotBlank() }?.let { add(path(it)) }

            pickup?.takeIf { it.placed }?.let { add(pin("s-restaurant", "f59e0b", it)) }
            dropoff?.takeIf { it.placed }?.let { add(pin("s-home", "10b981", it)) }
            // Last, so a rider standing at the shop is drawn over its pin —
            // which is the moment they are most likely to be looking.
            rider?.takeIf { it.placed }?.let { add(pin("s-bicycle", "2563eb", it)) }
        }

        if (overlays.isEmpty()) return null

        val points = listOfNotNull(pickup, dropoff, rider).filter { it.placed }

        // `auto` frames everything without anybody computing a bounding box,
        // and it is the one piece of this that a hand-rolled version always
        // gets subtly wrong.
        //
        // Except with a single point and no line, where it has nothing to frame
        // and zooms to the maximum — a shop pin filling the card with one
        // street either side of it, which tells a rider nothing. That case gets
        // an explicit centre and a neighbourhood zoom instead.
        val solo = points.size == 1 && route == null

        val frame = if (solo) {
            val only = points.first()
            String.format(Locale.US, "%.5f,%.5f,%s", only.lng, only.lat, SOLO_ZOOM)
        } else {
            "auto"
        }

        // Padding is only legal alongside `auto` or an explicit bounding box.
        // Sent with a centre and a zoom, Mapbox answers 422 — which arrives as
        // a card with no picture on it and nothing anywhere saying why. Found
        // by running the thing; the URL is right in every other respect.
        val padding = if (solo) "" else "padding=40&"

        return "$ENDPOINT/${overlays.joinToString(",")}/$frame/${width}x$height@2x" +
            "?${padding}access_token=$token"
    }

    /** How far out a lone pin is drawn: a neighbourhood, not a doorstep. */
    private const val SOLO_ZOOM = "13.5"

    /**
     * `path-5+2563eb-0.9(<encoded polyline>)`.
     *
     * The polyline is URL-encoded because the format is full of backslashes and
     * other characters that would otherwise end the path segment early — the
     * one detail that makes this overlay fail silently rather than loudly.
     */
    private fun path(encodedPolyline: String): String {
        val escaped = java.net.URLEncoder.encode(encodedPolyline, "UTF-8")

        return "path-5+2563eb-0.9($escaped)"
    }

    /** `pin-s-bicycle+2563eb(lng,lat)`, in the shape Mapbox's overlay syntax wants. */
    private fun pin(symbol: String, colour: String, point: MapPoint): String =
        String.format(Locale.US, "pin-%s+%s(%.5f,%.5f)", symbol, colour, point.lng, point.lat)
}
