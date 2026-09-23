package com.omaykan.rider.feature.signin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omaykan.rider.core.data.SessionRepository
import com.omaykan.rider.core.network.ApiException
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SignInUiState(
    val email: String = "",
    val password: String = "",
    val submitting: Boolean = false,
    /** Sits under the email field, where the server puts its answer. */
    val emailError: String? = null,
    /** Anything that is not about a field: offline, a rate limit, a 500. */
    val error: String? = null,
) {
    val canSubmit: Boolean
        get() = !submitting && email.isNotBlank() && password.isNotBlank()
}

/**
 * Signing in with an email and a password.
 *
 * A person, not a phone — the difference from :seller, which pairs with a
 * shop's code. A rider's token is minted against their own account and their
 * work is scoped to them, so there is nothing to pair with.
 *
 * Note where a wrong password lands. `RiderAuthController` answers a 422 with
 * the message under `email` for both "no such account" and "wrong password",
 * deliberately and identically, so the reply neither says nor times differently
 * whether an address is registered. This screen honours that by putting it
 * under the email field exactly as sent, rather than helpfully re-filing it
 * under the password.
 */
@HiltViewModel
class SignInViewModel @Inject constructor(
    private val sessions: SessionRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(SignInUiState())
    val state: StateFlow<SignInUiState> = _state.asStateFlow()

    fun onEmailChange(value: String) = _state.update {
        // Errors cleared as soon as the field is touched. Leaving a rejection
        // under a field somebody is retyping is the app arguing with them while
        // they fix it.
        it.copy(email = value, emailError = null, error = null)
    }

    fun onPasswordChange(value: String) = _state.update {
        it.copy(password = value, emailError = null, error = null)
    }

    fun submit() {
        val current = _state.value
        if (!current.canSubmit) return

        _state.update { it.copy(submitting = true, emailError = null, error = null) }

        viewModelScope.launch {
            try {
                sessions.signIn(current.email, current.password)
                // No navigation from here, and no success state either. Signing
                // in writes the token and the profile, the session flow turns
                // Gated or Working, and MainActivity swaps the screen out from
                // under this one.
            } catch (e: ApiException) {
                _state.update { it.withFailure(e) }
            }
        }
    }

    private fun SignInUiState.withFailure(e: ApiException): SignInUiState {
        val base = copy(submitting = false)

        return when (e) {
            // The server's own sentence, under the field it named — and the
            // password field emptied, because this is the one failure that
            // means what was in it was wrong. Every other case below keeps it:
            // a rider who lost signal mid-tap should press the button again,
            // not retype a password on a bike.
            is ApiException.Validation -> base.copy(
                password = "",
                emailError = e.first("email") ?: e.message,
            )
            // Login is throttled to 10/min and a rider guessing at which of two
            // passwords they used can genuinely reach it.
            is ApiException.RateLimited -> base.copy(error = e.message)
            else -> base.copy(error = e.message)
        }
    }
}
