package com.omaykan.seller

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omaykan.seller.core.data.SessionRepository
import com.omaykan.seller.core.model.SessionState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * Which of the two screens the app is on.
 *
 * The initial value is [SessionState.Restoring] and that is the load-bearing
 * part: the session is a combine over an encrypted preferences read and a
 * DataStore read, neither of which has answered on the first frame. Starting at
 * SignedOut instead would show the sign-in screen for a moment on every cold
 * start of an app that is already paired.
 */
@HiltViewModel
class LaunchViewModel @Inject constructor(
    sessions: SessionRepository,
) : ViewModel() {

    val session: StateFlow<SessionState> = sessions.session.stateIn(
        scope = viewModelScope,
        // Eagerly, not WhileSubscribed: this is the flow that decides what is
        // on screen, and it must not drop back to Restoring — blanking the app
        // — while the phone is rotated or briefly backgrounded.
        started = SharingStarted.Eagerly,
        initialValue = SessionState.Restoring,
    )
}
