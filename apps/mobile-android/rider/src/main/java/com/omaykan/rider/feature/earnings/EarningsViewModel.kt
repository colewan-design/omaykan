package com.omaykan.rider.feature.earnings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omaykan.rider.core.data.EarningsRepository
import com.omaykan.rider.core.data.SessionRepository
import com.omaykan.rider.core.model.Earnings
import com.omaykan.rider.core.network.ApiException
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class EarningsUiState(
    val earnings: Earnings = Earnings.Empty,
    val loading: Boolean = true,
    /** True once a fetch has succeeded, so zeroes read as zeroes and not as "not yet". */
    val loaded: Boolean = false,
    val error: String? = null,
)

/**
 * Reads the summary once when the screen opens, and again when asked.
 *
 * No polling loop, unlike [com.omaykan.rider.core.data.WorkFeed]. The board
 * changes because other people place orders; earnings change because *this*
 * rider finished a job, which they did with their own thumb on the screen
 * before this one. A fortnight of totals refreshing every fifteen seconds would
 * be battery spent on a figure that cannot have moved.
 */
@HiltViewModel
class EarningsViewModel @Inject constructor(
    private val earnings: EarningsRepository,
    private val sessions: SessionRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(EarningsUiState())
    val state: StateFlow<EarningsUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        _state.update { it.copy(loading = true, error = null) }

        viewModelScope.launch {
            try {
                val summary = earnings.fetch()
                _state.update {
                    it.copy(earnings = summary, loading = false, loaded = true, error = null)
                }
            } catch (e: ApiException) {
                /*
                 * A suspension that lands while this screen is open moves the
                 * whole app, not this screen: the gate answers 403 with the
                 * account's status, the session flow turns Gated, and
                 * MainActivity swaps in the status screen. Showing an error
                 * banner over a set of totals the rider can no longer earn
                 * against would be the wrong screen with a note on it.
                 */
                if (!sessions.applyGate(e)) {
                    _state.update { it.copy(loading = false, error = e.message) }
                }
            }
        }
    }
}
