package com.omaykan.rider.feature.job

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.viewinterop.AndroidView
import com.mapbox.common.MapboxOptions
import com.mapbox.geojson.Feature
import com.mapbox.geojson.FeatureCollection
import com.mapbox.geojson.LineString
import com.mapbox.geojson.Point
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.EdgeInsets
import com.mapbox.maps.MapView
import com.mapbox.maps.MapboxStyleManager
import com.mapbox.maps.Style
import com.mapbox.maps.extension.style.expressions.dsl.generated.match
import com.mapbox.maps.extension.style.layers.addLayer
import com.mapbox.maps.extension.style.layers.generated.circleLayer
import com.mapbox.maps.extension.style.layers.generated.lineLayer
import com.mapbox.maps.extension.style.layers.properties.generated.LineCap
import com.mapbox.maps.extension.style.layers.properties.generated.LineJoin
import com.mapbox.maps.extension.style.sources.addSource
import com.mapbox.maps.extension.style.sources.generated.GeoJsonSource
import com.mapbox.maps.extension.style.sources.generated.geoJsonSource
import com.mapbox.maps.extension.style.sources.getSourceAs
import com.mapbox.maps.plugin.LocationPuck3D
import com.mapbox.maps.plugin.PuckBearing
import com.mapbox.maps.plugin.animation.MapAnimationOptions
import com.mapbox.maps.plugin.animation.easeTo
import com.mapbox.maps.plugin.locationcomponent.LocationConsumer
import com.mapbox.maps.plugin.locationcomponent.LocationProvider
import com.mapbox.maps.plugin.locationcomponent.location
import com.omaykan.rider.BuildConfig
import com.omaykan.rider.core.designsystem.RiderTheme
import com.omaykan.rider.core.map.MapPoint
import com.omaykan.rider.core.map.decodePolyline

/**
 * The map behind the navigation screen — rendered on the device.
 *
 * ## What this replaced, and why it had to
 *
 * For most of this app's life the navigation screen showed the same picture the
 * job cards do: one Mapbox static image of the whole trip, framed to fit, with
 * a pin on it. That was honest and it was not navigation. A picture cannot
 * rotate under a rider, cannot tilt, and cannot follow — and it costs a metered
 * request every time the rider moves far enough to redraw, which is why the
 * view model used to hand this a *lagged* position (`mapHere`) rather than the
 * real one. All three of those are gone: a map rendered on the device redraws
 * for free, so it gets every fix, exactly.
 *
 * The cost is on the other side of the ledger and it is not small — see the
 * Maps SDK note in build.gradle.kts. A release APK went from 1.7 MB to roughly
 * 25 MB, and the secret downloads token became mandatory to build.
 *
 * ## The heading goes to the camera, and to the puck, and that is not twice
 *
 * This file used to carry a warning to whoever did this swap: bind the heading
 * to the vehicle **or** to the camera, never both, or the scooter spins against
 * the road. Having done it, the warning needs restating more precisely, because
 * the obvious reading of it is wrong.
 *
 * [PuckBearing.HEADING] rotates the model in *world* space, and the camera
 * bearing rotates the world. Setting both to the same angle is what makes the
 * scooter sit still, pointing up the screen, while the map turns underneath —
 * which is the thing that makes a map read as navigation. Double-counting is
 * what happens if the model is *also* given a per-frame yaw of its own on top
 * of the reported bearing; [MODEL_YAW] below is a fixed correction for how the
 * bike is modelled, not a heading, which is why it is a constant.
 *
 * ## What it does not do
 *
 * No gestures are disabled and none are added. Turn-by-turn with voice, offline
 * tiles and live traffic remain the rider's own navigation app's job — see
 * MapHandoff. This is the glanceable version: where I am, and which way the
 * road goes next.
 */
@Composable
fun JobRouteMap(
    pickup: MapPoint?,
    dropoff: MapPoint?,
    here: MapPoint?,
    headingDeg: Double?,
    route: String?,
    modifier: Modifier = Modifier,
) {
    // The same public token the static map on the cards uses. Blank is still a
    // supported build: no style will load, so rather than show a grey canvas
    // and a Mapbox error the screen keeps the plain panel it had before there
    // were maps, and the banner above still reads out every instruction.
    if (BuildConfig.MAPBOX_TOKEN.isBlank()) {
        Box(modifier.background(RiderTheme.colors.hairline))
        return
    }

    val dark = isSystemInDarkTheme()
    val puck = remember { RiderPuck() }
    val routeColour = RiderTheme.colors.accentPressed.toArgb()

    // Recreating the MapView on a theme change is deliberate and cheap here:
    // it happens when the phone flips to dark, not while riding.
    val styleUri = if (dark) Style.DARK else Style.MAPBOX_STREETS

    BoxWithConstraints(modifier.background(RiderTheme.colors.hairline)) {
        /*
         * Where the puck sits on screen: about seventy percent down, so most of
         * the map is the road ahead rather than the road already ridden.
         *
         * Done with camera padding rather than by aiming the camera at a point
         * in front of the rider, because padding is in screen space and so
         * survives a rotation, a split screen and a phone with a notch — all of
         * which change the viewport without changing where anybody is.
         *
         * Padding the **top** is what moves the puck down, which is the
         * opposite of the intuition and was wrong here first time round: the
         * inset shrinks the viewport from that edge, so the centre it is
         * aiming at moves *away* from the padded side.
         */
        val followPadding = with(LocalDensity.current) {
            EdgeInsets((maxHeight * TRAIL_FRACTION).toPx().toDouble(), 0.0, 0.0, 0.0)
        }

        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                MapboxOptions.accessToken = BuildConfig.MAPBOX_TOKEN

                MapView(context).apply {
                    mapboxMap.loadStyle(styleUri) { style ->
                        drawTrip(style, pickup, dropoff, route, routeColour)

                        // Pitch after the style, never before: a style load
                        // resets the camera, and an unpitched navigation map is
                        // the one angle at which a 3D model is
                        // indistinguishable from a flat sprite.
                        mapboxMap.setCamera(
                            CameraOptions.Builder()
                                .center(here?.toPoint() ?: pickup?.toPoint())
                                .zoom(FOLLOW_ZOOM)
                                .pitch(FOLLOW_PITCH)
                                .bearing(headingDeg ?: 0.0)
                                .padding(followPadding)
                                .build(),
                        )
                    }

                    // Settings before the provider, not after. Registering a
                    // consumer makes the provider push its first bearing
                    // immediately, and if puckBearing is still the default
                    // COURSE at that moment the angle is read under the wrong
                    // mode and dropped — so a rider opening this screen already
                    // moving would get a scooter facing north until the fix
                    // after next.
                    location.updateSettings {
                        enabled = true
                        puckBearingEnabled = true
                        // HEADING, not the default COURSE: COURSE derives an
                        // angle from movement between fixes and ignores the one
                        // the provider reports — a second, worse opinion about
                        // a question NavigationProgress has already answered
                        // from the platform's own sensor fusion.
                        puckBearing = PuckBearing.HEADING
                        locationPuck = LocationPuck3D(
                            // Rider, green uniform and top box, generated onto
                            // the bare bike by scripts/build-rider-model.py.
                            // The web map draws the same file.
                            modelUri = "asset://scooter_rider.glb",
                            // A real-size bike at z17 is a speck. Oversized the
                            // way every navigation puck is.
                            modelScale = listOf(28f, 28f, 28f),
                            modelRotation = MODEL_YAW,
                        )
                    }

                    location.setLocationProvider(puck)
                }
            },
            update = { view ->
                val style = view.mapboxMap.style ?: return@AndroidView

                drawTrip(style, pickup, dropoff, route, routeColour)

                val at = here ?: return@AndroidView
                if (!at.placed) return@AndroidView

                puck.moveTo(at, headingDeg)

                // Eased rather than set: fixes land every few seconds, and a
                // camera that jumps between them reads as the map stuttering
                // rather than the rider moving. The duration is deliberately
                // shorter than the gap between fixes so it has always settled
                // before the next one arrives.
                view.mapboxMap.easeTo(
                    CameraOptions.Builder()
                        .center(at.toPoint())
                        .zoom(FOLLOW_ZOOM)
                        .pitch(FOLLOW_PITCH)
                        .bearing(headingDeg ?: view.mapboxMap.cameraState.bearing)
                        .padding(followPadding)
                        .build(),
                    MapAnimationOptions.mapAnimationOptions { duration(1_200L) },
                )
            },
        )
    }
}

/**
 * How much of the map sits *behind* the rider.
 *
 * The rest is the road ahead. Expressed as a fraction of the view rather than a
 * dp figure so the framing is the same on a 5-inch phone and a tablet.
 */
private const val TRAIL_FRACTION = 0.42f

/** Close enough that the road has names on it, wide enough to see the next turn. */
private const val FOLLOW_ZOOM = 16.5

/** Tilted, because a flat overhead map is a picture and a tilted one is a road. */
private const val FOLLOW_PITCH = 55.0

/**
 * The bike's nose points -Y in the model, so it needs half a turn to point up
 * the screen at a bearing of zero.
 *
 * A property of how the model was built, not of where the rider is going. It is
 * a constant precisely so that nobody later reads it as part of the heading and
 * adds the two together.
 */
private val MODEL_YAW = listOf(0f, 0f, 180f)

private const val ROUTE_SOURCE = "job-route"
private const val ROUTE_LAYER = "job-route-line"
private const val ENDS_SOURCE = "job-ends"
private const val ENDS_LAYER = "job-ends-pins"

/**
 * The shop, the door, and the road between them.
 *
 * Idempotent: adds the sources and layers the first time, and afterwards only
 * pushes new data into them. `update` runs on every recomposition and adding a
 * layer that already exists throws, so this shape is load-bearing rather than
 * tidiness.
 */
private fun drawTrip(
    style: MapboxStyleManager,
    pickup: MapPoint?,
    dropoff: MapPoint?,
    route: String?,
    colour: Int,
) {
    val path = route?.let { decodePolyline(it) }.orEmpty().filter { it.placed }

    if (path.size > 1) {
        val line = LineString.fromLngLats(path.map { it.toPoint()!! })
        val existing = style.getSourceAs<GeoJsonSource>(ROUTE_SOURCE)

        if (existing == null) {
            style.addSource(geoJsonSource(ROUTE_SOURCE) { geometry(line) })
            style.addLayer(
                lineLayer(ROUTE_LAYER, ROUTE_SOURCE) {
                    lineColor(colour)
                    lineWidth(6.0)
                    lineOpacity(0.85)
                    lineCap(LineCap.ROUND)
                    lineJoin(LineJoin.ROUND)
                },
            )
        } else {
            // A re-route replaces the geometry. The view model redraws after
            // 250 m of drift, so this is rare but it is not never.
            existing.geometry(line)
        }
    }

    // Both ends in one source, told apart by a property, so the two pins are
    // one layer and cannot end up drawn in different orders on a restyle.
    val ends = listOfNotNull(
        pickup?.takeIf { it.placed }?.let { it to "pickup" },
        dropoff?.takeIf { it.placed }?.let { it to "dropoff" },
    ).map { (point, kind) ->
        Feature.fromGeometry(point.toPoint()).apply { addStringProperty("kind", kind) }
    }

    if (ends.isEmpty()) return

    val collection = FeatureCollection.fromFeatures(ends)
    val existing = style.getSourceAs<GeoJsonSource>(ENDS_SOURCE)

    if (existing != null) {
        existing.featureCollection(collection)
        return
    }

    style.addSource(geoJsonSource(ENDS_SOURCE) { featureCollection(collection) })
    style.addLayer(
        // Under the route line: the line ends *at* these points, and a pin
        // drawn over it hides which end of the road the rider is meant to be
        // going towards.
        circleLayer(ENDS_LAYER, ENDS_SOURCE) {
            circleRadius(7.0)
            circleStrokeWidth(2.5)
            circleStrokeColor("#ffffff")
            // The same two colours the static map uses for the same two things,
            // so a rider glancing from a card to this screen is not relearning
            // which blob is the shop.
            circleColor(
                match {
                    get { literal("kind") }
                    stop { literal("pickup"); literal("#f59e0b") }
                    stop { literal("dropoff"); literal("#10b981") }
                    literal("#6b7280")
                },
            )
        },
    )
}

private fun MapPoint.toPoint(): Point? =
    if (placed) Point.fromLngLat(lng!!, lat!!) else null

/**
 * The rider's own position, pushed into the map.
 *
 * The location component can read the platform's provider directly, and
 * deliberately does not: the position on this screen has already been through
 * [com.omaykan.rider.core.map.NavigationProgress], which decides what counts as
 * a heading when the phone reports none and drops fixes too inaccurate to draw.
 * Letting the map fetch its own would put a second, less careful answer on
 * screen next to the banner's.
 */
private class RiderPuck : LocationProvider {
    private val consumers = mutableListOf<LocationConsumer>()
    private var point: Point? = null
    private var bearing = 0.0

    override fun registerLocationConsumer(locationConsumer: LocationConsumer) {
        consumers += locationConsumer
        // Whatever we already knew, so a puck registered after the first fix
        // does not sit at null until the next one arrives.
        point?.let { locationConsumer.onLocationUpdated(it) }
        locationConsumer.onBearingUpdated(bearing)
    }

    override fun unRegisterLocationConsumer(locationConsumer: LocationConsumer) {
        consumers -= locationConsumer
    }

    fun moveTo(at: MapPoint, headingDeg: Double?) {
        val next = at.toPoint() ?: return
        point = next
        // Null heading means standing still, and the last angle is a better
        // answer than north — a scooter that swings to face up the screen every
        // time its rider stops at a light is worse than one that holds still.
        headingDeg?.let { bearing = it }

        consumers.forEach {
            it.onLocationUpdated(next)
            it.onBearingUpdated(bearing)
        }
    }
}
