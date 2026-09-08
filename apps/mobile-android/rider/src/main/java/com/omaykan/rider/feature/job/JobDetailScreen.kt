package com.omaykan.rider.feature.job

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Store
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.omaykan.rider.core.designsystem.ChipTone
import com.omaykan.rider.core.designsystem.IconBadge
import com.omaykan.rider.core.designsystem.MapSheet
import com.omaykan.rider.core.designsystem.PillShape
import com.omaykan.rider.core.designsystem.PrimaryButton
import com.omaykan.rider.core.designsystem.RiderTextStyles
import com.omaykan.rider.core.designsystem.RiderTheme
import com.omaykan.rider.core.designsystem.RoundActionButton
import com.omaykan.rider.core.designsystem.StatusChip
import com.omaykan.rider.core.map.MapPoint
import com.omaykan.rider.core.map.NavigationRoute
import com.omaykan.rider.core.model.DeliveryAssignment
import com.omaykan.rider.core.model.DeliveryStage
import com.omaykan.rider.core.model.Money
import com.omaykan.rider.core.nav.MapHandoff
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * A job, opened: the road to it, where the rider is on it, and what to do next.
 *
 * ## Three bands, in the order they are read at a junction
 *
 * The **instruction** over the top, the **map** filling everything behind, and
 * a **sheet** across the bottom holding the trip. Nothing floats loose over the
 * map any more: the arrival numbers used to be their own strip above the
 * buttons, which put two white rectangles between the road and the only control
 * that matters. One sheet, and the numbers live in the line they belong to.
 *
 * ## What the sheet says, top to bottom
 *
 * Who the job is with, and one tap to ring them. Then the two ends of the trip
 * — where the rider is, and where they are going *now*, which is the shop until
 * the food is in the bag and the door after. Then whether there is money to
 * collect, which is the fact that costs a rider real money to miss. Then the
 * one button that moves the job on.
 *
 * ## What it still refuses to do
 *
 * No voice, no re-routing, no off-route detection. "Navigate" hands the trip to
 * the app the rider drives with every day, which has all three plus offline
 * tiles for a city they know better than we do. See MapHandoff.
 */
@Composable
fun JobDetailScreen(
    jobId: String,
    onBack: () -> Unit,
    viewModel: JobDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(jobId) { viewModel.open(jobId) }

    // Delivered, released, or gone: there is no job behind this screen any
    // more, and a screen with nothing behind it should not be on screen.
    //
    // `everLoaded` is what keeps this from firing on the first frame, when the
    // job is null only because the feed has not answered yet.
    LaunchedEffect(state.job, state.everLoaded) {
        if (state.everLoaded && state.job == null) onBack()
    }

    BackHandler(onBack = onBack)

    val job = state.job

    Box(Modifier.fillMaxSize().background(RiderTheme.colors.hairline)) {
        JobRouteMap(
            pickup = job?.let { MapPoint(it.offer.pickup.lat, it.offer.pickup.lng) },
            dropoff = job?.let { MapPoint(it.deliveryLat, it.deliveryLng) },
            // The exact point, every fix. This used to be a lagged copy
            // because each distinct position was a metered image; the map is
            // rendered on the device now and redraws for free.
            here = state.here,
            headingDeg = state.headingDeg,
            route = state.route?.polyline,
            modifier = Modifier.fillMaxSize(),
        )

        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Header(onBack = onBack)

            state.step?.let { ManeuverBanner(it.distanceLabel, it.step.instruction) }

            Spacer(Modifier.weight(1f))

            job?.let {
                TripSheet(
                    job = it,
                    route = state.route,
                    here = state.here,
                    busy = state.busy,
                    error = state.error,
                    onAdvance = { next -> viewModel.advance(next) },
                    onRelease = viewModel::release,
                )
            }
        }
    }
}

/** Back, and nothing else. Every other control belongs to the job. */
@Composable
private fun Header(onBack: () -> Unit) {
    IconButton(
        onClick = onBack,
        modifier = Modifier
            .padding(start = 8.dp, top = 4.dp)
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surface),
    ) {
        Icon(
            Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = "Back to my jobs",
            tint = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/**
 * "250 m — Turn right onto Santa Cruz Ave", on black.
 *
 * Black rather than the app's green, and it is the only black surface in the
 * product: this is the one thing on screen a rider reads at speed, in sunlight,
 * out of the corner of an eye. It has to win against a map, and a card in the
 * app's own palette would sit *in* the map rather than over it.
 */
@Composable
private fun ManeuverBanner(distance: String, instruction: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .clip(MaterialTheme.shapes.large)
            .background(Color(0xFF0B0B0C))
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Filled.Navigation,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier
                .size(30.dp)
                // Mapbox writes the words; the arrow only has to not contradict
                // them. Rotating a north-pointing glyph to the right is the
                // cheapest honest version until there is an icon per manoeuvre.
                .rotate(45f),
        )

        Column(Modifier.padding(start = 16.dp)) {
            Text(
                text = distance,
                style = RiderTextStyles.statValue,
                color = Color.White,
            )
            Text(
                text = instruction,
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White.copy(alpha = 0.92f),
            )
        }
    }
}

/**
 * The whole job, on one sheet over the map.
 *
 * The party at the top and the button at the bottom are fixed; everything
 * between them is the trip. Which *end* of the trip is being described changes
 * with the stage, and only one is described at a time — a rider on the way to a
 * shop is not deciding anything about the customer's door, and a second address
 * in front of them at a junction is a second thing to read.
 */
@Composable
private fun TripSheet(
    job: DeliveryAssignment,
    route: NavigationRoute?,
    here: MapPoint?,
    busy: Boolean,
    error: String?,
    onAdvance: (DeliveryStage) -> Unit,
    onRelease: () -> Unit,
) {
    val context = LocalContext.current
    val goingToShop = job.stage == DeliveryStage.Assigned || job.stage == DeliveryStage.Pending

    val partyName = if (goingToShop) {
        job.offer.pickup.storeName
    } else {
        job.customerName ?: "The customer"
    }

    val destination = if (goingToShop) {
        job.offer.pickup.address ?: job.offer.pickup.storeName
    } else {
        job.deliveryAddress ?: job.offer.dropoffArea ?: "No address given"
    }

    // The shop's number is not on the wire — `asAssignment` sends the
    // customer's and nothing else — so on the way to a pickup there is nobody
    // to ring, and the button is absent rather than dead.
    val phone = job.customerPhone.takeUnless { goingToShop }

    MapSheet {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconBadge(
                icon = if (goingToShop) Icons.Filled.Store else Icons.Filled.Place,
                tint = MaterialTheme.colorScheme.primary,
                container = RiderTheme.colors.accentSoft,
                size = 46.dp,
            )

            Column(
                Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp),
            ) {
                Text(
                    text = if (goingToShop) "Picking up from" else "Delivering to",
                    style = MaterialTheme.typography.bodySmall,
                    color = RiderTheme.colors.textTertiary,
                )
                Text(
                    text = partyName,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            phone?.let {
                RoundActionButton(
                    icon = Icons.Filled.Call,
                    description = "Call $partyName",
                    onClick = { MapHandoff.dial(context, it) },
                )
            }
        }

        Divider()

        TripRow(
            leading = {
                Box(
                    Modifier
                        .size(12.dp)
                        .clip(PillShape)
                        .background(MaterialTheme.colorScheme.primary),
                )
            },
            label = "You",
            // Coordinates, not a reverse-geocoded street: this app never asks
            // anyone where the rider is, and a name for the spot would be a
            // request per fix for a line nobody reads twice. "Finding you" is
            // the honest answer while there is no fix, and there often is not.
            value = here?.takeIf { it.placed }?.let { point ->
                String.format(Locale.US, "%.4f, %.4f", point.lat, point.lng)
            } ?: "Finding you…",
        )

        TripRow(
            leading = {
                Icon(
                    imageVector = Icons.Filled.Place,
                    contentDescription = null,
                    tint = RiderTheme.colors.textSecondary,
                    modifier = Modifier.size(18.dp),
                )
            },
            label = if (goingToShop) "Pick up" else "Drop off",
            value = destination,
            modifier = Modifier.padding(top = 12.dp),
            trailing = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    route?.let {
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "${it.durationMinutes} min",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                // The clock time, because a shop asks "what
                                // time" and a duration answers "how long".
                                text = clockAt(it.arrivalAt()),
                                style = MaterialTheme.typography.bodySmall,
                                color = RiderTheme.colors.textTertiary,
                            )
                        }
                    }

                    RoundActionButton(
                        icon = Icons.Filled.MyLocation,
                        description = "Open in a map app",
                        onClick = {
                            if (goingToShop) {
                                MapHandoff.openMap(
                                    context,
                                    job.offer.pickup.lat,
                                    job.offer.pickup.lng,
                                    job.offer.pickup.address ?: job.offer.pickup.storeName,
                                )
                            } else {
                                MapHandoff.openMap(
                                    context,
                                    job.deliveryLat,
                                    job.deliveryLng,
                                    job.deliveryAddress,
                                )
                            }
                        },
                        size = 40.dp,
                        modifier = Modifier.padding(start = 12.dp),
                    )
                }
            },
        )

        Divider()

        // Said either way, and that is the point of it being here rather than
        // being an absence: a rider who sees nothing about cash cannot tell a
        // prepaid order from a screen that forgot to mention one, and the cost
        // of guessing wrong is an argument at somebody's door.
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = if (job.offer.collectsCash) {
                    Icons.Filled.Payments
                } else {
                    Icons.Filled.CheckCircle
                },
                contentDescription = null,
                tint = if (job.offer.collectsCash) {
                    RiderTheme.colors.cash
                } else {
                    RiderTheme.colors.success
                },
                modifier = Modifier.size(18.dp),
            )

            Text(
                text = "Payment",
                style = MaterialTheme.typography.bodyMedium,
                color = RiderTheme.colors.textSecondary,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 10.dp),
            )

            StatusChip(
                text = if (job.offer.collectsCash) {
                    "Collect ${Money.peso(job.offer.collectCents)}"
                } else {
                    "Paid online"
                },
                tone = if (job.offer.collectsCash) ChipTone.Cash else ChipTone.Success,
            )
        }

        error?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = RiderTheme.colors.danger,
                modifier = Modifier.padding(top = 12.dp),
            )
        }

        /*
         * Hand it back, or move it on.
         *
         * The pair is only ever a pair before pickup. Once the food is in the
         * bag there is nothing to refuse and the button disappears rather than
         * greying out — a disabled control at a junction is something to try
         * pressing, and this one would fail against the server anyway.
         *
         * Handing back is outlined in red rather than filled: it is the rarer
         * choice and the destructive one, and two filled buttons of equal
         * weight at the bottom of a sheet is how a thumb takes the wrong one.
         */
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (job.canRelease) {
                OutlinedButton(
                    onClick = onRelease,
                    enabled = !busy,
                    modifier = Modifier
                        .weight(1f)
                        .height(54.dp),
                    shape = PillShape,
                    border = BorderStroke(1.dp, RiderTheme.colors.danger),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = RiderTheme.colors.danger,
                    ),
                ) {
                    Text("Give it back", style = MaterialTheme.typography.titleMedium)
                }
            }

            job.stage.next?.let { next ->
                PrimaryButton(
                    text = job.stage.nextLabel.orEmpty(),
                    onClick = { onAdvance(next) },
                    busy = busy,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** One line of the trip: a marker, what it is, and where. */
@Composable
private fun TripRow(
    leading: @Composable () -> Unit,
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(24.dp), contentAlignment = Alignment.Center) { leading() }

        Column(
            Modifier
                .weight(1f)
                .padding(start = 8.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = RiderTheme.colors.textTertiary,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }

        trailing?.invoke()
    }
}

/** The hairline between the sheet's bands. */
@Composable
private fun Divider() {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 14.dp)
            .height(1.dp)
            .background(RiderTheme.colors.hairline),
    )
}

/** "13:05", in the phone's own locale and timezone. */
private fun clockAt(millis: Long): String =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(millis))
