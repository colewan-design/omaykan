package com.omaykan.rider.core.map

import kotlin.math.roundToInt

/**
 * The road between two points, with enough of it to navigate by.
 *
 * The card only ever needed [polyline] — a shape to draw. A navigation screen
 * needs the same request's other three answers: how long it takes, how far it
 * is, and what to do next. Mapbox returns all four from one call, so asking for
 * less and asking again later would be two metered requests for one road.
 */
data class NavigationRoute(
    /** Encoded polyline, precision 5. The same string the static map draws. */
    val polyline: String,
    val distanceMetres: Double,
    val durationSeconds: Double,
    /** In order. Empty when the request did not ask for steps. */
    val steps: List<RouteStep>,
) {
    val distanceKm: Double get() = distanceMetres / 1000.0

    val durationMinutes: Int get() = (durationSeconds / 60.0).roundToInt()

    /**
     * The clock time this route arrives at, given a start of now.
     *
     * A duration answers "how long"; a rider is usually asked "what time".
     * Computed at read time rather than stored, so a screen left open for ten
     * minutes does not show an arrival that was true when it loaded.
     */
    fun arrivalAt(nowMillis: Long = System.currentTimeMillis()): Long =
        nowMillis + (durationSeconds * 1000L).toLong()
}

/**
 * One instruction: "Turn right onto Santa Cruz Ave", and how far until it.
 *
 * [distanceMetres] is the length of *this* step — the distance from where the
 * step begins to where the manoeuvre happens — which is what the banner counts
 * down. Mapbox writes the sentence itself, in the language the request asked
 * for, and it is deliberately not reassembled here from `type` and `modifier`:
 * a hand-built sentence is a worse sentence in every language and a broken one
 * in most.
 */
data class RouteStep(
    val instruction: String,
    val distanceMetres: Double,
    /** Where the manoeuvre happens. Used to decide which step is current. */
    val lat: Double,
    val lng: Double,
    /** "turn", "roundabout", "arrive" … — picks the arrow, not the words. */
    val type: String?,
    /** "left", "right", "straight" … — rotates the arrow. */
    val modifier: String?,
) {
    /**
     * "250 m", or "1.2 km" past a kilometre.
     *
     * Metres to the nearest ten below a kilometre: a rider cannot act on
     * "247 m", and a number that changes every stride is a number nobody reads.
     */
    val distanceLabel: String
        get() = when {
            distanceMetres >= 1000 -> String.format("%.1f km", distanceMetres / 1000.0)
            else -> "${((distanceMetres / 10).roundToInt() * 10)} m"
        }
}
