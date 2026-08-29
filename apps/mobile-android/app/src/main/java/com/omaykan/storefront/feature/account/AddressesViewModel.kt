package com.omaykan.storefront.feature.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omaykan.storefront.core.data.AccountRepository
import com.omaykan.storefront.core.data.AddressDraft
import com.omaykan.storefront.core.model.SavedAddress
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
 * The add/edit form, open over the list.
 *
 * [editingId] null means this is a new address. [lat]/[lng] are carried through
 * an edit untouched rather than being re-derived: they came from a device
 * location fix, and there is no geocoder in this project that could recover
 * them from the text if an edit dropped them.
 */
data class AddressForm(
    val editingId: String? = null,
    val label: String = "",
    val line1: String = "",
    val barangay: String = "",
    val city: String = "",
    val notes: String = "",
    val lat: Double? = null,
    val lng: Double? = null,
    val busy: Boolean = false,
    val fieldErrors: Map<String, String> = emptyMap(),
    val error: String? = null,
) {
    val pinned: Boolean get() = lat != null && lng != null

    val isNew: Boolean get() = editingId == null

    val canSave: Boolean
        get() = !busy && label.isNotBlank() && line1.isNotBlank() && city.isNotBlank()

    fun toDraft() = AddressDraft(
        label = label,
        line1 = line1,
        barangay = barangay,
        city = city,
        notes = notes,
        lat = lat,
        lng = lng,
    )
}

data class AddressesUiState(
    val loaded: Boolean = false,
    val addresses: List<SavedAddress> = emptyList(),
    val form: AddressForm? = null,
    /** The row a delete is being confirmed for. */
    val confirmingDelete: SavedAddress? = null,
    /** Ids with a write in flight — the row shows it, the list stays usable. */
    val working: Set<String> = emptySet(),
    val error: String? = null,
    val notice: String? = null,
)

/**
 * The shopper's saved delivery addresses.
 *
 * These matter more on a phone than the list length suggests: a saved address
 * can carry a pin, and a pin is the difference between the server quoting a
 * real delivery fee and falling back to the shop's flat rate. Typed text never
 * yields one — there is no geocoder anywhere in this project — so the only
 * addresses that ever carry coordinates are ones saved from a device location.
 */
@HiltViewModel
class AddressesViewModel @Inject constructor(
    private val repository: AccountRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(AddressesUiState())
    val state: StateFlow<AddressesUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            repository.session.collect { session ->
                val account = (session as? SessionState.SignedIn)?.account ?: return@collect
                _state.update { it.copy(loaded = true, addresses = account.addresses) }
            }
        }
    }

    fun addAddress() = _state.update { it.copy(form = AddressForm()) }

    fun editAddress(address: SavedAddress) = _state.update {
        it.copy(
            form = AddressForm(
                editingId = address.id,
                label = address.label,
                line1 = address.line1,
                barangay = address.barangay,
                city = address.city,
                notes = address.notes,
                lat = address.lat,
                lng = address.lng,
            ),
        )
    }

    fun closeForm() = _state.update { it.copy(form = null) }

    fun onFormChange(block: (AddressForm) -> AddressForm) = _state.update { current ->
        val form = current.form ?: return@update current
        current.copy(form = block(form).copy(error = null))
    }

    /**
     * Drops the pin off an address being edited.
     *
     * Offered because the alternative is worse: an address whose text has been
     * corrected to a different street but whose coordinates still point at the
     * old one quotes a fee for a journey nobody is making. Clearing is honest —
     * the shop falls back to its flat rate, which is what an unpinned address
     * gets anyway.
     */
    fun clearPin() = onFormChange { it.copy(lat = null, lng = null) }

    fun saveForm() {
        val form = _state.value.form ?: return
        if (!form.canSave) return

        _state.update { it.copy(form = form.copy(busy = true, error = null, fieldErrors = emptyMap())) }

        viewModelScope.launch {
            try {
                if (form.isNew) {
                    repository.saveAddress(form.toDraft())
                } else {
                    repository.updateAddress(form.editingId!!, form.toDraft())
                }
                _state.update {
                    it.copy(
                        form = null,
                        notice = if (form.isNew) "Address saved." else "Address updated.",
                    )
                }
            } catch (e: ApiException.Validation) {
                _state.update { current ->
                    current.copy(
                        form = current.form?.copy(
                            busy = false,
                            fieldErrors = e.errors.mapValues { (_, messages) -> messages.first() },
                            error = if (e.errors.isEmpty()) e.message else null,
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

    fun makeDefault(address: SavedAddress) {
        if (address.isDefault) return

        write(address.id) { repository.makeAddressDefault(address.id) }
    }

    fun confirmDelete(address: SavedAddress) = _state.update { it.copy(confirmingDelete = address) }

    fun cancelDelete() = _state.update { it.copy(confirmingDelete = null) }

    fun deleteConfirmed() {
        val address = _state.value.confirmingDelete ?: return
        _state.update { it.copy(confirmingDelete = null) }

        write(address.id, notice = "Address removed.") { repository.deleteAddress(address.id) }
    }

    fun dismissError() = _state.update { it.copy(error = null) }

    fun onNoticeShown() = _state.update { it.copy(notice = null) }

    /**
     * One in-place write against one row.
     *
     * The id goes into `working` rather than a screen-wide spinner: the rest of
     * the list stays readable and tappable while one row is being promoted or
     * removed, which is the whole difference between a list and a form.
     */
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
