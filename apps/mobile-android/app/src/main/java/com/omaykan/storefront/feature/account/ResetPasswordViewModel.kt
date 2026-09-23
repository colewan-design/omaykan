package com.omaykan.storefront.feature.account

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.omaykan.storefront.core.data.AccountRepository
import com.omaykan.storefront.core.network.ApiException
import com.omaykan.storefront.navigation.ResetPasswordRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ResetPasswordUiState(
    val email: String = "",
    val password: String = "",
    val confirmation: String = "",
    val showPassword: Boolean = false,
    val busy: Boolean = false,
    val error: String? = null,
    /**
     * Set when the link itself is the problem — expired, or already used.
     *
     * Separate from [error] because there is no retry: the form is useless from
     * here on, and the only move left is asking for a new link.
     */
    val linkDead: String? = null,
    /** Name of the shopper now signed in, once the reset has gone through. */
    val done: String? = null,
) {
    val localProblem: String?
        get() = when {
            password.length < 8 -> "At least 8 characters."
            confirmation.isNotEmpty() && confirmation != password -> "Those two do not match."
            else -> null
        }

    val canSubmit: Boolean
        get() = !busy && linkDead == null && password.length >= 8 && confirmation == password
}

/**
 * The far end of the reset email.
 *
 * Everything it needs arrives in the route — the token and the address it was
 * issued for — so there is nothing to look up and nothing to remember. Success
 * signs the shopper in, because the server mints a session as part of the
 * reset (see AccountRepository.resetPassword).
 */
@HiltViewModel
class ResetPasswordViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: AccountRepository,
) : ViewModel() {

    private val route = savedStateHandle.toRoute<ResetPasswordRoute>()

    private val _state = MutableStateFlow(ResetPasswordUiState(email = route.email))
    val state: StateFlow<ResetPasswordUiState> = _state.asStateFlow()

    fun onPassword(value: String) = _state.update { it.copy(password = value, error = null) }

    fun onConfirmation(value: String) = _state.update { it.copy(confirmation = value, error = null) }

    fun togglePasswordVisible() = _state.update { it.copy(showPassword = !it.showPassword) }

    fun submit() {
        val current = _state.value
        if (!current.canSubmit) return

        _state.update { it.copy(busy = true, error = null) }

        viewModelScope.launch {
            try {
                val account = repository.resetPassword(
                    token = route.token,
                    email = route.email,
                    password = current.password,
                )
                _state.update { it.copy(busy = false, done = account.name) }
            } catch (e: ApiException.Validation) {
                // The server puts a spent or expired link on `token`, and
                // anything it dislikes about the password on `password`.
                val dead = e.errors["token"]?.firstOrNull()
                _state.update {
                    it.copy(
                        busy = false,
                        linkDead = dead,
                        error = if (dead == null) e.errors["password"]?.firstOrNull() ?: e.message else null,
                    )
                }
            } catch (e: ApiException) {
                _state.update { it.copy(busy = false, error = e.message) }
            }
        }
    }

    fun dismissError() = _state.update { it.copy(error = null) }
}
