package com.omaykan.rider.feature.work

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.omaykan.rider.core.designsystem.RiderTheme
import com.omaykan.rider.core.map.MapPoint
import com.omaykan.rider.core.map.StaticMap

/**
 * The trip, as a picture, across the top of a job card.
 *
 * ## Why it earns the space
 *
 * A rider reads a board in seconds, and "Sto. Tomas Proper, Baguio City" is a
 * string they have to translate into a direction. A picture is the direction.
 * It answers the question the text cannot — *is this on my way, or is it up the
 * mountain* — before any of the words below it are read.
 *
 * ## What it draws, and what it refuses to
 *
 * Whatever is genuinely known, and nothing else. On the board that is the shop,
 * plus the rider themselves when they are already sharing a location; the
 * drop-off is a named area and not a coordinate until the job is claimed, and
 * inventing a pin for it would draw a precise answer to a question the server
 * deliberately left vague. On a claimed job it is the shop, the door, and the
 * driving route between them.
 *
 * ## When it is not there at all
 *
 * A build with no Mapbox token, a shop that never set its coordinates, no
 * network. Every one of those returns null from [StaticMap.url] and this
 * composable draws nothing — not a grey box, not an error. The card below is
 * the card that shipped before there was a map, and it is complete on its own.
 */
@Composable
fun JobMap(
    pickup: MapPoint?,
    modifier: Modifier = Modifier,
    dropoff: MapPoint? = null,
    rider: MapPoint? = null,
    route: String? = null,
    height: Dp = 132.dp,
) {
    val url = StaticMap.url(
        pickup = pickup,
        dropoff = dropoff,
        rider = rider,
        route = route,
    ) ?: return

    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            // Behind the image while it loads, so the card does not flash white
            // and settle — the tiles arrive over a shop's wifi, not instantly.
            .background(RiderTheme.colors.hairline),
    ) {
        AsyncImage(
            model = url,
            // Null, not a description of the route: the same trip is written
            // out in full in the rail directly below, and a screen reader
            // announcing it twice is worse than announcing it once.
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxWidth().height(height),
        )
    }
}
