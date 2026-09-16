package com.omaykan.rider

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.omaykan.rider.core.data.WorkState
import com.omaykan.rider.core.designsystem.ForestTopBar
import com.omaykan.rider.core.designsystem.SegmentedPills
import com.omaykan.rider.core.location.SharingState
import com.omaykan.rider.core.map.MapPoint
import com.omaykan.rider.core.map.NavigationProgress
import com.omaykan.rider.core.map.NavigationRoute
import com.omaykan.rider.core.map.RouteRepository
import com.omaykan.rider.core.map.decodePolyline
import com.omaykan.rider.core.model.DeliveryAssignment
import com.omaykan.rider.core.model.DeliveryItem
import com.omaykan.rider.core.model.DeliveryOffer
import com.omaykan.rider.core.model.DeliveryStage
import com.omaykan.rider.core.model.Earnings
import com.omaykan.rider.core.model.EarningsTotal
import com.omaykan.rider.core.model.Pickup
import com.omaykan.rider.core.model.RatingSummary
import com.omaykan.rider.core.model.RiderProfile
import com.omaykan.rider.core.model.RiderStatus
import com.omaykan.rider.core.model.Vehicle
import com.omaykan.rider.core.model.VehicleType
import com.omaykan.rider.feature.home.HomeContent
import com.omaykan.rider.feature.home.HomeUiState
import com.omaykan.rider.feature.home.RiderTab
import com.omaykan.rider.feature.home.TabBar
import com.omaykan.rider.feature.job.JobDetailContent
import com.omaykan.rider.feature.job.JobDetailUiState
import com.omaykan.rider.feature.work.OfferCard
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

/*
 * A whole delivery, played through the shipping screens.
 *
 * Debug source set only. The board, the job's details, the map and the home
 * screen drawn here are the production composables — OfferCard,
 * JobDetailContent, HomeContent — fed the state a real delivery would feed
 * them, so what is watched is what a rider gets. What is simulated is only
 * what the app would otherwise wait on: the server accepting a tap, and a
 * phone moving along a road.
 *
 * The road is real: both legs are asked of RouteRepository, the app's own
 * Directions caller, and the rider is moved along the returned geometry one
 * position a second, the way a phone's GPS reports one. The turn banner, the
 * countdown and the "Arriving" step all come out of NavigationProgress, the
 * same code the job screen runs on a real fix.
 *
 * It plays itself. Any press of the screen's own button — Take this job,
 * I have picked up the order, Picked up, Delivered — skips to the next beat,
 * including the rest of a ride.
 *
 * `adb shell am start -n com.omaykan.rider.debug/com.omaykan.rider.GalleryActivity --es screen delivery`
 * `... --ei speed 12` to ride faster (default 6× a 30 km/h rider).
 */

/** Where the rider is when the job appears: a short ride from the shop. */
private val Start = MapPoint(16.4023, 120.5960)

/** Aling Nena's Kitchen. */
private val Shop = MapPoint(16.4119, 120.5960)

/** The customer's door in Sto. Tomas. */
private val Door = MapPoint(16.3950, 120.5900)

/** Baguio traffic, before the speed-up. */
private const val RIDE_KPH = 30.0

/** One position a second, the rate a phone's GPS reports at. */
private const val TICK_MS = 1_000L

/** How far ahead the scooter looks to decide which way it faces. */
private const val LOOK_AHEAD_M = 30.0

private val SimRider = RiderProfile(
    id = "sim",
    name = "Lakay Santos",
    email = "rider@example.com",
    phone = "09171234567",
    licenseNumber = "N01-23-456789",
    plateNumber = "NGE 7421",
    photoUrl = null,
    vehicle = Vehicle(
        type = VehicleType.Scooter,
        make = "Yamaha",
        model = "NMAX 155",
        color = "black",
        label = "black Yamaha NMAX 155",
        plateNumber = "NGE 7421",
    ),
    rating = RatingSummary(average = 4.9, count = 128),
    status = RiderStatus.Approved,
    reviewNote = null,
    reviewedAt = null,
    createdAt = null,
)

private val SimOffer = DeliveryOffer(
    id = "sim-2491",
    ticketNumber = "2491",
    placedAt = null,
    pickup = Pickup("s1", "Aling Nena's Kitchen", "12 Otek St, Baguio City", Shop.lat, Shop.lng),
    dropoffArea = "Sto. Tomas, Baguio City",
    distanceKm = 2.6,
    deliveryFeeCents = 6_500,
    itemCount = 4,
    paid = false,
    collectCents = 54_000,
)

private fun simJob(stage: DeliveryStage) = DeliveryAssignment(
    offer = SimOffer,
    stage = stage,
    acceptedAt = null,
    deliveryAddress = "88 Marcoville, Sto. Tomas, Baguio City",
    deliveryLat = Door.lat,
    deliveryLng = Door.lng,
    customerName = "Ana Villanueva",
    customerPhone = "09171234567",
    items = listOf(
        DeliveryItem("Pancit Canton", 2.0),
        DeliveryItem("Ube halaya, 250g jar", 1.0),
        DeliveryItem("Coke 1.5L", 1.0),
    ),
)

/** The beats of a delivery, in order. */
private enum class Act { Board, Accepted, ToShop, AtShop, ToDoor, AtDoor, Done }

@Composable
fun DeliveryRun(routes: RouteRepository, speed: Int, onExit: () -> Unit) {
    val context = LocalContext.current
    val say: (String) -> Unit = remember(context) {
        { text -> Toast.makeText(context, text, Toast.LENGTH_LONG).show() }
    }

    var act by remember { mutableStateOf(Act.Board) }
    var stage by remember { mutableStateOf(DeliveryStage.Assigned) }
    var busy by remember { mutableStateOf(false) }
    var showMap by remember { mutableStateOf(false) }
    var route by remember { mutableStateOf<NavigationRoute?>(null) }
    var path by remember { mutableStateOf<List<MapPoint>>(emptyList()) }
    var here by remember { mutableStateOf(Start) }
    var heading by remember { mutableStateOf<Double?>(null) }

    // A press of the screen's own button. Conflated: two presses in one beat
    // skip one beat, not two.
    val taps = remember { Channel<Unit>(Channel.CONFLATED) }

    LaunchedEffect(Unit) {
        /** Hold for [ms], or less if the rider presses the button. */
        suspend fun beat(ms: Long) {
            withTimeoutOrNull(ms) { taps.receive() }
        }

        /** The spinner a real press shows while the server answers. */
        suspend fun press() {
            busy = true
            delay(800)
            busy = false
        }

        /** Ask for the road, and stand the rider at the start of it facing along it. */
        suspend fun leg(from: MapPoint, to: MapPoint) {
            val found = routes.route(from, to)
            if (found == null) say("No route — check the Mapbox token. The rider will not move.")
            route = found
            path = found?.let { decodePolyline(it.polyline) }.orEmpty().filter { it.placed }
            here = path.firstOrNull() ?: from
            heading = aheadBearing(path, NavigationProgress.cumulativeMetres(path), 0.0) ?: heading
        }

        /** Ride the road, one fix a second, until the end of it or a press. */
        suspend fun ride() {
            if (path.size < 2) {
                beat(3_000)
                return
            }

            val cumulative = NavigationProgress.cumulativeMetres(path)
            val total = cumulative.last()
            val perTick = RIDE_KPH / 3.6 * speed * (TICK_MS / 1_000.0)
            var travelled = 0.0

            while (travelled < total) {
                travelled = if (taps.tryReceive().isSuccess) total else (travelled + perTick).coerceAtMost(total)
                here = pointAlong(path, cumulative, travelled)
                aheadBearing(path, cumulative, travelled)?.let { heading = it }
                delay(TICK_MS)
            }
        }

        // 1. A job on the board.
        say("Simulation: a delivery appears on the job board.")
        beat(3_500)
        press()

        // 2. Taken. The details open while the road to the shop is fetched.
        stage = DeliveryStage.Assigned
        act = Act.Accepted
        showMap = false
        say("Job taken. The details show the shop, the door and the basket.")
        leg(Start, Shop)
        beat(4_000)

        // 3. The ride to the shop.
        act = Act.ToShop
        showMap = true
        say("Riding to the shop. The banner reads out the next turn.")
        ride()

        // 4. At the counter.
        act = Act.AtShop
        showMap = false
        say("At the shop. Check the bag, then tap \"I have picked up the order\".")
        beat(5_000)
        press()
        stage = DeliveryStage.PickedUp

        // 5. The ride to the door.
        leg(Shop, Door)
        act = Act.ToDoor
        showMap = true
        say("Food in the bag. Riding to the customer — \"Arriving\" lights up near the door.")
        ride()

        // 6. At the door.
        act = Act.AtDoor
        say("At the door. Collect ₱540.00, then tap Delivered.")
        beat(5_000)
        press()
        stage = DeliveryStage.Delivered

        // 7. Done: the job leaves the rider's hands and the screen closes.
        act = Act.Done
        say("Delivered. The job closes, and the ₱65 fee is counted in today's earnings.")
    }

    val job = simJob(stage)

    when (act) {
        Act.Board -> BoardScene(busy = busy, onTake = { taps.trySend(Unit) })

        Act.Done -> DoneScene(delivered = job)

        else -> JobDetailContent(
            state = JobDetailUiState(
                job = job,
                route = route,
                here = here,
                headingDeg = heading,
                step = route?.let { NavigationProgress.currentStep(it.steps, here) },
                metresLeft = NavigationProgress.metresRemaining(path, here),
                busy = busy,
                everLoaded = true,
            ),
            showMap = showMap,
            onShowMap = { showMap = it },
            onBack = onExit,
            onSupport = { say("Support would ring the team's number here.") },
            onAdvance = { taps.trySend(Unit) },
            onRelease = { say("Handing a job back is switched off in the simulation.") },
        )
    }
}

/** The Jobs tab with the one offer on it. */
@Composable
private fun BoardScene(busy: Boolean, onTake: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Column(Modifier.weight(1f)) {
            ForestTopBar(title = "Jobs", subtitle = "1 on the board · ${SimRider.plateNumber}")

            SegmentedPills(
                options = listOf("Available", "My jobs (0)"),
                selectedIndex = 0,
                onSelect = {},
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp),
            )

            Column(
                Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
            ) {
                OfferCard(offer = SimOffer, busy = busy, enabled = !busy, onAccept = onTake, riderPoint = Start)
            }
        }

        TabBar(selected = RiderTab.Jobs, onSelect = {})
    }
}

/** Home, after: nothing in hand, one more delivery today, and the fee counted. */
@Composable
private fun DoneScene(delivered: DeliveryAssignment) {
    var watching by remember { mutableStateOf(true) }
    var sharing by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f)) {
            HomeContent(
                rider = SimRider,
                work = WorkState(completed = listOf(delivered), loading = false),
                state = HomeUiState(
                    earnings = Earnings.Empty.copy(today = EarningsTotal(jobs = 10, feeCents = 69_000)),
                    loading = false,
                    loaded = true,
                ),
                watchingJobs = watching,
                sharing = SharingState(sharing = sharing),
                locationGranted = true,
                onSetJobAlerts = { watching = it },
                onSetSharing = { sharing = it },
                onRefresh = {},
                onOpenJobs = {},
                onOpenEarnings = {},
                onOpenAccount = {},
                onOpenJob = {},
            )
        }
        TabBar(selected = RiderTab.Home, onSelect = {})
    }
}

/**
 * Where a rider [metres] along the road is.
 *
 * Linear between the two vertices it falls between: they are tens of metres
 * apart, and the difference from a great circle over that span is millimetres.
 */
private fun pointAlong(path: List<MapPoint>, cumulative: List<Double>, metres: Double): MapPoint {
    if (path.size < 2) return path.firstOrNull() ?: Start

    val clamped = metres.coerceIn(0.0, cumulative.last())
    val found = cumulative.binarySearch { it.compareTo(clamped) }
    val index = (if (found >= 0) found else -found - 2).coerceIn(0, path.size - 2)

    val from = path[index]
    val to = path[index + 1]
    val span = cumulative[index + 1] - cumulative[index]
    val fraction = if (span <= 0.0) 0.0 else (clamped - cumulative[index]) / span

    return MapPoint(
        lat = from.lat!! + (to.lat!! - from.lat!!) * fraction,
        lng = from.lng!! + (to.lng!! - from.lng!!) * fraction,
    )
}

/**
 * Which way the road runs from here, towards a point a little further along
 * it — so a short kink in the polyline does not twitch the scooter.
 */
private fun aheadBearing(path: List<MapPoint>, cumulative: List<Double>, metres: Double): Double? {
    if (path.size < 2) return null

    val total = cumulative.last()
    val from = pointAlong(path, cumulative, metres.coerceIn(0.0, (total - 1.0).coerceAtLeast(0.0)))
    val to = pointAlong(path, cumulative, (metres + LOOK_AHEAD_M).coerceAtMost(total))

    if (NavigationProgress.distanceMetres(from, to) < 1.0) return null
    return NavigationProgress.bearing(from, to)
}
