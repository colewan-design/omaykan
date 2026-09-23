package com.omaykan.storefront.feature.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omaykan.storefront.core.data.AccountRepository
import com.omaykan.storefront.core.model.SessionState
import com.omaykan.storefront.core.network.ApiException
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SecurityUiState(
    val loaded: Boolean = false,
    /** The address on the account, as the server last confirmed it. */
    val currentEmail: String = "",
    val hasPassword: Boolean = false,
    val googleLinked: Boolean = false,

    val email: String = "",
    val emailPassword: String = "",
    val emailBusy: Boolean = false,
    val emailError: String? = null,

    val currentPassword: String = "",
    val newPassword: String = "",
    val confirmation: String = "",
    val passwordBusy: Boolean = false,
    val passwordError: String? = null,

    val notice: String? = null,
) {
    /**
     * A Google-only account cannot move its address from here.
     *
     * Not a rule this app invented: the server refuses it before it looks at
     * the payload, because Google's copy is what the next sign-in arrives with
     * — so a change made here is either undone or, worse, stops matching and
     * mints a second account.
     */
    val emailManagedByGoogle: Boolean get() = googleLinked && !hasPassword

    val canSaveEmail: Boolean
        get() = !emailBusy &&
            !emailManagedByGoogle &&
            email.isNotBlank() &&
            !email.equals(currentEmail, ignoreCase = true) &&
            emailPassword.isNotEmpty()

    val passwordProblem: String?
        get() = when {
            newPassword.length < 8 -> "At least 8 characters."
            confirmation.isNotEmpty() && confirmation != newPassword -> "Those two do not match."
            else -> null
        }

    val canSavePassword: Boolean
        get() = !passwordBusy &&
            newPassword.length >= 8 &&
            confirmation == newPassword &&
            (!hasPassword || currentPassword.isNotEmpty())
}

/**
 * The credentials, each behind the current password.
 *
 * Both forms live on one screen because they answer the same question — "who
 * can get into this account" — and because a Google-only shopper needs to see
 * why one of them is closed to them and what opens it: setting a password here
 * is what makes the email theirs to change.
 */
@HiltViewModel
class SecurityViewModel @Inject constructor(
    private val repository: AccountRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(SecurityUiState())
    val state: StateFlow<SecurityUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            repository.session.collect { session ->
                val account = (session as? SessionState.SignedIn)?.account ?: return@collect

                _state.update { current ->
                    current.copy(
                        loaded = true,
                        currentEmail = account.email,
                        hasPassword = account.hasPassword,
                        googleLinked = account.googleLinked,
                        // Seeded once. A refresh arriving mid-edit must not
                        // overwrite an address being typed.
                        email = if (current.loaded) current.email else account.email,
                    )
                }
            }
        }
    }

    fun onEmail(value: String) = _state.update { it.copy(email = value.trim(), emailError = null) }

    fun onEmailPassword(value: String) =
        _state.update { it.copy(emailPassword = value, emailError = null) }

    fun onCurrentPassword(value: String) =
        _state.update { it.copy(currentPassword = value, passwordError = null) }

    fun onNewPassword(value: String) =
        _state.update { it.copy(newPassword = value, passwordError = null) }

    fun onConfirmation(value: String) =
        _state.update { it.copy(confirmation = value, passwordError = null) }

    fun saveEmail() {
        val current = _state.value
        if (!current.canSaveEmail) return

        _state.update { it.copy(emailBusy = true, emailError = null) }

        viewModelScope.launch {
            try {
                repository.updateEmail(current.email, current.emailPassword)
                _state.update {
                    it.copy(
                        emailBusy = false,
                        // The password field is cleared and the email field is
                        // not: the address is now what the account holds, and
                        // showing it is the confirmation.
                        emailPassword = "",
                        notice = "Email changed.",
                    )
                }
            } catch (e: ApiException.Validation) {
                _state.update {
                    it.copy(
                        emailBusy = false,
                        emailError = e.first("email")
                            ?: e.first("currentPassword")
                            ?: e.message,
                    )
                }
            } catch (e: ApiException) {
                _state.update { it.copy(emailBusy = false, emailError = e.message) }
            }
        }
    }

    fun savePassword() {
        val current = _state.value
        if (!current.canSavePassword) return

        _state.update { it.copy(passwordBusy = true, passwordError = null) }

        viewModelScope.launch {
            try {
                repository.updatePassword(
                    currentPassword = current.currentPassword.takeIf { current.hasPassword },
                    password = current.newPassword,
                )
                _state.update {
                    it.copy(
                        passwordBusy = false,
                        currentPassword = "",
                        newPassword = "",
                        confirmation = "",
                        notice = if (current.hasPassword) {
                            "Password changed. Your other devices are signed out."
                        } else {
                            "Password set. You can now sign in with it, or change your email."
                        },
                    )
                }
            } catch (e: ApiException.Validation) {
                _state.update {
                    it.copy(
                        passwordBusy = false,
                        passwordError = e.first("currentPassword")
                            ?: e.first("password")
                            ?: e.message,
                    )
                }
            } catch (e: ApiException) {
                _state.update { it.copy(passwordBusy = false, passwordError = e.message) }
            }
        }
    }

    fun onNoticeShown() = _state.update { it.copy(notice = null) }
}
