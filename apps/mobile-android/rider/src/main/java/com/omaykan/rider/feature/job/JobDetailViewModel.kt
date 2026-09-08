package com.omaykan.rider.feature.job

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omaykan.rider.core.data.DeliveryRepository
import com.omaykan.rider.core.data.WorkFeed
import com.omaykan.rider.core.location.LocationSource
import com.omaykan.rider.core.map.MapPoint
import com.omaykan.rider.core.map.NavigationProgress
import com.omaykan.rider.core.map.NavigationRoute
import com.omaykan.rider.core.map.RouteRepository
import com.omaykan.rider.core.map.StepProgress
import com.omaykan.rider.core.model.DeliveryAssignment
import com.omaykan.rider.core.model.DeliveryStage
import com.omaykan.rider.core.network.ApiException
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * One job, opened.
 *
 * @property job null while the feed has not caught up, and again once the job
 *   leaves the rider's list — releasing it, or delivering it. The screen closes
 *   itself on the second, which is why this is nullable rather than a snapshot
 *   taken when the screen opened.
 */
data class JobDetailUiState(
    val job: DeliveryAssignment? = null,
    val route: NavigationRoute? = null,
    /** Where the rider is, from the live stream rather than a cached fix. */
    val here: MapPoint? = null,
    /** Degrees clockwise from north. Kept from the last good one when standing still. */
    val headingDeg: Double? = null,
    val step: StepProgress? = null,
    val busy: Boolean = false,
    val error: String? = null,
    /**
     * Whether this job has ever been seen. Distinguishes "the feed has not
     * answered yet" from "the job is finished and gone", which look identical
     * from a null and mean opposite things to a screen deciding whether to
     * close itself.
     */
    val everLoaded: Boolean = false,
)

/**
 * The screen behind a tapped job: the road, the car, and the next instruction.
 *
 * ## Where it points
 *
 * At the shop until the food is in the bag, and at the door after — the same
 * rule the "Navigate" button follows, because a rider asking "where am I going"
 * mid-job means one thing at a time. Advancing the stage re-routes by itself:
 * the destination changes, so the route does.
 *
 * ## What it does not do
 *
 * It does not read out instructions, and it does not notice when a rider leaves
 * the route. Both belong to real turn-by-turn, and this screen deliberately
 * stops short of it — the "Navigate" button still hands the trip to the app the
 * rider drives with every day, which has voice, offline tiles and re-routing.
 * See MapHandoff for that argument in full.
 */
@HiltViewModel
class JobDetailViewModel @Inject constructor(
    private val feed: WorkFeed,
    private val deliveries: DeliveryRepository,
    private val routes: RouteRepository,
    private val locations: LocationSource,
) : ViewModel() {

    private val _state = MutableStateFlow(JobDetailUiState())
    val state: StateFlow<JobDetailUiState> = _state.asStateFlow()

    private var jobId: String? = null
    private var lastPoint: MapPoint? = null

    /** Where the current route was computed from. Null until there is one. */
    private var routedFrom: MapPoint? = null

    /**
     * How far the rider may drift before the road is worth asking for again.
     *
     * Only matters on the way to the shop, where the route starts from *them* —
     * so standing still costs nothing and crossing town costs one Directions
     * call per quarter kilometre, rather than one per fix.
     */
    private val reRouteAfterMetres = 250.0


    /**
     * Point this at a job. Called once, when the screen opens.
     *
     * Collects the shared feed rather than sampling it, and that is the whole
     * of a bug worth remembering: `WorkFeed.state` is `WhileSubscribed`, and
     * this screen replaces the list that was its only subscriber. Reading
     * `.value` here meant reading a flow that had stopped — so tapping
     * "Picked up" advanced the job on the server and changed nothing on screen.
     * Collecting fixes it twice over: the stage lands, and the polling loop
     * stays alive while a rider is actually working.
     */
    fun open(id: String) {
        if (jobId == id) return
        jobId = id

        viewModelScope.launch {
            feed.state.collect { work ->
                val job = work.active.firstOrNull { it.id == id }
                val previous = _state.value.job

                _state.update {
                    it.copy(job = job, everLoaded = it.everLoaded || job != null)
                }

                // A new job, or one whose stage moved — either way the
                // destination may have changed, so the road has.
                if (job != null && job.stage != previous?.stage) loadRoute(job)
            }
        }

        watchLocation()
    }

    /**
     * The road to wherever the rider is headed *now*.
     *
     * Recomputed when the stage moves, because the destination does: the shop
     * while the food is still on its counter, the customer's door after.
     */
    private fun loadRoute(job: DeliveryAssignment) {
        val from = MapPoint(job.offer.pickup.lat, job.offer.pickup.lng)
        val to = MapPoint(job.deliveryLat, job.deliveryLng)

        val goingToShop = job.stage == DeliveryStage.Assigned || job.stage == DeliveryStage.Pending
        val here = _state.value.here

        // Heading to the shop, the useful road is the one from where the rider
        // *is* — not the shop-to-door line, which they are not on yet. Without a
        // fix there is no such road, so the whole trip is the honest fallback.
        val start = if (goingToShop && here?.placed == true) here else from
        val end = if (goingToShop) from else to

        routedFrom = start

        viewModelScope.launch {
            val route = routes.route(start, end)

            _state.update {
                it.copy(
                    route = route,
                    step = NavigationProgress.currentStep(route?.steps.orEmpty(), it.here),
                )
            }
        }
    }

    /**
     * The live fix stream, for as long as this screen is on screen.
     *
     * Not the shared reporter: that one exists to tell the *shop* where a rider
     * is and only runs when they have opted into being watched. This is the
     * rider's own map, and it should work whether or not they are sharing.
     */
    private fun watchLocation() {
        if (!locations.permitted()) return

        viewModelScope.launch {
            locations.fixes().collect { fix ->
                val point = MapPoint(fix.lat, fix.lng)

                val heading = NavigationProgress.heading(
                    reported = fix.headingDeg,
                    previous = lastPoint,
                    current = point,
                )

                lastPoint = point

                _state.update {
                    it.copy(
                        here = point,
                        // Kept when there is no new answer, so a rider stopped
                        // at a light keeps pointing the way they were going
                        // rather than snapping to north.
                        headingDeg = heading ?: it.headingDeg,
                        step = NavigationProgress.currentStep(it.route?.steps.orEmpty(), point),
                    )
                }

                reRouteIfDrifted(point)
            }
        }
    }

    /**
     * Ask for the road again once the rider has genuinely moved along it.
     *
     * Without this the distance and the ETA are frozen at whatever they were
     * when the screen opened, which is worse than showing nothing: a strip that
     * says "4 min" for the whole trip reads as live and is not. Only the leg to
     * the shop needs it — that route starts from the rider. The leg to the door
     * runs shop-to-door and does not move under them.
     */
    private fun reRouteIfDrifted(point: MapPoint) {
        val job = _state.value.job ?: return

        val goingToShop = job.stage == DeliveryStage.Assigned || job.stage == DeliveryStage.Pending
        if (!goingToShop) return

        val from = routedFrom
        if (from != null && NavigationProgress.distanceMetres(from, point) < reRouteAfterMetres) {
            return
        }

        loadRoute(job)
    }

    /**
     * Give the job back to the board.
     *
     * Only ever offered before pickup — the server refuses anything past
     * `assigned` with a 422, because by then the food is physically with the
     * rider and handing it back is a phone call, not a button. The screen does
     * not navigate away itself: the job leaves the feed, the collector in
     * [open] sees a null, and the screen closes on the same path a delivered
     * job takes.
     */
    fun release() {
        val job = _state.value.job ?: return
        if (_state.value.busy) return

        _state.update { it.copy(busy = true, error = null) }

        viewModelScope.launch {
            val error = try {
                deliveries.release(job.id)
                feed.released(job.id)
                null
            } catch (e: ApiException) {
                e.message
            }

            _state.update { it.copy(busy = false, error = error) }
        }
    }

    /** Advance the stage, then re-route: the destination has changed. */
    fun advance(next: DeliveryStage) {
        val job = _state.value.job ?: return
        if (_state.value.busy) return

        _state.update { it.copy(busy = true, error = null) }

        viewModelScope.launch {
            // Written back through the shared feed, not held here: the board
            // and this screen must not disagree about which stage a job is in.
            // The collector in [open] is what puts the new stage on screen —
            // and re-routes, because the destination has changed.
            val error = try {
                feed.advanced(deliveries.advance(job.id, next))
                null
            } catch (e: ApiException) {
                e.message
            }

            _state.update { it.copy(busy = false, error = error) }
        }
    }
}
