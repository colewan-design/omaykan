package com.omaykan.rider.feature.forgot

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omaykan.rider.core.data.AccountRepository
import com.omaykan.rider.core.network.ApiException
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ForgotPasswordUiState(
    val email: String = "",
    val submitting: Boolean = false,
    val emailError: String? = null,
    val error: String? = null,
    /**
     * The server's own sentence, once it has answered.
     *
     * Non-null is the whole "it worked" state — there is no boolean beside it,
     * because the thing to show is the message and not the fact.
     */
    val sent: String? = null,
) {
    val canSubmit: Boolean
        get() = !submitting && sent == null && email.isNotBlank()
}

/**
 * Asking for a reset link.
 *
 * The reply is deliberately the same whether or not the address belongs to a
 * rider, and this screen shows it verbatim rather than turning it into "sent!".
 * Paraphrasing a deliberately ambiguous answer into a claim would make the
 * claim false half the time — and the platform's riders are a small, knowable
 * set, so an endpoint that distinguished them would be a way to enumerate it.
 */
@HiltViewModel
class ForgotPasswordViewModel @Inject constructor(
    private val accounts: AccountRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ForgotPasswordUiState())
    val state: StateFlow<ForgotPasswordUiState> = _state.asStateFlow()

    fun onEmailChange(value: String) = _state.update {
        it.copy(email = value, emailError = null, error = null)
    }

    fun submit() {
        val current = _state.value
        if (!current.canSubmit) return

        _state.update { it.copy(submitting = true, emailError = null, error = null) }

        viewModelScope.launch {
            try {
                val message = accounts.requestPasswordReset(current.email)
                _state.update { it.copy(submitting = false, sent = message) }
            } catch (e: ApiException) {
                _state.update {
                    it.copy(
                        submitting = false,
                        emailError = (e as? ApiException.Validation)?.first("email"),
                        // Throttled at 5/min, and a rider who cannot get in will
                        // press this more than five times.
                        error = if (e is ApiException.Validation) null else e.message,
                    )
                }
            }
        }
    }
}
