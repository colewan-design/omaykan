package com.omaykan.rider.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omaykan.rider.core.data.EarningsRepository
import com.omaykan.rider.core.data.SessionRepository
import com.omaykan.rider.core.data.WorkFeed
import com.omaykan.rider.core.data.WorkState
import com.omaykan.rider.core.location.LocationShareController
import com.omaykan.rider.core.location.LocationSource
import com.omaykan.rider.core.location.PositionReporter
import com.omaykan.rider.core.location.SharingState
import com.omaykan.rider.core.model.Earnings
import com.omaykan.rider.core.network.ApiException
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The two numbers at the top of the home screen.
 *
 * They are deliberately not the same *kind* of number, and the screen says so:
 * one is money that has already been earned and one is money the rider is about
 * to be holding on somebody else's behalf. Adding them, or drawing them the
 * same way, would be the app's own invitation to spend the shop's takings.
 */
data class HomeUiState(
    val earnings: Earnings = Earnings.Empty,
    val loading: Boolean = true,
    /** True once a fetch has succeeded, so a zero reads as a zero and not as "not yet". */
    val loaded: Boolean = false,
    val error: String? = null,
)

/**
 * The screen a rider lands on: what today has paid, what they are holding, and
 * what they have just finished.
 *
 * ## Why it reads two sources and owns neither
 *
 * The jobs come from [WorkFeed], which is the app's single polling loop and is
 * shared with the board — the home screen must not disagree with the Jobs tab
 * about what is being carried, and two feeds would eventually do exactly that.
 * The totals come from `GET /api/rider/earnings`, which aggregates over every
 * row in the database rather than over the thirty jobs this app happens to have
 * fetched. Neither is computed here.
 *
 * The one figure that *is* computed here is [cashInHand], and it is a sum over
 * jobs in hand rather than a server total, because there is no endpoint for it —
 * see the property.
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val feed: WorkFeed,
    private val earnings: EarningsRepository,
    private val sessions: SessionRepository,
    private val sharing: LocationShareController,
    private val locations: LocationSource,
    reporter: PositionReporter,
) : ViewModel() {

    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    /**
     * The jobs. Collecting this is also what runs the polling loop — see
     * [WorkFeed.state] — so this screen keeps the board current while it is
     * open and stops when the phone goes in a pocket.
     */
    val work: StateFlow<WorkState> = feed.state

    /**
     * Whether the rider is on the map, and how that is going.
     *
     * The same pairing the work screen used to make, for the same reason:
     * [PositionReporter.state] knows how the pings are going, and only
     * [LocationShareController.sharing] knows whether the service is still
     * alive — it can end from the Stop action on its own notification or from
     * the system reclaiming memory, and reading the reporter alone would leave
     * the switch on over a dead feed.
     */
    val sharingState: StateFlow<SharingState> =
        combine(sharing.sharing, reporter.state) { alive, reported ->
            reported.copy(sharing = alive)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SharingState())

    init {
        load()
    }

    /** Whether the rider has granted location at all, checked on each recomposition. */
    fun locationGranted(): Boolean = locations.permitted()

    /**
     * Turn sharing on or off.
     *
     * Off is not merely "stop sending": the service erases the last fix from
     * the server on its way out, so a rider who switches this off disappears
     * from every map rather than freezing on one.
     */
    fun setSharing(on: Boolean) {
        if (on) sharing.start() else sharing.stop()
    }

    /** Both halves of the screen, on one pull. */
    fun refresh() {
        feed.refresh()
        load()
    }

    private fun load() {
        _state.update { it.copy(loading = true, error = null) }

        viewModelScope.launch {
            try {
                val summary = earnings.fetch()
                _state.update {
                    it.copy(earnings = summary, loading = false, loaded = true, error = null)
                }
            } catch (e: ApiException) {
                // A suspension that lands here moves the whole app rather than
                // this screen: the gate answers 403 with the new status,
                // SessionRepository folds it in, and MainActivity swaps the
                // shell for the status screen by itself.
                if (!sessions.applyGate(e)) {
                    _state.update { it.copy(loading = false, error = e.message) }
                }
            }
        }
    }
}

/**
 * Money in the rider's pocket that belongs to a shop.
 *
 * Summed over the jobs in hand, and only those: a delivered job's cash has
 * already been handed over at the counter, and a job on the board has nothing
 * to do with this rider at all. It is the one figure on the home screen this
 * app works out for itself, because there is no endpoint that answers it — the
 * backend records what an order is worth, not what a rider is carrying in
 * notes.
 *
 * That makes it an estimate of a physical fact rather than an accounting
 * balance, which is why the card that draws it says "to collect" rather than
 * "owed" and offers no way to settle anything. A Settle button here would be a
 * promise this platform's API cannot keep.
 */
val WorkState.cashInHand: Long
    get() = active.sumOf { it.offer.collectCents }
