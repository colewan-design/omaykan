package com.omaykan.storefront.feature.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omaykan.storefront.core.data.AccountRepository
import com.omaykan.storefront.core.model.CustomerPreferences
import com.omaykan.storefront.core.model.SessionState
import com.omaykan.storefront.core.model.Substitution
import com.omaykan.storefront.core.network.ApiException
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProfileUiState(
    val loaded: Boolean = false,
    val name: String = "",
    val phone: String = "",
    val preferences: CustomerPreferences = CustomerPreferences(),
    val busy: Boolean = false,
    val fieldErrors: Map<String, String> = emptyMap(),
    val error: String? = null,
    val notice: String? = null,
    /** True once the form differs from what the server last confirmed. */
    val dirty: Boolean = false,
) {
    val canSave: Boolean get() = !busy && dirty && name.isNotBlank()
}

/**
 * Name, phone, and the four settings that change what happens to an order.
 *
 * The form is edited on a copy and only written when Save is pressed, so an
 * abandoned edit does not rewrite a profile that feeds a real delivery — a
 * half-typed phone number must not be what a rider calls. The preference
 * toggles are part of the same copy for consistency, even though each is one
 * boolean the server would happily take on its own.
 */
@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val repository: AccountRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ProfileUiState())
    val state: StateFlow<ProfileUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            repository.session.collect { session ->
                val account = (session as? SessionState.SignedIn)?.account ?: return@collect

                // Only ever seeds the form. A refresh arriving mid-edit must
                // not throw away what is being typed.
                _state.update { current ->
                    if (current.loaded && current.dirty) {
                        current
                    } else {
                        current.copy(
                            loaded = true,
                            name = account.name,
                            phone = account.phone,
                            preferences = account.preferences,
                            dirty = false,
                        )
                    }
                }
            }
        }
    }

    fun onName(value: String) = edit { it.copy(name = value, fieldErrors = it.fieldErrors - "name") }

    fun onPhone(value: String) = edit { it.copy(phone = value, fieldErrors = it.fieldErrors - "phone") }

    fun onEmailUpdates(on: Boolean) = edit {
        it.copy(preferences = it.preferences.copy(emailUpdates = on))
    }

    fun onSmsUpdates(on: Boolean) = edit {
        it.copy(preferences = it.preferences.copy(smsUpdates = on))
    }

    fun onMarketingEmails(on: Boolean) = edit {
        it.copy(preferences = it.preferences.copy(marketingEmails = on))
    }

    fun onSubstitutions(choice: Substitution) = edit {
        it.copy(preferences = it.preferences.copy(substitutions = choice))
    }

    fun save() {
        val current = _state.value
        if (!current.canSave) return

        _state.update { it.copy(busy = true, error = null, fieldErrors = emptyMap()) }

        viewModelScope.launch {
            try {
                repository.updateProfile(
                    name = current.name,
                    phone = current.phone,
                    preferences = current.preferences,
                )
                _state.update { it.copy(busy = false, dirty = false, notice = "Saved.") }
            } catch (e: ApiException.Validation) {
                _state.update {
                    it.copy(
                        busy = false,
                        fieldErrors = e.errors.mapValues { (_, messages) -> messages.first() },
                        error = if (e.errors.isEmpty()) e.message else null,
                    )
                }
            } catch (e: ApiException) {
                _state.update { it.copy(busy = false, error = e.message) }
            }
        }
    }

    fun onNoticeShown() = _state.update { it.copy(notice = null) }

    private fun edit(block: (ProfileUiState) -> ProfileUiState) =
        _state.update { block(it).copy(dirty = true, error = null) }
}
