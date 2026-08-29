package com.omaykan.storefront.feature.pairing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omaykan.storefront.core.data.StoreCodeRepository
import com.omaykan.storefront.core.model.StoreRef
import com.omaykan.storefront.core.network.ApiException
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PairingUiState(
    val code: String = "",
    val submitting: Boolean = false,
    /** Shown against the code field: the code itself is the problem. */
    val fieldError: String? = null,
    /** Shown as a notice: the shop is real, but it cannot sell online. */
    val notice: String? = null,
    val resolved: StoreRef? = null,
) {
    val canSubmit: Boolean get() = code.trim().length >= MIN_CODE_LENGTH && !submitting

    private companion object {
        const val MIN_CODE_LENGTH = 4
    }
}

@HiltViewModel
class PairingViewModel @Inject constructor(
    private val repository: StoreCodeRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(PairingUiState())
    val state: StateFlow<PairingUiState> = _state.asStateFlow()

    fun onCodeChange(value: String) {
        // Codes are printed in upper case and read aloud across a counter, so
        // case and stray spaces are the shopper's typo to absorb, not theirs
        // to avoid.
        val cleaned = value.filter { !it.isWhitespace() }.uppercase()
        _state.update { it.copy(code = cleaned, fieldError = null, notice = null) }
    }

    fun submit() {
        val code = _state.value.code.trim()
        if (code.isEmpty() || _state.value.submitting) return

        _state.update { it.copy(submitting = true, fieldError = null, notice = null) }

        viewModelScope.launch {
            try {
                val store = repository.resolve(code)
                _state.update { it.copy(submitting = false, resolved = store.ref) }
            } catch (e: ApiException) {
                _state.update { current ->
                    when (e) {
                        // A real, open shop that simply does not sell online.
                        // Its own sentence, in its own place — telling someone
                        // the code is wrong here sends them back to a counter
                        // for a code that will never work.
                        is ApiException.Conflict -> current.copy(submitting = false, notice = e.message)
                        is ApiException.NotFound -> current.copy(
                            submitting = false,
                            fieldError = "We could not find a store with that code.",
                        )
                        is ApiException.RateLimited -> current.copy(
                            submitting = false,
                            fieldError = e.retryAfterSeconds
                                ?.let { "Too many tries. Try again in ${it}s." }
                                ?: e.message,
                        )
                        else -> current.copy(submitting = false, fieldError = e.message)
                    }
                }
            }
        }
    }

    /** Consumed by the screen once it has navigated, so Back cannot re-fire it. */
    fun onNavigated() {
        _state.update { it.copy(resolved = null) }
    }
}
