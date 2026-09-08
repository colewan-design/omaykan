package com.omaykan.seller.feature.signin

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omaykan.seller.core.auth.GoogleAuthFlow
import com.omaykan.seller.core.auth.GoogleAuthResult
import com.omaykan.seller.core.data.PendingSignIn
import com.omaykan.seller.core.data.SessionRepository
import com.omaykan.seller.core.model.StaffStore
import com.omaykan.seller.core.network.ApiException
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SignInUiState(
    /** Username or email: accounts made from a username alone still exist. */
    val identifier: String = "",
    val password: String = "",
    val submitting: Boolean = false,
    /**
     * The shops the account can open, once there is more than one.
     *
     * Empty is the ordinary case — one shop goes straight through, because
     * asking somebody who works in one place which place they are in is a
     * question with one answer.
     */
    val storeChoices: List<StaffStore> = emptyList(),
    /** Sits under whichever field the server rejected. */
    val identifierError: String? = null,
    /** Anything that is not about a field: offline, rate limits, a 500. */
    val error: String? = null,
) {
    val canSubmit: Boolean
        get() = !submitting && identifier.isNotBlank() && password.isNotBlank()

    val choosingStore: Boolean
        get() = storeChoices.isNotEmpty()
}

/**
 * Signing in to a shop.
 *
 * Two calls behind one button — prove who, then choose where — because to the
 * person holding the phone it is one act. The second half only becomes visible
 * when it has to: a manager who covers three branches sees a list, and everyone
 * else goes straight to their orders.
 *
 * This replaced pairing with the shop's code. The trade is stated in
 * SessionRepository, and the part that shows up here is that a wrong password
 * and an unknown account come back as the same 422 — the server refuses to say
 * which, so neither does this screen.
 */
@HiltViewModel
class SignInViewModel @Inject constructor(
    private val sessions: SessionRepository,
    private val google: GoogleAuthFlow,
) : ViewModel() {

    private val _state = MutableStateFlow(SignInUiState())
    val state: StateFlow<SignInUiState> = _state.asStateFlow()

    private var pendingToken: String? = null

    /** False on a build with no OAuth client id: the button simply is not there. */
    val googleAvailable: Boolean get() = google.available

    init {
        // The redirect lands in a different activity, in a different task, so
        // the result arrives here rather than as a return value. Collected for
        // the life of the ViewModel because the browser can hand it back while
        // this screen is being recreated.
        viewModelScope.launch {
            google.results.collectLatest { result ->
                google.consume()

                when (result) {
                    is GoogleAuthResult.Code -> {
                        start()
                        attempt { sessions.signInWithGoogle(result.code, result.codeVerifier) }
                    }
                    // Backing out is not a failure. Stop the spinner and say
                    // nothing: they know what they did.
                    GoogleAuthResult.Cancelled -> _state.update { it.copy(submitting = false) }
                    is GoogleAuthResult.Failed -> _state.update {
                        it.copy(submitting = false, error = result.message)
                    }
                }
            }
        }
    }

    fun onIdentifierChange(value: String) = _state.update {
        // Errors cleared as soon as the field is touched. Leaving "incorrect
        // username or password" under a field somebody is retyping is the app
        // arguing with them while they fix it.
        it.copy(identifier = value, identifierError = null, error = null)
    }

    fun onPasswordChange(value: String) = _state.update {
        it.copy(password = value, identifierError = null, error = null)
    }

    fun submit() {
        val current = _state.value
        if (!current.canSubmit) return

        start()

        viewModelScope.launch {
            attempt { sessions.signIn(current.identifier, current.password) }
        }
    }

    /**
     * Opens Google's consent page in the phone's own browser.
     *
     * The spinner starts here and the result arrives through [google]'s stream,
     * because the redirect comes back into a different activity entirely.
     */
    fun signInWithGoogle(context: Context) {
        if (_state.value.submitting) return

        start()

        if (!google.launch(context)) {
            _state.update {
                it.copy(
                    submitting = false,
                    error = "No browser on this phone could open Google's sign-in page.",
                )
            }
        }
    }

    fun chooseStore(store: StaffStore) {
        val token = pendingToken ?: return
        if (_state.value.submitting) return

        _state.update { it.copy(submitting = true, error = null) }

        viewModelScope.launch {
            try {
                sessions.chooseStore(token, store)
                // No navigation from here. Choosing writes the token and the
                // store, the session flow turns Paired, and MainActivity swaps
                // the screen — one source of truth for "am I signed in", rather
                // than a screen that thinks it is and a session that disagrees.
            } catch (e: ApiException) {
                _state.update { it.copy(submitting = false, error = e.message) }
            }
        }
    }

    /** Back out of the store list to the form, e.g. wrong account entirely. */
    fun cancelStoreChoice() {
        pendingToken = null
        _state.update { it.copy(storeChoices = emptyList(), password = "", error = null) }
    }

    private fun start() = _state.update {
        it.copy(submitting = true, identifierError = null, error = null)
    }

    private suspend fun attempt(signIn: suspend () -> PendingSignIn) {
        try {
            val pending = signIn()
            pendingToken = pending.token

            when (pending.stores.size) {
                // A sign-in with no shop behind it is refused server-side, so
                // this is only reachable if that ever stops being true. Say
                // something true rather than showing an empty list.
                0 -> _state.update {
                    it.copy(
                        submitting = false,
                        error = "This account isn't set up for any shop yet. Ask an admin to add you in Staff.",
                    )
                }
                1 -> chooseStore(pending.stores.first())
                else -> _state.update {
                    it.copy(submitting = false, storeChoices = pending.stores)
                }
            }
        } catch (e: ApiException) {
            _state.update { it.withFailure(e) }
        }
    }

    private fun SignInUiState.withFailure(e: ApiException): SignInUiState {
        val base = copy(submitting = false, password = "")

        return when (e) {
            // 422: a wrong password, an unknown account, or an account that
            // signs in with Google and has no password to check. The server's
            // own sentence distinguishes the third; the first two it will not
            // tell apart, on purpose.
            is ApiException.Validation -> base.copy(identifierError = e.message)
            // 403: real account, cannot sign in here — unverified email,
            // disabled, or no membership anywhere. Each names a different thing
            // to go and do, so the server's wording is shown as it stands.
            is ApiException.Forbidden -> base.copy(error = e.message)
            // Both sign-in doors are throttled to 10/min, and somebody fixing a
            // typo on a phone keyboard can genuinely reach it.
            is ApiException.RateLimited -> base.copy(error = e.message)
            else -> base.copy(error = e.message)
        }
    }
}
