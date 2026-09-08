package com.omaykan.rider

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omaykan.rider.core.data.SessionRepository
import com.omaykan.rider.core.model.SessionState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Which of the three screens the app is on.
 *
 * The initial value is [SessionState.Restoring] and that is the load-bearing
 * part: the session is a combine over two reads of an EncryptedSharedPreferences
 * file behind an Android keystore unlock, neither of which has answered on the
 * first frame. Starting at SignedOut would show the sign-in screen for a moment
 * on every cold start of an app that is already signed in.
 *
 * The refresh on construction is the other half. The cached profile decides
 * what to show *now*; `GET /api/rider/me` says what is actually true, and it is
 * the only way an approval — or a suspension — reaches a phone, since there is
 * no push. Its failure is swallowed inside the repository: a rider opening the
 * app underground keeps the screen the cache chose.
 */
@HiltViewModel
class LaunchViewModel @Inject constructor(
    private val sessions: SessionRepository,
) : ViewModel() {

    val session: StateFlow<SessionState> = sessions.session.stateIn(
        scope = viewModelScope,
        // Eagerly, not WhileSubscribed: this is the flow that decides what is
        // on screen, and it must not drop back to Restoring — blanking the
        // app — while the phone is rotated or briefly backgrounded.
        started = SharingStarted.Eagerly,
        initialValue = SessionState.Restoring,
    )

    init {
        viewModelScope.launch { sessions.refresh() }
    }
}
