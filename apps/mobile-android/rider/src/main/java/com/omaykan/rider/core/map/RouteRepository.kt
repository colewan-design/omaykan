package com.omaykan.rider.core.map

import com.omaykan.rider.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The road between the shop and the door: its shape, its length, how long it
 * takes, and what to do at each turn.
 *
 * A straight line between two points is a lie in a city built on a mountain: it
 * crosses ravines, and the distance it implies is not the distance anybody
 * rides. So the Directions API is asked for the real road — the same call the
 * web dashboard makes (`packages/core/src/maps/mapbox.ts`), with one difference
 * that matters here: `geometries=polyline` rather than geojson, because the
 * encoded form is exactly what the static image's `path-` overlay wants. No
 * decoding, no re-encoding, and no chance of the two disagreeing about
 * precision.
 *
 * ## Cached, and why that can be forever
 *
 * A route from this shop to this address does not change while a rider carries
 * it. The cache is keyed on both endpoints rounded to four decimals — about
 * eleven metres, finer than a doorway — and lives as long as the process. A
 * metered request per repaint is the easy mistake here.
 *
 * A failure caches `null` deliberately: a token without the Directions scope,
 * or a dead network, then costs one request rather than one per glance at the
 * card. The caller draws the pins without a line, which is honest — it is what
 * the board shows anyway.
 */
@Singleton
class RouteRepository @Inject constructor(
    /**
     * The app's own client, which is safe to reuse for a third-party call:
     * RiderAuthInterceptor is scoped to our API's host, so the rider's bearer
     * token is never attached to a request bound for Mapbox.
     */
    private val client: OkHttpClient,
    private val json: Json,
) {
    private val cache = mutableMapOf<String, NavigationRoute?>()
    private val lock = Mutex()

    /** Just the shape, for a card that only draws one. */
    suspend fun polyline(from: MapPoint, to: MapPoint): String? =
        route(from, to)?.polyline

    /**
     * The route, or null when there is none to be had.
     *
     * Null covers every unusable case together — no token, an unplaced end, a
     * refusal from Mapbox — because every caller does the same thing with all
     * of them.
     */
    suspend fun route(from: MapPoint, to: MapPoint): NavigationRoute? {
        if (!StaticMap.available || !from.placed || !to.placed) return null

        val key = keyFor(from, to)

        lock.withLock { if (cache.containsKey(key)) return cache[key] }

        val fetched = withContext(Dispatchers.IO) { fetch(from, to) }

        lock.withLock { cache[key] = fetched }

        return fetched
    }

    /** Blocking, so it is called on IO. One request; no retry — a picture is not worth one. */
    private fun fetch(from: MapPoint, to: MapPoint): NavigationRoute? = runCatching {
        val coords = String.format(
            Locale.US,
            "%.5f,%.5f;%.5f,%.5f",
            from.lng, from.lat, to.lng, to.lat,
        )

        // `steps` is what turns a shape into a set of instructions, and it
        // costs nothing extra: same request, same billing, four answers instead
        // of one. `banner_instructions` is deliberately *not* asked for — those
        // are the Navigation SDK's own richer format and nothing here renders
        // them.
        val url = "https://api.mapbox.com/directions/v5/mapbox/driving/$coords" +
            "?geometries=polyline&overview=full&steps=true" +
            "&access_token=${BuildConfig.MAPBOX_TOKEN}"

        client.newCall(Request.Builder().url(url).build()).execute().use { response ->
            if (!response.isSuccessful) return@runCatching null

            val body = response.body?.string() ?: return@runCatching null

            json.decodeFromString<DirectionsDto>(body).routes.firstOrNull()?.toModel()
        }
    }.getOrNull()

    /** Four decimals ≈ 11 m: finer than a doorway, coarse enough to hit twice. */
    private fun keyFor(from: MapPoint, to: MapPoint): String = String.format(
        Locale.US,
        "%.4f,%.4f>%.4f,%.4f",
        from.lat, from.lng, to.lat, to.lng,
    )
}

/**
 * What this app reads out of a Directions body, which is a fraction of it.
 *
 * `geometry` is a plain string only because the request asked for
 * `geometries=polyline`. With geojson it is an object and this would not
 * decode — the two go together and neither should be changed alone.
 */
@Serializable
private data class DirectionsDto(
    val routes: List<DirectionsRouteDto> = emptyList(),
)

@Serializable
private data class DirectionsRouteDto(
    val geometry: String = "",
    val distance: Double = 0.0,
    val duration: Double = 0.0,
    val legs: List<DirectionsLegDto> = emptyList(),
) {
    /**
     * Null for a route with no shape, which is the one field nothing here can
     * work without — a screen can survive missing instructions and cannot
     * survive a missing road.
     *
     * Legs are flattened. A leg is the span between two waypoints, and this app
     * only ever asks for two, so there is exactly one; flattening rather than
     * indexing means an added waypoint later reads as more steps instead of as
     * silently dropped ones.
     */
    fun toModel(): NavigationRoute? {
        if (geometry.isBlank()) return null

        return NavigationRoute(
            polyline = geometry,
            distanceMetres = distance,
            durationSeconds = duration,
            steps = legs.flatMap { leg -> leg.steps.mapNotNull { it.toModel() } },
        )
    }
}

@Serializable
private data class DirectionsLegDto(
    val steps: List<DirectionsStepDto> = emptyList(),
)

@Serializable
private data class DirectionsStepDto(
    val distance: Double = 0.0,
    val maneuver: DirectionsManeuverDto? = null,
) {
    fun toModel(): RouteStep? {
        val m = maneuver ?: return null
        val instruction = m.instruction?.takeIf { it.isNotBlank() } ?: return null

        // [lng, lat], the way GeoJSON orders a position and the opposite of
        // every other coordinate in this file.
        val lng = m.location.getOrNull(0) ?: return null
        val lat = m.location.getOrNull(1) ?: return null

        return RouteStep(
            instruction = instruction,
            distanceMetres = distance,
            lat = lat,
            lng = lng,
            type = m.type,
            modifier = m.modifier,
        )
    }
}

@Serializable
private data class DirectionsManeuverDto(
    /** Mapbox writes the sentence; this app never assembles one. */
    @SerialName("instruction")
    val instruction: String? = null,
    val location: List<Double> = emptyList(),
    val type: String? = null,
    val modifier: String? = null,
)
