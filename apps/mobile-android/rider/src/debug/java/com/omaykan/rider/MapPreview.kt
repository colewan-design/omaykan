package com.omaykan.rider

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.mapbox.common.MapboxOptions
import com.mapbox.geojson.Point
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.MapView
import com.mapbox.maps.Style
import com.mapbox.maps.plugin.LocationPuck3D
import com.mapbox.maps.plugin.PuckBearing
import com.mapbox.maps.plugin.locationcomponent.LocationConsumer
import com.mapbox.maps.plugin.locationcomponent.LocationProvider
import com.mapbox.maps.plugin.locationcomponent.location
import com.omaykan.rider.core.designsystem.RiderCard
import com.omaykan.rider.core.designsystem.RiderTheme
import com.omaykan.rider.core.designsystem.SectionLabel

/**
 * The 3D rider marker, on a real map, before any of it reaches a real screen.
 *
 * Debug source set only. Its whole job is to answer a question a static
 * picture cannot: what does the model actually look like sitting on Mapbox's
 * own lighting, at the size and pitch a rider would see it — and does the
 * heading rotate the way it should.
 *
 * The location is fixed and fake, and that is the point. A preview that waited
 * for a GPS fix would be a preview you could not take indoors, and the marker
 * is being judged on how it *looks*, not on where it is.
 */
private const val LAT = 16.4023
private const val LNG = 120.5960

/** A standing-still rider, with a bearing this screen controls by hand. */
private class FixedLocation : LocationProvider {
    private val consumers = mutableListOf<LocationConsumer>()
    private var bearing = 0.0

    override fun registerLocationConsumer(locationConsumer: LocationConsumer) {
        consumers += locationConsumer
        locationConsumer.onLocationUpdated(Point.fromLngLat(LNG, LAT))
        locationConsumer.onBearingUpdated(bearing)
    }

    override fun unRegisterLocationConsumer(locationConsumer: LocationConsumer) {
        consumers -= locationConsumer
    }

    /** Drag the slider, watch the model turn. The whole reason this exists. */
    fun setBearing(degrees: Double) {
        bearing = degrees
        consumers.forEach { it.onBearingUpdated(degrees) }
    }
}

@Composable
fun MapPreview(initialBearing: Float = 0f, pitch: Double = 55.0) {
    var bearing by remember { mutableFloatStateOf(initialBearing) }
    val provider = remember { FixedLocation().apply { setBearing(initialBearing.toDouble()) } }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Box(Modifier.weight(1f)) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    // v11 reads the public token from here rather than a
                    // resource. Same pk. token the static map already uses.
                    MapboxOptions.accessToken = BuildConfig.MAPBOX_TOKEN

                    MapView(context).apply {
                        mapboxMap.setCamera(
                            CameraOptions.Builder()
                                .center(Point.fromLngLat(LNG, LAT))
                                .zoom(18.0)
                                // Tilted, because a flat overhead view is the
                                // one angle at which a 3D model is
                                // indistinguishable from a flat sprite.
                                .pitch(55.0)
                                .build(),
                        )
                        // Camera set again after the style lands: a style
                        // load resets pitch, so setting it beforehand leaves a
                        // flat overhead view — the one angle at which a 3D
                        // model looks exactly like a flat sprite.
                        mapboxMap.loadStyle(Style.MAPBOX_STREETS) {
                            mapboxMap.setCamera(
                                CameraOptions.Builder()
                                    .center(Point.fromLngLat(LNG, LAT))
                                    .zoom(18.0)
                                    .pitch(55.0)
                                    .build(),
                            )
                        }

                        // Settings *before* the provider, and the order is a
                        // bug that was here for a while: registering a consumer
                        // makes the provider push its bearing immediately, and
                        // if puckBearing is still the default COURSE at that
                        // moment the angle is consumed under the wrong mode and
                        // thrown away. It showed up as `--ei bearing 90`
                        // launching a marker that stubbornly faced north, while
                        // dragging the slider afterwards worked fine.
                        location.updateSettings {
                            enabled = true
                            puckBearingEnabled = true
                            // COURSE is the default and it derives the angle
                            // from movement between fixes. This provider never
                            // moves, so the course is always zero and the model
                            // never turns. HEADING is what reads the bearing a
                            // provider reports — which is what a rider's
                            // compass and the Directions step both give us.
                            puckBearing = PuckBearing.HEADING
                            locationPuck = LocationPuck3D(
                                // Rider, uniform and top box merged onto the
                                // bare bike by scripts/build-rider-model.py.
                                modelUri = "asset://scooter_rider.glb",
                                // The model is a metre-scaled bike; on a map at
                                // z18 a real-size object is a speck, so it is
                                // deliberately oversized the way every
                                // navigation puck is.
                                modelScale = listOf(45f, 45f, 45f),
                                // The nose points -Y in the model. This is the
                                // yaw correction that puts it up-screen at a
                                // bearing of zero.
                                modelRotation = listOf(0f, 0f, 180f),
                            )
                        }

                        location.setLocationProvider(provider)
                    }
                },
            )
        }

        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SectionLabel("Heading ${bearing.toInt()}°")
            RiderCard(padding = 12.dp) {
                Slider(
                    value = bearing,
                    onValueChange = {
                        bearing = it
                        provider.setBearing(it.toDouble())
                    },
                    valueRange = 0f..360f,
                )
                Text(
                    text = "Drag to turn the marker. If the model spins against " +
                        "the road, the rotation is being applied twice.",
                    style = MaterialTheme.typography.bodySmall,
                    color = RiderTheme.colors.textSecondary,
                )
            }
        }
    }
}

/** Painted under the map while the style loads, so it does not flash white. */
@Composable
fun MapPreviewBackdrop() {
    Box(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .background(RiderTheme.colors.canopy),
    )
}
