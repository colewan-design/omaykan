package com.omaykan.rider.feature.status

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omaykan.rider.core.data.SessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The waiting screen's only job: ask again.
 *
 * There is no push on this platform, so an approval reaches a phone exactly one
 * way — `GET /api/rider/me`, called by somebody. This calls it when the screen
 * opens and when the rider taps Check again, and the session flow does the
 * rest: a status of `approved` turns SessionState into Working and MainActivity
 * replaces this screen with the job board.
 *
 * No timer, deliberately. A poll here would run all day against an answer that
 * changes once, and an operator's review is measured in hours. The rider
 * reopening the app is the natural trigger, and it is already wired: the
 * refresh in LaunchViewModel's `init` fires on every cold start.
 */
@HiltViewModel
class StatusViewModel @Inject constructor(
    private val sessions: SessionRepository,
) : ViewModel() {

    private val _checking = MutableStateFlow(false)
    val checking: StateFlow<Boolean> = _checking.asStateFlow()

    fun check() {
        if (_checking.value) return
        _checking.value = true

        viewModelScope.launch {
            // Failure is swallowed inside the repository — the cached profile
            // is still the right thing to show, and a rider waiting on a human
            // does not need an error about a network they will retry anyway.
            sessions.refresh()
            _checking.value = false
        }
    }

    fun signOut() {
        viewModelScope.launch { sessions.signOut() }
    }
}
