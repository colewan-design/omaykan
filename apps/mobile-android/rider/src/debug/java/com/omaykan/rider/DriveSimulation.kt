package com.omaykan.rider

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.mapbox.common.MapboxOptions
import com.mapbox.geojson.LineString
import com.mapbox.geojson.Point
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.MapView
import com.mapbox.maps.Style
import com.mapbox.maps.extension.style.layers.addLayer
import com.mapbox.maps.extension.style.layers.generated.lineLayer
import com.mapbox.maps.extension.style.layers.properties.generated.LineCap
import com.mapbox.maps.extension.style.layers.properties.generated.LineJoin
import com.mapbox.maps.extension.style.sources.addSource
import com.mapbox.maps.extension.style.sources.generated.geoJsonSource
import com.mapbox.maps.plugin.LocationPuck3D
import com.mapbox.maps.plugin.PuckBearing
import com.mapbox.maps.plugin.locationcomponent.LocationConsumer
import com.mapbox.maps.plugin.locationcomponent.LocationProvider
import com.mapbox.maps.plugin.locationcomponent.location
import com.omaykan.rider.core.designsystem.GhostButton
import com.omaykan.rider.core.designsystem.PrimaryButton
import com.omaykan.rider.core.designsystem.RiderCard
import com.omaykan.rider.core.designsystem.RiderTheme
import com.omaykan.rider.core.designsystem.SectionLabel
import com.omaykan.rider.core.designsystem.SegmentedPills
import com.omaykan.rider.core.map.MapPoint
import com.omaykan.rider.core.map.NavigationProgress
import com.omaykan.rider.core.map.NavigationRoute
import com.omaykan.rider.core.map.RouteRepository
import com.omaykan.rider.core.map.StaticMap
import com.omaykan.rider.core.map.decodePolyline
import com.omaykan.rider.feature.job.JobRouteMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import kotlin.math.abs
import kotlin.math.roundToInt

/** Loakan Proper, Baguio — the southern end, up by the airport. */
private val LOAKAN = MapPoint(16.3757, 120.6196)

/** La Trinidad, Benguet — the municipal hall, ~11 km north through the city. */
private val LA_TRINIDAD = MapPoint(16.4557, 120.5875)

/** How fast the pretend rider rides, before the multiplier. Baguio traffic. */
private const val BASE_KPH = 30.0

private val SPEEDS = listOf(1, 4, 12)

/**
 * A location provider driven by this screen rather than by a sensor.
 *
 * The same shape as the one behind [MapPreview], with the bearing coming off
 * the road instead of off a slider.
 */
private class DrivenLocation : LocationProvider {
    private val consumers = mutableListOf<LocationConsumer>()
    private var point: Point? = null
    private var bearing = 0.0

    override fun registerLocationConsumer(locationConsumer: LocationConsumer) {
        consumers += locationConsumer
        point?.let { locationConsumer.onLocationUpdated(it) }
        locationConsumer.onBearingUpdated(bearing)
    }

    override fun unRegisterLocationConsumer(locationConsumer: LocationConsumer) {
        consumers -= locationConsumer
    }

    /**
     * Move the rider, now.
     *
     * `duration = 0` on both, because this screen is already interpolating at
     * frame rate. The puck's own animator smooths a fix that arrives every few
     * seconds, which is right on a phone and wrong here — layered on top of a
     * per-frame position it reads as a scooter skating a fixed distance behind
     * where it actually is, most visibly on the inside of a tight bend.
     */
    fun drive(lat: Double, lng: Double, headingDeg: Double) {
        val next = Point.fromLngLat(lng, lat)
        point = next
        bearing = headingDeg
        consumers.forEach {
            it.onLocationUpdated(next) { duration = 0 }
            it.onBearingUpdated(headingDeg) { duration = 0 }
        }
    }
}

/** Where the drive has got to, recomputed every frame. */
private data class DriveState(
    val at: MapPoint,
    val headingDeg: Double,
)

/**
 * A rider driving a real road, on a real map, with nothing real behind it.
 *
 * Debug source set only. [MapPreview] answers "does the model turn" with a
 * slider — a stationary marker and a hand on a bearing. This answers the
 * question the slider cannot: does the model *read* as a scooter going
 * somewhere. A turn taken at speed exposes things a slider hides — a rotation
 * counted twice, a model that pivots about its tail instead of its centre, a
 * heading that takes the long way round through north, a puck that lags a
 * second behind the camera.
 *
 * The road is the real one. [RouteRepository] is the app's own Directions
 * caller, so what is driven here is exactly the geometry a job card draws and
 * the navigation banner counts down — a simulation against a hand-drawn path
 * would be a simulation of the wrong thing.
 *
 * ## North-up by default, and that is the point
 *
 * Shipping navigation follows: the camera turns and the scooter holds still
 * pointing up the screen. That is right for a rider and useless for looking at
 * a model, because a marker that never rotates on screen is a marker whose
 * rotation you cannot check. So this starts north-up — the map stays put, the
 * scooter swings through every bend — and the Follow pill switches to the
 * shipping behaviour to confirm the other half: in follow mode the model
 * should sit dead still up-screen. If it counter-rotates there, the heading is
 * being applied to both the puck and the camera, which is the mistake
 * JobRouteMap warns about.
 */
@Composable
fun DriveSimulation(pitch: Double = 55.0) {
    var route by remember { mutableStateOf<NavigationRoute?>(null) }
    var path by remember { mutableStateOf<List<MapPoint>>(emptyList()) }
    var failure by remember { mutableStateOf<String?>(null) }

    var running by remember { mutableStateOf(true) }
    var speedIndex by remember { mutableStateOf(0) }
    var follow by remember { mutableStateOf(false) }
    var travelled by remember { mutableStateOf(0.0) }
    var heading by remember { mutableStateOf(0.0) }
    var at by remember { mutableStateOf(LOAKAN) }

    // The loaded style, held rather than asked for: the road below is drawn
    // from `update`, which can run before the style has landed.
    var style by remember { mutableStateOf<Style?>(null) }
    var roadDrawn by remember { mutableStateOf(false) }

    val provider = remember { DrivenLocation() }

    // Fetched once. The repository caches on both endpoints anyway, so a
    // restart of the drive never costs a second metered request.
    LaunchedEffect(Unit) {
        if (!StaticMap.available) {
            failure = "No Mapbox token in this build. Rebuild with OMAYKAN_MAPBOX_TOKEN set."
            return@LaunchedEffect
        }

        val repository = RouteRepository(OkHttpClient(), Json { ignoreUnknownKeys = true })
        val fetched = withContext(Dispatchers.IO) { repository.route(LOAKAN, LA_TRINIDAD) }

        if (fetched == null) {
            failure = "Directions refused the route. Check the token's scopes and the network."
            return@LaunchedEffect
        }

        route = fetched
        path = decodePolyline(fetched.polyline)
        at = path.firstOrNull() ?: LOAKAN
    }

    val cumulative = remember(path) { cumulativeMetres(path) }
    val total = cumulative.lastOrNull() ?: 0.0

    // The drive itself: one frame, one step along the road.
    LaunchedEffect(path, running, speedIndex) {
        if (path.size < 2 || !running) return@LaunchedEffect

        val metresPerSecond = BASE_KPH * SPEEDS[speedIndex] / 3.6
        var last = withFrameNanos { it }

        while (true) {
            val now = withFrameNanos { it }
            val seconds = (now - last) / 1_000_000_000.0
            last = now

            val next = travelled + metresPerSecond * seconds

            if (next >= total) {
                travelled = total
                val state = sample(path, cumulative, total)
                at = state.at
                heading = state.headingDeg
                provider.drive(state.at.lat!!, state.at.lng!!, state.headingDeg)
                running = false
                break
            }

            travelled = next

            val state = sample(path, cumulative, next)
            at = state.at

            /*
             * Turned toward, not snapped to.
             *
             * A decoded polyline is a chain of straight segments, so the raw
             * bearing is a staircase: constant along a segment, then a jump at
             * the vertex. A real scooter leans into a bend over about a
             * second, and the staircase is what makes a model look like it is
             * teleporting between angles rather than turning.
             *
             * shortestTurn is what keeps a 350°→10° bend a 20° flick right
             * rather than a 340° spin left — the same function the shipping
             * heading uses, exercised here at every vertex of an eleven
             * kilometre road instead of once per GPS fix.
             */
            val delta = NavigationProgress.shortestTurn(heading, state.headingDeg)
            val turnRate = (TURN_DEGREES_PER_SECOND * SPEEDS[speedIndex] * seconds)
            heading = if (abs(delta) <= turnRate) {
                state.headingDeg
            } else {
                (heading + turnRate * (if (delta > 0) 1.0 else -1.0) + 360.0) % 360.0
            }

            provider.drive(state.at.lat!!, state.at.lng!!, heading)
        }
    }

    val step = remember(route, at) {
        NavigationProgress.currentStep(route?.steps.orEmpty(), at)
    }

    val lineColor = MaterialTheme.colorScheme.primary.toArgb()

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Box(Modifier.weight(1f)) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    MapboxOptions.accessToken = BuildConfig.MAPBOX_TOKEN

                    MapView(context).apply {
                        mapboxMap.loadStyle(Style.MAPBOX_STREETS) { loaded ->
                            // Pitch set after the style lands, not before: a
                            // style load resets it, and a flat overhead view is
                            // the one angle at which a 3D model is
                            // indistinguishable from a flat sprite.
                            mapboxMap.setCamera(
                                CameraOptions.Builder()
                                    .center(Point.fromLngLat(LOAKAN.lng!!, LOAKAN.lat!!))
                                    .zoom(ZOOM)
                                    .pitch(pitch)
                                    .build(),
                            )
                            style = loaded
                        }

                        location.setLocationProvider(provider)
                        location.updateSettings {
                            enabled = true
                            puckBearingEnabled = true
                            // HEADING, not COURSE: the provider above reports
                            // an angle, and COURSE would ignore it and derive
                            // its own from the last two positions — a second
                            // opinion about the same thing, a frame late.
                            puckBearing = PuckBearing.HEADING
                            locationPuck = LocationPuck3D(
                                // The dressed bike: rider, green uniform, top
                                // box. Generated from the bare `scooter.glb`
                                // by scripts/build-rider-model.py — edit the
                                // script, not the binary.
                                modelUri = "asset://scooter_rider.glb",
                                modelScale = listOf(45f, 45f, 45f),
                                // Nose points -Y in the model; 180° of yaw puts
                                // it up-screen at a bearing of zero.
                                modelRotation = listOf(0f, 0f, 180f),
                            )
                        }
                    }
                },
                update = { view ->
                    // The road, drawn once the geometry has arrived. Guarded,
                    // because `update` runs on every recomposition — which here
                    // is every frame — and adding the same layer twice throws.
                    val loaded = style
                    if (loaded != null && !roadDrawn && path.size > 1) {
                        roadDrawn = true
                        loaded.addSource(
                            geoJsonSource(ROUTE_SOURCE) {
                                geometry(
                                    LineString.fromLngLats(
                                        path.map { Point.fromLngLat(it.lng!!, it.lat!!) },
                                    ),
                                )
                            },
                        )
                        loaded.addLayer(
                            lineLayer(ROUTE_LAYER, ROUTE_SOURCE) {
                                lineColor(lineColor)
                                lineWidth(7.0)
                                lineOpacity(0.75)
                                lineCap(LineCap.ROUND)
                                lineJoin(LineJoin.ROUND)
                            },
                        )
                    }

                    if (at.placed) {
                        view.mapboxMap.setCamera(
                            CameraOptions.Builder()
                                .center(Point.fromLngLat(at.lng!!, at.lat!!))
                                .zoom(ZOOM)
                                .pitch(pitch)
                                // North-up unless following. In follow mode the
                                // camera takes the heading and the model should
                                // then look frozen: that is the check, not a bug.
                                .bearing(if (follow) heading else 0.0)
                                .build(),
                        )
                    }
                },
            )
        }

        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            val problem = failure
            if (problem != null) {
                SectionLabel("Loakan → La Trinidad")
                RiderCard(padding = 12.dp) {
                    Text(
                        text = problem,
                        style = MaterialTheme.typography.bodySmall,
                        color = RiderTheme.colors.danger,
                    )
                }
                return@Column
            }

            SectionLabel(
                "Loakan → La Trinidad · ${(travelled / 1000).format1()} of " +
                    "${(total / 1000).format1()} km · ${heading.roundToInt()}°",
            )

            RiderCard(padding = 12.dp) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = step?.let { "${it.distanceLabel} · ${it.step.instruction}" }
                            ?: if (path.isEmpty()) "Fetching the road…" else "Arrived.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = RiderTheme.colors.textSecondary,
                    )

                    SegmentedPills(
                        options = SPEEDS.map { "${it}×" },
                        selectedIndex = speedIndex,
                        onSelect = { speedIndex = it },
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val finished = total > 0 && travelled >= total

                        PrimaryButton(
                            text = when {
                                path.isEmpty() -> "Loading"
                                finished -> "Replay"
                                running -> "Pause"
                                else -> "Drive"
                            },
                            onClick = {
                                if (finished) {
                                    travelled = 0.0
                                    at = path.first()
                                    heading = sample(path, cumulative, 0.0).headingDeg
                                    running = true
                                } else {
                                    running = !running
                                }
                            },
                            enabled = path.isNotEmpty(),
                            modifier = Modifier.weight(1f),
                        )
                        GhostButton(
                            text = if (follow) "North-up" else "Follow",
                            icon = null,
                            onClick = { follow = !follow },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

/** Zoom close enough that the model is a scooter and not a dot. */
private const val ZOOM = 17.0

/** How fast the model is allowed to swing, per second of simulated time. */
private const val TURN_DEGREES_PER_SECOND = 160.0

private const val ROUTE_SOURCE = "sim-route"
private const val ROUTE_LAYER = "sim-route-line"

private fun Double.format1(): String = String.format(java.util.Locale.US, "%.1f", this)

/**
 * Running distance to each vertex, so a metre offset can be turned into a
 * position without walking the whole road every frame.
 */
private fun cumulativeMetres(path: List<MapPoint>): List<Double> {
    if (path.isEmpty()) return emptyList()

    val out = ArrayList<Double>(path.size)
    var running = 0.0
    out += 0.0

    for (i in 1 until path.size) {
        running += NavigationProgress.distanceMetres(path[i - 1], path[i])
        out += running
    }

    return out
}

/**
 * Where a rider [metres] along the road is, and which way the road points there.
 *
 * Linear interpolation between the two vertices it falls between. Straight-line
 * rather than great-circle on purpose: polyline vertices are tens of metres
 * apart and the difference between the two over that span is millimetres.
 */
private fun sample(
    path: List<MapPoint>,
    cumulative: List<Double>,
    metres: Double,
): DriveState {
    if (path.size < 2) {
        return DriveState(path.firstOrNull() ?: LOAKAN, 0.0)
    }

    val clamped = metres.coerceIn(0.0, cumulative.last())

    // The last vertex at or before the offset. binarySearch returns the
    // insertion point negated when there is no exact hit, which is the common case.
    val found = cumulative.binarySearch { it.compareTo(clamped) }
    val index = (if (found >= 0) found else -found - 2).coerceIn(0, path.size - 2)

    val from = path[index]
    val to = path[index + 1]
    val span = cumulative[index + 1] - cumulative[index]
    val fraction = if (span <= 0.0) 0.0 else (clamped - cumulative[index]) / span

    return DriveState(
        at = MapPoint(
            lat = from.lat!! + (to.lat!! - from.lat!!) * fraction,
            lng = from.lng!! + (to.lng!! - from.lng!!) * fraction,
        ),
        headingDeg = NavigationProgress.bearing(from, to),
    )
}

/**
 * The **shipping** navigation map, driven along the same road.
 *
 * [DriveSimulation] above renders a map of its own, which makes it a good place
 * to judge the model and a useless place to catch a bug in the screen a rider
 * actually opens. This renders [JobRouteMap] — the real one, unmodified — and
 * feeds it the position and heading a real delivery would, so the follow
 * camera, the route line, the two end pins and the puck can all be seen
 * working without a backend, a shop, an order and somebody riding to it.
 *
 * `adb shell am start -n com.omaykan.rider.debug/com.omaykan.rider.GalleryActivity --es screen nav`
 */
@Composable
fun NavigationPreview() {
    var route by remember { mutableStateOf<NavigationRoute?>(null) }
    var path by remember { mutableStateOf<List<MapPoint>>(emptyList()) }
    var at by remember { mutableStateOf(LOAKAN) }
    var heading by remember { mutableStateOf<Double?>(null) }

    LaunchedEffect(Unit) {
        if (!StaticMap.available) return@LaunchedEffect
        val repository = RouteRepository(OkHttpClient(), Json { ignoreUnknownKeys = true })
        val fetched = withContext(Dispatchers.IO) { repository.route(LOAKAN, LA_TRINIDAD) } ?: return@LaunchedEffect
        route = fetched
        path = decodePolyline(fetched.polyline)
        at = path.firstOrNull() ?: LOAKAN
    }

    val cumulative = remember(path) { cumulativeMetres(path) }
    val total = cumulative.lastOrNull() ?: 0.0

    // Eight times a walking pace, so a whole delivery is watchable. The screen
    // under test only ever sees a position and a heading, so the speed it
    // arrives at is not something it can tell.
    LaunchedEffect(path) {
        if (path.size < 2) return@LaunchedEffect
        var travelled = 0.0
        var last = withFrameNanos { it }

        while (travelled < total) {
            val now = withFrameNanos { it }
            travelled += (BASE_KPH * 8 / 3.6) * ((now - last) / 1_000_000_000.0)
            last = now

            val state = sample(path, cumulative, travelled.coerceAtMost(total))
            at = state.at

            val delta = NavigationProgress.shortestTurn(heading ?: state.headingDeg, state.headingDeg)
            val rate = TURN_DEGREES_PER_SECOND * 8 * ((now - last) / 1_000_000_000.0 + 0.016)
            heading = when {
                heading == null -> state.headingDeg
                abs(delta) <= rate -> state.headingDeg
                else -> (heading!! + rate * (if (delta > 0) 1.0 else -1.0) + 360.0) % 360.0
            }
        }
    }

    JobRouteMap(
        pickup = LOAKAN,
        dropoff = LA_TRINIDAD,
        here = at,
        headingDeg = heading,
        route = route?.polyline,
        modifier = Modifier.fillMaxSize(),
    )
}
