package com.omaykan.storefront.feature.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omaykan.storefront.core.data.AccountRepository
import com.omaykan.storefront.core.model.PaymentPreference
import com.omaykan.storefront.core.model.SavedPaymentMethod
import com.omaykan.storefront.core.model.SessionState
import com.omaykan.storefront.core.network.ApiException
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The add form. There is no edit: a payment method is a kind and a number, and
 * changing either is a different method.
 */
data class PaymentMethodForm(
    val kind: PaymentPreference = PaymentPreference.Cash,
    val detail: String = "",
    val busy: Boolean = false,
    val fieldErrors: Map<String, String> = emptyMap(),
    val error: String? = null,
) {
    /** An e-wallet is a number; cash is not. */
    val needsDetail: Boolean get() = kind != PaymentPreference.Cash

    val canSave: Boolean get() = !busy && (!needsDetail || detail.isNotBlank())
}

data class PaymentMethodsUiState(
    val loaded: Boolean = false,
    val methods: List<SavedPaymentMethod> = emptyList(),
    val form: PaymentMethodForm? = null,
    val confirmingDelete: SavedPaymentMethod? = null,
    val working: Set<String> = emptySet(),
    val error: String? = null,
    val notice: String? = null,
) {
    /**
     * Cash is a single row by nature — it carries no detail, so a second one
     * would be an exact duplicate, and the server refuses it with a 422. Better
     * to grey the option out than to let someone find that out by tapping Save.
     */
    val cashAlreadySaved: Boolean
        get() = methods.any { it.kind == PaymentPreference.Cash }
}

/**
 * What the shopper means to pay with.
 *
 * A preference and nothing more: there is no processor behind any of this, and
 * nothing in this app charges anything. Payment is cash or an e-wallet handed
 * over at the door — see plan.md §4a — so what is saved here is a note to the
 * merchant and a prefill for checkout.
 */
@HiltViewModel
class PaymentMethodsViewModel @Inject constructor(
    private val repository: AccountRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(PaymentMethodsUiState())
    val state: StateFlow<PaymentMethodsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            repository.session.collect { session ->
                val account = (session as? SessionState.SignedIn)?.account ?: return@collect
                _state.update { it.copy(loaded = true, methods = account.paymentMethods) }
            }
        }
    }

    fun addMethod() = _state.update {
        // Opens on whichever option is still available, so the common case is
        // one tap rather than two.
        it.copy(
            form = PaymentMethodForm(
                kind = if (it.cashAlreadySaved) PaymentPreference.Ewallet else PaymentPreference.Cash,
            ),
        )
    }

    fun closeForm() = _state.update { it.copy(form = null) }

    fun onKind(kind: PaymentPreference) = _state.update { current ->
        val form = current.form ?: return@update current
        current.copy(form = form.copy(kind = kind, error = null, fieldErrors = emptyMap()))
    }

    fun onDetail(value: String) = _state.update { current ->
        val form = current.form ?: return@update current
        current.copy(form = form.copy(detail = value, error = null, fieldErrors = form.fieldErrors - "detail"))
    }

    fun saveForm() {
        val form = _state.value.form ?: return
        if (!form.canSave) return

        _state.update { it.copy(form = form.copy(busy = true, error = null, fieldErrors = emptyMap())) }

        viewModelScope.launch {
            try {
                repository.savePaymentMethod(form.kind, form.detail)
                _state.update { it.copy(form = null, notice = "Payment method saved.") }
            } catch (e: ApiException.Validation) {
                _state.update { current ->
                    current.copy(
                        form = current.form?.copy(
                            busy = false,
                            fieldErrors = e.errors.mapValues { (_, messages) -> messages.first() },
                            error = e.errors["kind"]?.firstOrNull()
                                ?: if (e.errors.isEmpty()) e.message else null,
                        ),
                    )
                }
            } catch (e: ApiException) {
                _state.update { current ->
                    current.copy(form = current.form?.copy(busy = false, error = e.message))
                }
            }
        }
    }

    fun makeDefault(method: SavedPaymentMethod) {
        if (method.isDefault) return

        write(method.id) { repository.makePaymentMethodDefault(method.id) }
    }

    fun confirmDelete(method: SavedPaymentMethod) = _state.update { it.copy(confirmingDelete = method) }

    fun cancelDelete() = _state.update { it.copy(confirmingDelete = null) }

    fun deleteConfirmed() {
        val method = _state.value.confirmingDelete ?: return
        _state.update { it.copy(confirmingDelete = null) }

        write(method.id, notice = "Payment method removed.") { repository.deletePaymentMethod(method.id) }
    }

    fun onNoticeShown() = _state.update { it.copy(notice = null) }

    private fun write(id: String, notice: String? = null, block: suspend () -> Unit) {
        _state.update { it.copy(working = it.working + id, error = null) }

        viewModelScope.launch {
            try {
                block()
                _state.update { it.copy(working = it.working - id, notice = notice) }
            } catch (e: ApiException) {
                _state.update { it.copy(working = it.working - id, error = e.message) }
            }
        }
    }
}
