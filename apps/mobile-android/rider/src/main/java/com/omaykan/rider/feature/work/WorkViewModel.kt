package com.omaykan.rider.feature.work

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omaykan.rider.core.data.DeliveryRepository
import com.omaykan.rider.core.data.SessionRepository
import com.omaykan.rider.core.data.WorkFeed
import com.omaykan.rider.core.data.WorkState
import com.omaykan.rider.core.location.LocationSource
import com.omaykan.rider.core.map.MapPoint
import com.omaykan.rider.core.map.RouteRepository
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

/** Which half of the screen the rider is looking at. */
enum class WorkTab(val label: String) {
    Board("Available"),
    Mine("My jobs"),
}

/**
 * What this screen holds on top of the shared feed.
 *
 * The jobs themselves live in [WorkFeed] because they outlive this screen — the
 * polling loop is shared and survives a rotation. What is here is what belongs
 * to *looking* at them: which tab, which row is busy, and the sentence at the
 * top.
 */
data class WorkUiState(
    val tab: WorkTab = WorkTab.Board,
    /** The id of the job whose button is spinning. One at a time, on purpose. */
    val busyId: String? = null,
    /** Something that happened and went well enough to say so plainly. */
    val notice: String? = null,
    /** Something that failed. */
    val error: String? = null,
)

/**
 * The rider's actual job: what is going, what they are carrying, what they
 * finished.
 *
 * Nothing about the rider themselves any more. The location switch and the
 * running totals moved to [com.omaykan.rider.feature.home.HomeViewModel] with
 * the screen that draws them; what is left here is the board and the writes
 * against it.
 *
 * One view model for the board and the rider's own list rather than two,
 * because every action moves a job between them. Accepting takes one off the
 * board and puts it in `active`; releasing does the reverse. Two would each
 * hold half the truth and have to tell each other every time, which is how one
 * of them ends up showing a job that is already gone.
 *
 * Every call runs through [act], so a suspension mid-shift is dealt with in
 * one place: the approval gate answers 403 with the new status,
 * SessionRepository folds it into the session, and MainActivity swaps this
 * screen for the status one by itself — rather than leaving a rider staring at
 * a red banner over a board they can no longer use.
 */
@HiltViewModel
class WorkViewModel @Inject constructor(
    private val feed: WorkFeed,
    private val deliveries: DeliveryRepository,
    private val sessions: SessionRepository,
    private val locations: LocationSource,
    private val routes: RouteRepository,
) : ViewModel() {

    private val _ui = MutableStateFlow(WorkUiState())
    val ui: StateFlow<WorkUiState> = _ui.asStateFlow()

    /**
     * The jobs. Collecting this is also what runs the polling loop — see
     * [WorkFeed.state] — so the screen collects it with lifecycle awareness and
     * the fetching stops when the phone goes in a pocket.
     */
    val work: StateFlow<WorkState> = feed.state

    /**
     * Where this rider is, for the pin on a job card.
     *
     * Read rather than requested: `lastKnown` is whatever the platform already
     * has, so a card never turns the GPS on to draw a picture. Null is the
     * ordinary case — no permission, or a phone that has not fixed since it was
     * unlocked — and the map simply draws the shop without a rider on it.
     *
     * Refreshed when the list is. Not polled: a pin that is a minute stale on a
     * still picture is not worth a wakeup, and the live position other people
     * watch belongs to PositionReporter, not to this.
     */
    private val _riderPoint = MutableStateFlow<MapPoint?>(null)
    val riderPoint: StateFlow<MapPoint?> = _riderPoint.asStateFlow()

    /**
     * The driving route for a claimed job, if there is one to be had.
     *
     * Suspends, and answers instantly the second time — RouteRepository caches
     * per endpoint pair for the life of the process. Called from the card so
     * that a job scrolled past is never routed, which keeps a screen of six
     * jobs to the handful of Directions calls a rider can actually see.
     */
    suspend fun routeFor(job: DeliveryAssignment): String? = routes.polyline(
        MapPoint(job.offer.pickup.lat, job.offer.pickup.lng),
        MapPoint(job.deliveryLat, job.deliveryLng),
    )

    private fun refreshRiderPoint() {
        _riderPoint.value = locations
            .takeIf { it.permitted() }
            ?.lastKnown()
            ?.let { MapPoint(it.lat, it.lng) }
    }

    fun onTabChange(tab: WorkTab) = _ui.update { it.copy(tab = tab, notice = null, error = null) }

    fun refresh() {
        _ui.update { it.copy(error = null) }
        refreshRiderPoint()
        feed.refresh()
    }

    fun dismissMessages() = _ui.update { it.copy(notice = null, error = null) }

    /**
     * Take a job.
     *
     * The 409 is not an error and is not shown as one: two riders tapping the
     * same row a second apart is the normal case on a busy board, and the
     * server's conditional UPDATE means exactly one of them wins. The loser is
     * told plainly and the board is refreshed underneath them, so their next
     * tap is against what is actually still there.
     */
    fun accept(orderId: String) = act(orderId) {
        try {
            val assignment = deliveries.accept(orderId)
            feed.claimed(assignment)
            _ui.update {
                it.copy(
                    tab = WorkTab.Mine,
                    notice = "${assignment.offer.pickup.storeName} is yours. Head over and " +
                        "pick it up.",
                )
            }
        } catch (e: ApiException.Taken) {
            _ui.update { it.copy(notice = e.message) }
            feed.refresh()
        }
    }

    /** assigned → picked up → delivered. */
    fun advance(job: DeliveryAssignment, to: DeliveryStage) = act(job.id) {
        val updated = deliveries.advance(job.id, to)
        feed.advanced(updated)

        if (to == DeliveryStage.Delivered) {
            _ui.update { it.copy(notice = "Delivered. Nice one.") }
        }
    }

    /** Give a job back to the board. */
    fun release(job: DeliveryAssignment) = act(job.id) {
        deliveries.release(job.id)
        feed.released(job.id)
        _ui.update {
            it.copy(
                tab = WorkTab.Board,
                notice = "Handed back. It is on the board again.",
            )
        }
    }

    /**
     * Run one action against one job.
     *
     * One at a time, across the whole screen. That is stricter than it needs to
     * be and it is the right strictness here: these are writes against a
     * shared board taken by somebody holding a phone in one hand, and two in
     * flight at once is how a rider accepts a second job while the first is
     * still deciding whether it was taken.
     *
     * `busyId` names the row rather than raising a global spinner, so the
     * button that is thinking is the one that was pressed.
     */
    private fun act(orderId: String, block: suspend () -> Unit) {
        if (_ui.value.busyId != null) return

        _ui.update { it.copy(busyId = orderId, notice = null, error = null) }

        viewModelScope.launch {
            try {
                block()
            } catch (e: ApiException) {
                // The gate replaces the whole screen, so there is nothing to
                // say here — an error banner on a screen that is going away
                // would flash and then vanish.
                if (!sessions.applyGate(e)) {
                    _ui.update { it.copy(error = e.message) }
                }
            } finally {
                _ui.update { it.copy(busyId = null) }
            }
        }
    }

}