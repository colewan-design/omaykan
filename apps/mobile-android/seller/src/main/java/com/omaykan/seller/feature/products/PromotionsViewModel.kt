package com.omaykan.seller.feature.products

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omaykan.seller.core.data.PromoCodeRepository
import com.omaykan.seller.core.network.ApiException
import com.omaykan.seller.core.network.dto.CreatePromoCodeRequestDto
import com.omaykan.seller.core.network.dto.PromoCodeDto
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** A new code, as typed. Parsed only on create. */
data class PromoForm(
    val code: String = "",
    val percent: Boolean = true,
    val value: String = "",
    val minOrder: String = "",
    val channel: String = "online",
    val maxUses: String = "",
    val perCustomer: String = "1",
)

data class PromotionsUiState(
    val loading: Boolean = true,
    val codes: List<PromoCodeDto> = emptyList(),
    val loadError: String? = null,
    val creating: Boolean = false,
    val form: PromoForm = PromoForm(),
    val saving: Boolean = false,
    val formError: String? = null,
    /** A pause or retire that did not go through, by code id. */
    val rowErrors: Map<String, String> = emptyMap(),
)

@HiltViewModel
class PromotionsViewModel @Inject constructor(
    private val repository: PromoCodeRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(PromotionsUiState())
    val state: StateFlow<PromotionsUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        _state.update { it.copy(loading = true, loadError = null) }
        viewModelScope.launch {
            try {
                val codes = repository.list()
                _state.update { it.copy(loading = false, codes = codes) }
            } catch (e: ApiException) {
                _state.update { it.copy(loading = false, loadError = e.message) }
            }
        }
    }

    fun startCreating() = _state.update { it.copy(creating = true, form = PromoForm(), formError = null) }

    fun cancelCreating() = _state.update { it.copy(creating = false, formError = null) }

    fun edit(change: PromoForm.() -> PromoForm) = _state.update { it.copy(form = it.form.change(), formError = null) }

    fun create() {
        val current = _state.value
        if (current.saving) return
        val form = current.form
        val value = form.value.trim().replace(",", "").toDoubleOrNull()

        val error = when {
            form.code.isBlank() -> "Give the code a name, like WELCOME10."
            !form.code.trim().matches(Regex("[A-Za-z0-9_-]{3,40}")) -> "Letters, numbers, - and _ only, 3 to 40 of them."
            value == null || value <= 0 -> "Enter how much it takes off."
            form.percent && value > 100 -> "A percentage can be at most 100."
            else -> null
        }
        if (error != null) {
            _state.update { it.copy(formError = error) }
            return
        }

        val request = CreatePromoCodeRequestDto(
            code = form.code.trim(),
            kind = if (form.percent) "percent" else "amount",
            percent = if (form.percent) value else null,
            amountCents = if (form.percent) null else Math.round(value!! * 100),
            minSubtotalCents = form.minOrder.trim().replace(",", "").toDoubleOrNull()?.let { Math.round(it * 100) } ?: 0,
            channel = form.channel,
            maxRedemptions = form.maxUses.trim().toIntOrNull(),
            perCustomerLimit = form.perCustomer.trim().toIntOrNull(),
        )

        _state.update { it.copy(saving = true, formError = null) }
        viewModelScope.launch {
            try {
                val created = repository.create(request)
                _state.update { it.copy(saving = false, creating = false, codes = listOf(created) + it.codes) }
            } catch (e: ApiException) {
                val message = (e as? ApiException.Validation)?.errors?.values?.firstOrNull()?.firstOrNull() ?: e.message
                _state.update { it.copy(saving = false, formError = message) }
            }
        }
    }

    fun setActive(code: PromoCodeDto, active: Boolean) = act(code.id) {
        val saved = repository.setActive(code.id, active)
        _state.update { state -> state.copy(codes = state.codes.map { if (it.id == saved.id) saved else it }) }
    }

    fun retire(code: PromoCodeDto) = act(code.id) {
        repository.retire(code.id)
        _state.update { state -> state.copy(codes = state.codes.filterNot { it.id == code.id }) }
    }

    private fun act(id: String, block: suspend () -> Unit) {
        _state.update { it.copy(rowErrors = it.rowErrors - id) }
        viewModelScope.launch {
            try {
                block()
            } catch (e: ApiException) {
                _state.update { it.copy(rowErrors = it.rowErrors + (id to e.message)) }
            }
        }
    }
}
