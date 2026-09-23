package com.omaykan.storefront.core.map

import com.omaykan.storefront.BuildConfig
import com.omaykan.storefront.core.model.RiderPosition
import com.omaykan.storefront.core.model.RoutePoint
import java.util.Locale

/**
 * A picture of where the order is, as a URL.
 *
 * The customer's half of the same argument :seller's StaticMap makes: the
 * Mapbox Android SDK adds several megabytes and a secret download token to a
 * build, and draws a pannable, rotatable map that nobody standing in their
 * kitchen wants. The question being asked here is "how close is my food", and
 * the honest shape of that answer is one glanceable picture with a sentence
 * under it.
 *
 * The web tracking page does get a real interactive map (packages/core
 * LiveDeliveryMap) — that one is a page somebody can leave open on a laptop.
 *
 * This is the third copy of this idea in this repo, after :seller's and the
 * web's. Two of them are near-identical and that is the signal the rider
 * README already names about `core:network` and the theme: at three copies, a
 * shared module stops being a guess. The map is on that list.
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
     * and the caller shows the delivery stage in words instead — which is what
     * this screen showed before there was a map at all.
     */
    fun url(
        rider: RiderPosition?,
        pickup: RoutePoint?,
        dropoff: RoutePoint?,
        width: Int = 640,
        height: Int = 360,
    ): String? {
        if (!available) return null

        val markers = buildList {
            // Drawn in this order so the rider ends up on top of the two fixed
            // pins when they reach one — which is the moment somebody waiting
            // is most likely to be looking at this.
            pickup?.takeIf { it.placed }?.let { add(pin("s-restaurant", "f59e0b", it.lat!!, it.lng!!)) }
            dropoff?.takeIf { it.placed }?.let { add(pin("s-home", "10b981", it.lat!!, it.lng!!)) }
            rider?.let {
                // Grey once the fix has gone stale, so a rider whose phone has
                // dropped off the network never looks the same as a moving one.
                add(pin("s-bicycle", if (it.stale) "6b7280" else "2563eb", it.lat, it.lng))
            }
        }

        if (markers.isEmpty()) return null

        // `auto` frames every marker without anybody computing a bounding box,
        // and it is the piece a hand-rolled version always gets subtly wrong.
        //
        // Except with a single marker, where it has nothing to frame and zooms
        // to the maximum. The confirmation screen hits that case every time —
        // an order with no rider yet and no geocoded address has only the shop
        // pin — and a card filled by one street is worse than a neighbourhood.
        val frame = if (markers.size == 1) {
            val only = onlyPoint(rider, pickup, dropoff)
            String.format(Locale.US, "%.5f,%.5f,%s", only.second, only.first, SOLO_ZOOM)
        } else {
            "auto"
        }

        return "$ENDPOINT/${markers.joinToString(",")}/$frame/${width}x$height@2x" +
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
