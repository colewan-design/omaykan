package com.omaykan.rider

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.omaykan.rider.core.data.WorkState
import com.omaykan.rider.core.designsystem.RiderTheme
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
import com.omaykan.rider.core.model.EarningsDay
import com.omaykan.rider.core.model.EarningsTotal
import com.omaykan.rider.core.model.Pickup
import com.omaykan.rider.core.model.RatingSummary
import com.omaykan.rider.core.model.RiderProfile
import com.omaykan.rider.core.model.RiderStatus
import com.omaykan.rider.core.model.Vehicle
import com.omaykan.rider.core.model.VehicleType
import com.omaykan.rider.feature.account.AccountScreen
import com.omaykan.rider.feature.earnings.EarningsContent
import com.omaykan.rider.feature.earnings.EarningsUiState
import com.omaykan.rider.feature.forgot.ForgotPasswordScreen
import com.omaykan.rider.feature.home.HomeContent
import com.omaykan.rider.feature.home.HomeUiState
import com.omaykan.rider.feature.home.RiderTab
import com.omaykan.rider.feature.home.TabBar
import com.omaykan.rider.feature.job.JobDetailContent
import com.omaykan.rider.feature.job.JobDetailUiState
import com.omaykan.rider.feature.onboarding.OnboardingScreen
import com.omaykan.rider.feature.register.RegisterScreen
import com.omaykan.rider.feature.signin.SignInScreen
import com.omaykan.rider.feature.work.AssignmentCard
import com.omaykan.rider.feature.work.CompletedRow
import com.omaykan.rider.feature.work.OfferCard
import com.omaykan.rider.feature.work.WorkContent
import com.omaykan.rider.feature.work.WorkTab
import com.omaykan.rider.feature.work.WorkUiState
import dagger.hilt.android.AndroidEntryPoint
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Every screen in the app, with made-up jobs, one per launch.
 *
 * Debug source set only, so it cannot ship. It exists because the board is the
 * screen that matters and it is empty unless a real shop has a real order out —
 * which makes "does an offer card look right" a question you otherwise cannot
 * ask without a backend, a shop and a customer.
 *
 * `adb shell am start -n com.omaykan.rider.debug/com.omaykan.rider.GalleryActivity`
 * `... GalleryActivity --es screen signin`
 *   (`register`, `forgot`, `onboarding`, `home`, `earnings`, `account`, `job`,
 *   `jobmap`, `finished`, `delivery`, `map`, `drive`, `nav`)
 *
 * `delivery` plays a whole job through the shipping screens — see DeliveryRun.
 * `--ei speed 6` sets how many times faster than a 30 km/h rider it rides.
 *
 * `job` is the delivery details of a job still at the shop, `jobmap` the same
 * screen's map face once it is picked up, and `finished` the details of one
 * already delivered.
 *
 * `home`, `earnings` and `job` render the production layouts (`HomeContent`,
 * `EarningsContent`, `JobDetailContent`) with fixture state, which is what lets
 * a redesign be checked without a signed-in session. `map` is the 3D marker
 * standing still with a bearing slider; `drive` is the same marker driven along
 * a real Directions route; `nav` renders the shipping `JobRouteMap` unmodified
 * and drives a position and a heading into it.
 */
@AndroidEntryPoint
class GalleryActivity : ComponentActivity() {

    /** The app's own Directions caller, so the job samples draw the real road. */
    @Inject lateinit var routes: RouteRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val cashOffer = DeliveryOffer(
            id = "1",
            ticketNumber = "1042",
            placedAt = null,
            pickup = Pickup("s1", "Aling Nena's Kitchen", "12 Otek St, Baguio City", 16.4119, 120.5960),
            dropoffArea = "Sto. Tomas, Baguio City",
            distanceKm = 2.4,
            deliveryFeeCents = 6500,
            itemCount = 3,
            paid = false,
            collectCents = 54000,
        )

        val prepaidOffer = cashOffer.copy(
            id = "2",
            ticketNumber = "1043",
            pickup = Pickup("s2", "Ganza Bakery", "Session Rd, Baguio City", 16.4125, 120.5985),
            dropoffArea = "Camp 7, Baguio City",
            distanceKm = 5.1,
            deliveryFeeCents = 9000,
            itemCount = 1,
            paid = true,
            collectCents = 0,
        )

        val marketOffer = cashOffer.copy(
            id = "3",
            ticketNumber = "1044",
            pickup = Pickup("s3", "Hilltop Market Stall 14", "Magsaysay Ave, Baguio City", 16.4150, 120.5968),
            dropoffArea = "Aurora Hill, Baguio City",
            distanceKm = 3.2,
            deliveryFeeCents = 7500,
            itemCount = 6,
            paid = false,
            collectCents = 128000,
        )

        fun job(stage: DeliveryStage) = DeliveryAssignment(
            offer = cashOffer,
            stage = stage,
            acceptedAt = null,
            deliveryAddress = "88 Marcoville, Sto. Tomas, Baguio City",
            deliveryLat = 16.3950,
            deliveryLng = 120.5900,
            customerName = "Ana Villanueva",
            customerPhone = "09171234567",
            items = listOf(
                DeliveryItem("Pancit Canton", 2.0),
                DeliveryItem("Ube halaya, 250g jar", 1.0),
                DeliveryItem("Coke 1.5L", 1.0),
            ),
        )

        val screen = intent.getStringExtra("screen")
        val bearing = intent.getIntExtra("bearing", 0).toFloat()
        val pitch = intent.getIntExtra("pitch", 55).toDouble()

        setContent {
            RiderTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    when (screen) {
                        "map" -> MapPreview(bearing, pitch)
                        "drive" -> DriveSimulation(pitch)
                        "nav" -> NavigationPreview()
                        "signin" -> SignInScreen(onRegister = {}, onForgotPassword = {})
                        "register" -> RegisterScreen(onBack = { finish() })
                        "forgot" -> ForgotPasswordScreen(onBack = { finish() })
                        // Writes the "seen" flag on Get started, like the real
                        // one. Debug build, debug preferences file — the point
                        // of looking at it is to press the buttons.
                        "onboarding" -> OnboardingScreen(onDone = {})
                        "account" -> AccountPreview()
                        "home" -> HomePreview(
                            routes = routes,
                            active = emptyList(),
                            board = listOf(cashOffer, prepaidOffer, marketOffer),
                            claim = { offer -> job(DeliveryStage.Assigned).copy(offer = offer) },
                        )
                        "earnings" -> EarningsPreview(job(DeliveryStage.Delivered))
                        // Opened on their own, there is nothing behind these to
                        // go back to, so Back closes the gallery.
                        "job" -> JobPreview(job(DeliveryStage.Assigned), map = false, routes = routes, onBack = { finish() })
                        "jobmap" -> JobPreview(job(DeliveryStage.PickedUp), map = true, routes = routes, onBack = { finish() })
                        "delivery" -> DeliveryRun(
                            routes = routes,
                            speed = intent.getIntExtra("speed", 6).coerceIn(1, 30),
                            onExit = { finish() },
                        )
                        "finished" -> JobPreview(
                            job(DeliveryStage.Delivered).copy(
                                offer = cashOffer.copy(ticketNumber = "2487", deliveryFeeCents = 15_000),
                                customerName = "Maria Santos",
                                deliveryAddress = "Lot 12, Pines View Subdivision, Camp 7, Baguio City",
                            ),
                            map = false,
                            routes = routes,
                            onBack = { finish() },
                        )
                        else -> Cards(cashOffer, prepaidOffer, ::job)
                    }
                }
            }
        }
    }
}

private val PreviewRider = RiderProfile(
    id = "r1",
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

/** A fortnight of made-up days ending today in Manila, as the server sends them. */
private fun previewEarnings(): Earnings {
    val today = LocalDate.now(ZoneId.of("Asia/Manila"))
    val fees = listOf(
        52_000L, 0L, 61_000L, 45_000L, 78_000L, 30_000L, 66_000L,
        58_000L, 71_000L, 0L, 64_000L, 49_000L, 83_000L, 62_500L,
    )
    val days = fees.mapIndexed { index, fee ->
        EarningsDay(
            date = today.minusDays((fees.lastIndex - index).toLong()).toString(),
            jobs = (fee / 7_000L).toInt(),
            feeCents = fee,
        )
    }

    return Earnings(
        today = EarningsTotal(jobs = 9, feeCents = 62_500),
        week = EarningsTotal(jobs = 28, feeCents = 432_000),
        month = EarningsTotal(jobs = 104, feeCents = 1_560_000),
        allTime = EarningsTotal(jobs = 612, feeCents = 9_750_000),
        days = days,
    )
}

/**
 * The home screen with made-up numbers, and the tab bar under it.
 *
 * `HomeContent` is the production layout; only its sources are fixtures. The
 * other tabs are reachable from the bar so navigation changes can be checked
 * here too.
 *
 * The Jobs tab's buttons act on local state the way WorkViewModel acts on the
 * feed — take, advance, hand back — after a short pause so the button's
 * spinner shows. Nothing reaches the server.
 */
@Composable
private fun HomePreview(
    routes: RouteRepository,
    active: List<DeliveryAssignment>,
    board: List<DeliveryOffer>,
    /** Turns a board offer into the job this rider now carries. */
    claim: (DeliveryOffer) -> DeliveryAssignment,
) {
    var tab by remember { mutableStateOf(RiderTab.Home) }
    var sharing by remember { mutableStateOf(false) }
    var watchingJobs by remember { mutableStateOf(false) }
    var openJob by remember { mutableStateOf<DeliveryAssignment?>(null) }
    var work by remember { mutableStateOf(WorkState(board = board, active = active, loading = false)) }
    var workUi by remember { mutableStateOf(WorkUiState()) }
    val scope = rememberCoroutineScope()

    // WorkViewModel.act: one write at a time, the pressed button spins.
    fun act(id: String, block: () -> Unit) {
        if (workUi.busyId != null) return
        workUi = workUi.copy(busyId = id, notice = null, error = null)
        scope.launch {
            delay(600)
            block()
            workUi = workUi.copy(busyId = null)
        }
    }

    // RiderShell's arrangement: an open job covers the tabs, opens on the map
    // once it is picked up, and Back returns to the tab it was opened from.
    openJob?.let { job ->
        JobPreview(job, map = job.stage == DeliveryStage.PickedUp, routes = routes, onBack = { openJob = null })
        return
    }

    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f)) {
            when (tab) {
                RiderTab.Home -> HomeContent(
                    rider = PreviewRider,
                    work = work,
                    state = HomeUiState(earnings = Earnings.Empty, loading = false, loaded = true),
                    watchingJobs = watchingJobs,
                    sharing = SharingState(sharing = sharing),
                    locationGranted = true,
                    onSetJobAlerts = { watchingJobs = it },
                    onSetSharing = { sharing = it },
                    onRefresh = {},
                    onOpenJobs = { tab = RiderTab.Jobs },
                    onOpenEarnings = { tab = RiderTab.Earnings },
                    onOpenAccount = { tab = RiderTab.Account },
                    onOpenJob = { id -> openJob = work.active.firstOrNull { it.id == id } },
                )

                RiderTab.Account -> AccountScreen(PreviewRider, onSignOut = {})
                RiderTab.Earnings -> EarningsContent(
                    state = EarningsUiState(earnings = previewEarnings(), loading = false, loaded = true),
                    finished = emptyList(),
                    onRefresh = {},
                )
                RiderTab.Jobs -> WorkContent(
                    rider = PreviewRider,
                    work = work,
                    ui = workUi,
                    riderPoint = RiderStart,
                    routeFor = { job ->
                        routes.polyline(
                            MapPoint(job.offer.pickup.lat, job.offer.pickup.lng),
                            MapPoint(job.deliveryLat, job.deliveryLng),
                        )
                    },
                    onTabChange = { workUi = workUi.copy(tab = it, notice = null, error = null) },
                    onRefresh = {},
                    onAccept = { id ->
                        act(id) {
                            val offer = work.board.first { it.id == id }
                            val job = claim(offer)
                            work = work.copy(board = work.board - offer, active = work.active + job)
                            workUi = workUi.copy(
                                tab = WorkTab.Mine,
                                notice = "${offer.pickup.storeName} is yours. Head over and pick it up.",
                            )
                        }
                    },
                    onAdvance = { job, stage ->
                        act(job.id) {
                            val moved = job.copy(stage = stage)
                            work = if (stage == DeliveryStage.Delivered) {
                                work.copy(active = work.active - job, completed = listOf(moved) + work.completed)
                            } else {
                                work.copy(active = work.active.map { if (it.id == job.id) moved else it })
                            }
                            if (stage == DeliveryStage.Delivered) {
                                workUi = workUi.copy(notice = "Delivered. Nice one.")
                            }
                        }
                    },
                    onRelease = { job ->
                        act(job.id) {
                            // The real board gets it back on the next poll.
                            work = work.copy(active = work.active - job, board = work.board + job.offer)
                            workUi = workUi.copy(tab = WorkTab.Board, notice = "Handed back. It is on the board again.")
                        }
                    },
                    onOpenJob = { id -> openJob = work.active.firstOrNull { it.id == id } },
                )
            }
        }
        TabBar(selected = tab, onSelect = { tab = it })
    }
}

@Composable
private fun EarningsPreview(template: DeliveryAssignment) {
    val today = LocalDate.now(ZoneId.of("Asia/Manila"))

    fun finished(ticket: String, name: String, daysAgo: Long, time: String, fee: Long) = template.copy(
        offer = template.offer.copy(id = "f$ticket", ticketNumber = ticket, deliveryFeeCents = fee),
        customerName = name,
        acceptedAt = "${today.minusDays(daysAgo)}T$time:00+08:00",
    )

    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f)) {
            EarningsContent(
                state = EarningsUiState(earnings = previewEarnings(), loading = false, loaded = true),
                finished = listOf(
                    finished("2487", "Maria Santos", 0, "10:24", 15_000),
                    finished("2486", "Ben Dizon", 0, "09:05", 6_500),
                    finished("2480", "Ana Villanueva", 1, "18:40", 9_000),
                    finished("2477", "Rosa Bautista", 1, "12:10", 7_500),
                ),
                onRefresh = {},
            )
        }
        TabBar(selected = RiderTab.Earnings, onSelect = {})
    }
}

/** Where the rider stands on the way to the shop: across the park from it. */
private val RiderStart = MapPoint(16.4023, 120.5960)

/**
 * The job screen's two faces with a fixture job and the real road.
 *
 * The route is not a fixture. It is asked of [RouteRepository] — the same
 * Directions call the job screen makes — between the same two ends the screen
 * would route: the rider to the shop before pickup, the shop to the door after.
 * So the map face shows the line, the turn banner and the ETA exactly as a
 * live job does, and the rider stands at the start of it facing along the
 * road. With no Mapbox token there is no route, which is what the app shows
 * then too.
 */
@Composable
private fun JobPreview(
    job: DeliveryAssignment,
    map: Boolean,
    routes: RouteRepository,
    onBack: () -> Unit,
) {
    var showMap by remember { mutableStateOf(map) }

    // The system gesture does what the arrow does, as it does in the app.
    BackHandler(onBack = onBack)

    val goingToShop = job.stage == DeliveryStage.Assigned || job.stage == DeliveryStage.Pending
    val shop = MapPoint(job.offer.pickup.lat, job.offer.pickup.lng)
    val door = MapPoint(job.deliveryLat, job.deliveryLng)
    val here = if (goingToShop) RiderStart else shop

    val route by produceState<NavigationRoute?>(null, job.id, job.stage) {
        value = routes.route(here, if (goingToShop) shop else door)
    }

    // Facing along the first stretch of road, as a rider setting off would be.
    val heading = route?.let { found ->
        val path = decodePolyline(found.polyline)
        val start = path.firstOrNull()
        val ahead = start?.let { s -> path.firstOrNull { NavigationProgress.distanceMetres(s, it) > 15.0 } }
        NavigationProgress.heading(reported = null, previous = start, current = ahead)
    }

    JobDetailContent(
        state = JobDetailUiState(
            job = job,
            route = route,
            here = here,
            headingDeg = heading,
            step = route?.let { NavigationProgress.currentStep(it.steps, here) },
            metresLeft = route?.let { NavigationProgress.metresRemaining(decodePolyline(it.polyline), here) },
            everLoaded = true,
        ),
        showMap = showMap,
        onShowMap = { showMap = it },
        onBack = onBack,
        onSupport = {},
        onAdvance = {},
        onRelease = {},
    )
}

@Composable
private fun Cards(
    cashOffer: DeliveryOffer,
    prepaidOffer: DeliveryOffer,
    job: (DeliveryStage) -> DeliveryAssignment,
) {
    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        SegmentedPills(listOf("Available", "My jobs (1)"), 0, {})
        OfferCard(cashOffer, busy = false, enabled = true, onAccept = {})
        OfferCard(prepaidOffer, busy = false, enabled = true, onAccept = {})
        AssignmentCard(job(DeliveryStage.Assigned), false, true, {}, {}, {})
        AssignmentCard(job(DeliveryStage.PickedUp), false, true, {}, {}, {})
        CompletedRow(job(DeliveryStage.Delivered))
    }
}

/** Exercises the production profile menu and panels with a debug-only profile. */
@Composable
private fun AccountPreview() {
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f)) {
            AccountScreen(rider = PreviewRider, onSignOut = {})
        }
        TabBar(selected = RiderTab.Account, onSelect = {})
    }
}
