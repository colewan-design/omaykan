package com.omaykan.rider.core.map

import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Where along a route somebody is, and which way they are pointing.
 *
 * Deliberately free of Android, of Compose and of any map SDK: this is the part
 * of navigation that is arithmetic, and arithmetic is the part worth testing.
 * Whatever ends up drawing the map calls into here; nothing here knows what a
 * map is.
 */
object NavigationProgress {

    private const val EARTH_RADIUS_M = 6_371_000.0

    /**
     * Which way the car should point, in degrees clockwise from north.
     *
     * [reported] is the platform's own heading and is used whenever it exists,
     * because it comes off the sensor fusion and is better than anything
     * derivable from two points. It is frequently null — a stationary phone has
     * no course, and some cheap chips never report one — so the fallback is the
     * bearing from the previous fix to this one.
     *
     * Null when there is neither: the caller keeps the last angle rather than
     * snapping the car to north, which is what a rider stopped at a light would
     * otherwise see.
     */
    fun heading(reported: Float?, previous: MapPoint?, current: MapPoint?): Double? {
        reported?.let { return it.toDouble() }

        if (previous == null || current == null) return null
        if (!previous.placed || !current.placed) return null

        // Two fixes a metre apart are noise, not a direction. Below this the
        // computed angle spins wildly and the car twitches on screen.
        if (distanceMetres(previous, current) < MIN_MOVE_FOR_HEADING_M) return null

        return bearing(previous, current)
    }

    /** Under this, a "movement" is GPS jitter and its angle is meaningless. */
    const val MIN_MOVE_FOR_HEADING_M = 8.0

    /**
     * The instruction to show, and how far until it.
     *
     * Chosen by nearness to the manoeuvre rather than by counting steps off as
     * they pass, because this app does not watch a rider continuously — it sees
     * whatever fix it has when the screen is open, which may be the third one
     * of the trip or the thirtieth. Picking the nearest manoeuvre that is still
     * ahead survives both.
     *
     * Returns null for an empty route, and for a rider who is past the last
     * manoeuvre — at which point the honest banner is no banner.
     */
    fun currentStep(steps: List<RouteStep>, at: MapPoint?): StepProgress? {
        if (steps.isEmpty()) return null

        if (at == null || !at.placed) {
            // No fix: the first instruction is the best guess, at its own full
            // length, which is what a screen opened before moving should say.
            val first = steps.first()
            return StepProgress(first, first.distanceMetres)
        }

        // Distance to each manoeuvre as the crow flies. Straight-line rather
        // than along-route: it is a metre-scale approximation over the last few
        // hundred metres of a step, and the banner rounds to tens anyway.
        val distances = steps.map { distanceMetres(at, MapPoint(it.lat, it.lng)) }

        val nearest = distances.indices.minByOrNull { distances[it] } ?: return null

        // Arrived, or as good as. The last manoeuvre is "you have arrived", and
        // standing on it is the end of the instructions.
        if (nearest == steps.lastIndex && distances[nearest] < ARRIVED_WITHIN_M) return null

        /*
         * A manoeuvre you are standing on is behind you, so the next one is the
         * one to show — and "next" means the next *along the route*, not the
         * next nearest.
         *
         * The distinction is the whole of this block. Mapbox's first step is
         * `depart`, sitting at the start of the route, so the instant a rider
         * sets off the closest instruction is about where they already are:
         * that is what put "0 m - Drive northwest on Harrison Road" on the
         * banner for a whole leg of a real delivery. Picking the nearest
         * manoeuvre more than a few metres away fixes that case and breaks a
         * commoner one — a rider on a corner is usually nearer to the turn they
         * have just taken than to the one ahead, and would be sent backwards.
         *
         * Steps arrive in route order, so stepping the index forward is both
         * the simplest answer and the only one that knows which way is on.
         */
        val index = if (distances[nearest] <= PASSED_WITHIN_M) {
            (nearest + 1).coerceAtMost(steps.lastIndex)
        } else {
            nearest
        }

        return StepProgress(steps[index], distances[index])
    }

    /** Close enough to the final manoeuvre that there is nothing left to say. */
    const val ARRIVED_WITHIN_M = 25.0

    /** Standing on a manoeuvre means it is behind you, not ahead of you. */
    const val PASSED_WITHIN_M = 20.0

    /** Great-circle metres. Good enough for a banner; not for billing. */
    fun distanceMetres(a: MapPoint, b: MapPoint): Double {
        val lat1 = Math.toRadians(a.lat!!)
        val lat2 = Math.toRadians(b.lat!!)
        val dLat = lat2 - lat1
        val dLng = Math.toRadians(b.lng!! - a.lng!!)

        val h = sin(dLat / 2).let { it * it } +
            cos(lat1) * cos(lat2) * sin(dLng / 2).let { it * it }

        return 2 * EARTH_RADIUS_M * asin(sqrt(h))
    }

    /** Initial bearing from [a] to [b], 0–360 clockwise from north. */
    fun bearing(a: MapPoint, b: MapPoint): Double {
        val lat1 = Math.toRadians(a.lat!!)
        val lat2 = Math.toRadians(b.lat!!)
        val dLng = Math.toRadians(b.lng!! - a.lng!!)

        val y = sin(dLng) * cos(lat2)
        val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLng)

        return (Math.toDegrees(atan2(y, x)) + 360.0) % 360.0
    }

    /**
     * The shortest way round from one angle to another, as a signed delta.
     *
     * What stops a car spinning 350° anticlockwise when the road turns 10°
     * clockwise through north — the single most noticeable bug in any rotating
     * map, and the reason this is a function rather than a subtraction.
     */
    fun shortestTurn(from: Double, to: Double): Double {
        val delta = ((to - from + 540.0) % 360.0) - 180.0

        return if (abs(delta) == 180.0) 180.0 else delta
    }
}

/** An instruction and the distance still to run before it. */
data class StepProgress(
    val step: RouteStep,
    val metresToManeuver: Double,
) {
    /** "250 m", the same rounding a step's own length uses. */
    val distanceLabel: String
        get() = RouteStep(
            instruction = step.instruction,
            distanceMetres = metresToManeuver,
            lat = step.lat,
            lng = step.lng,
            type = step.type,
            modifier = step.modifier,
        ).distanceLabel
}
