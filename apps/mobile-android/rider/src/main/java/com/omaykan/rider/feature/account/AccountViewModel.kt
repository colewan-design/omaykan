package com.omaykan.rider.feature.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omaykan.rider.core.data.AccountRepository
import com.omaykan.rider.core.data.DocumentUpload
import com.omaykan.rider.core.data.SessionRepository
import com.omaykan.rider.core.model.RiderProfile
import com.omaykan.rider.core.model.VehicleType
import com.omaykan.rider.core.network.ApiException
import com.omaykan.rider.core.network.dto.RatingsDto
import com.omaykan.rider.core.network.dto.SupportDto
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Two forms, two independent states.
 *
 * They are two different acts: saving a phone number should not ask for a
 * password, and changing a password should not quietly save a half-edited name
 * alongside it. One combined "saving" flag would make each button spin for the
 * other's work.
 */
data class AccountUiState(
    val name: String = "",
    val phone: String = "",
    val plateNumber: String = "",
    val savingDetails: Boolean = false,
    val detailsSaved: Boolean = false,
    val fieldErrors: Map<String, String> = emptyMap(),
    val detailsError: String? = null,

    // -- the bike ---------------------------------------------------------
    val vehicleType: VehicleType = VehicleType.Motorcycle,
    val vehicleMake: String = "",
    val vehicleModel: String = "",
    val vehicleColor: String = "",
    val savingVehicle: Boolean = false,
    val vehicleSaved: Boolean = false,
    val vehicleError: String? = null,

    // -- the photograph ---------------------------------------------------
    val savingPhoto: Boolean = false,
    val photoError: String? = null,

    // -- what customers said ----------------------------------------------
    val ratingsLoading: Boolean = false,
    val ratings: RatingsDto? = null,
    val ratingsError: String? = null,

    // -- reaching a human --------------------------------------------------
    val support: SupportDto? = null,

    val currentPassword: String = "",
    val newPassword: String = "",
    val confirmPassword: String = "",
    val savingPassword: Boolean = false,
    val passwordChanged: Boolean = false,
    val passwordFieldError: String? = null,
    val passwordError: String? = null,
) {
    val canSaveDetails: Boolean
        get() = !savingDetails && name.isNotBlank() && phone.isNotBlank() && plateNumber.isNotBlank()

    /** Type always has a value, so the only thing that blocks saving is a save. */
    val canSaveVehicle: Boolean get() = !savingVehicle

    val canSavePassword: Boolean
        get() = !savingPassword &&
            currentPassword.isNotBlank() &&
            newPassword.length >= MIN_PASSWORD &&
            confirmPassword.isNotBlank()

    companion object {
        /** `Password::min(8)` on the server. Matched here so the button says so first. */
        const val MIN_PASSWORD = 8
    }
}

/**
 * The rider's own account.
 *
 * Until this existed nothing anywhere could change a single field on a rider
 * after registration — not this app, not the portal, and not an operator, who
 * has a review screen and no edit screen. A new phone number meant a database
 * edit.
 *
 * Seeded from the session rather than from a fetch. The profile is already in
 * `RiderSessionStore` — it is what the canopy renders the rider's name from —
 * and a screen that re-fetched it would show an empty form for one frame on a
 * slow connection for no gain.
 */
@HiltViewModel
class AccountViewModel @Inject constructor(
    private val accounts: AccountRepository,
    private val sessions: SessionRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(AccountUiState())
    val state: StateFlow<AccountUiState> = _state.asStateFlow()

    /**
     * Called by the screen with the profile the session already holds.
     *
     * Only while the fields are untouched: this runs again on every
     * recomposition that changes the rider, and a save answers with a new
     * profile — re-seeding then would be fine, but re-seeding *mid-edit*, when
     * a suspension arrives and rewrites the stored rider, would wipe what
     * somebody is typing.
     */
    fun seed(rider: RiderProfile) {
        _state.update { current ->
            if (current.name.isNotEmpty() || current.savingDetails) {
                current
            } else {
                current.copy(
                    name = rider.name,
                    phone = rider.phone,
                    plateNumber = rider.plateNumber,
                    vehicleType = rider.vehicle.type,
                    vehicleMake = rider.vehicle.make.orEmpty(),
                    vehicleModel = rider.vehicle.model.orEmpty(),
                    vehicleColor = rider.vehicle.color.orEmpty(),
                )
            }
        }
    }

    fun onNameChange(value: String) = _state.update {
        it.copy(name = value, detailsSaved = false, detailsError = null, fieldErrors = emptyMap())
    }

    fun onPhoneChange(value: String) = _state.update {
        it.copy(phone = value, detailsSaved = false, detailsError = null, fieldErrors = emptyMap())
    }

    fun onPlateChange(value: String) = _state.update {
        it.copy(plateNumber = value, detailsSaved = false, detailsError = null, fieldErrors = emptyMap())
    }

    fun onCurrentPasswordChange(value: String) = _state.update {
        it.copy(currentPassword = value, passwordFieldError = null, passwordError = null, passwordChanged = false)
    }

    fun onNewPasswordChange(value: String) = _state.update {
        it.copy(newPassword = value, passwordFieldError = null, passwordError = null, passwordChanged = false)
    }

    fun onConfirmPasswordChange(value: String) = _state.update {
        it.copy(confirmPassword = value, passwordFieldError = null, passwordError = null, passwordChanged = false)
    }

    fun saveDetails() {
        val current = _state.value
        if (!current.canSaveDetails) return

        _state.update {
            it.copy(savingDetails = true, detailsError = null, detailsSaved = false, fieldErrors = emptyMap())
        }

        viewModelScope.launch {
            try {
                val saved = accounts.updateProfile(
                    name = current.name.trim(),
                    phone = current.phone.trim(),
                    plateNumber = current.plateNumber.trim(),
                )

                // Re-seeded from the answer, not from what was typed: the server
                // upper-cases the plate, and a field still showing the
                // lower-case version would disagree with the shop's screen.
                _state.update {
                    it.copy(
                        name = saved.name,
                        phone = saved.phone,
                        plateNumber = saved.plateNumber,
                        savingDetails = false,
                        detailsSaved = true,
                    )
                }
            } catch (e: ApiException) {
                if (sessions.applyGate(e)) return@launch

                _state.update {
                    it.copy(
                        savingDetails = false,
                        fieldErrors = (e as? ApiException.Validation)?.fieldMessages().orEmpty(),
                        detailsError = if (e is ApiException.Validation) null else e.message,
                    )
                }
            }
        }
    }

    fun savePassword() {
        val current = _state.value
        if (!current.canSavePassword) return

        // Checked here rather than at the server, because the server cannot
        // check it: `confirmed` compares two fields this app sends as one, and
        // sending a mismatch would silently set the password to the first.
        if (current.newPassword != current.confirmPassword) {
            _state.update { it.copy(passwordError = "The two new passwords do not match.") }
            return
        }

        _state.update {
            it.copy(savingPassword = true, passwordError = null, passwordFieldError = null, passwordChanged = false)
        }

        viewModelScope.launch {
            try {
                accounts.changePassword(current.currentPassword, current.newPassword)

                _state.update {
                    it.copy(
                        currentPassword = "",
                        newPassword = "",
                        confirmPassword = "",
                        savingPassword = false,
                        passwordChanged = true,
                    )
                }
            } catch (e: ApiException) {
                if (sessions.applyGate(e)) return@launch

                _state.update {
                    it.copy(
                        savingPassword = false,
                        // The server's own sentence, under the field it named.
                        // "That is not your current password" belongs on the
                        // current-password box, not at the top of the form.
                        passwordFieldError = (e as? ApiException.Validation)?.first("currentPassword"),
                        passwordError = when {
                            e !is ApiException.Validation -> e.message
                            e.first("currentPassword") != null -> null
                            else -> e.first("password") ?: e.message
                        },
                    )
                }
            }
        }
    }

    /** The first message per field, in the shape the text fields read. */
    private fun ApiException.Validation.fieldMessages(): Map<String, String> =
        listOf("name", "phone", "plateNumber").mapNotNull { field ->
            first(field)?.let { field to it }
        }.toMap()

    // -- The bike -----------------------------------------------------------

    fun onVehicleTypeChange(value: VehicleType) = _state.update {
        it.copy(vehicleType = value, vehicleSaved = false, vehicleError = null)
    }

    fun onVehicleMakeChange(value: String) = _state.update {
        it.copy(vehicleMake = value, vehicleSaved = false, vehicleError = null)
    }

    fun onVehicleModelChange(value: String) = _state.update {
        it.copy(vehicleModel = value, vehicleSaved = false, vehicleError = null)
    }

    fun onVehicleColorChange(value: String) = _state.update {
        it.copy(vehicleColor = value, vehicleSaved = false, vehicleError = null)
    }

    /**
     * Save the bike.
     *
     * The three descriptive fields are sent trimmed and **not** null-coalesced:
     * an empty one has to reach the server as `""` so that a rider who
     * repainted a bike can remove "red" rather than only replace it. See
     * ProfileUpdateRequestDto.
     */
    fun saveVehicle() {
        val current = _state.value
        if (!current.canSaveVehicle) return

        _state.update { it.copy(savingVehicle = true, vehicleError = null, vehicleSaved = false) }

        viewModelScope.launch {
            try {
                val saved = accounts.updateProfile(
                    vehicleType = current.vehicleType,
                    vehicleMake = current.vehicleMake.trim(),
                    vehicleModel = current.vehicleModel.trim(),
                    vehicleColor = current.vehicleColor.trim(),
                )

                _state.update {
                    it.copy(
                        vehicleType = saved.vehicle.type,
                        vehicleMake = saved.vehicle.make.orEmpty(),
                        vehicleModel = saved.vehicle.model.orEmpty(),
                        vehicleColor = saved.vehicle.color.orEmpty(),
                        savingVehicle = false,
                        vehicleSaved = true,
                    )
                }
            } catch (e: ApiException) {
                if (sessions.applyGate(e)) return@launch

                _state.update { it.copy(savingVehicle = false, vehicleError = e.message) }
            }
        }
    }

    // -- The photograph -----------------------------------------------------

    /**
     * Upload a picked photo.
     *
     * The picked file is read on the caller's side, so what arrives here is
     * already bytes — this never touches a `Uri`, and therefore never holds a
     * grant that can be revoked underneath it mid-upload.
     */
    fun uploadPhoto(upload: DocumentUpload) {
        if (_state.value.savingPhoto) return

        _state.update { it.copy(savingPhoto = true, photoError = null) }

        viewModelScope.launch {
            try {
                accounts.uploadPhoto(upload)
                _state.update { it.copy(savingPhoto = false) }
            } catch (e: ApiException) {
                if (sessions.applyGate(e)) return@launch

                _state.update { it.copy(savingPhoto = false, photoError = e.message) }
            }
        }
    }

    /** Report a file the picker could not give us, without spending a request. */
    fun onPhotoUnreadable(message: String) = _state.update {
        it.copy(savingPhoto = false, photoError = message)
    }

    fun removePhoto() {
        if (_state.value.savingPhoto) return

        _state.update { it.copy(savingPhoto = true, photoError = null) }

        viewModelScope.launch {
            try {
                accounts.removePhoto()
                _state.update { it.copy(savingPhoto = false) }
            } catch (e: ApiException) {
                if (sessions.applyGate(e)) return@launch

                _state.update { it.copy(savingPhoto = false, photoError = e.message) }
            }
        }
    }

    // -- Ratings and support -------------------------------------------------

    /**
     * Load the scores, once per visit to that panel.
     *
     * Behind the approval gate server-side, so a gated rider meets a 403 —
     * `applyGate` turns that into the status screen rather than an error
     * message on a panel they should not be looking at.
     */
    fun loadRatings() {
        if (_state.value.ratingsLoading) return

        _state.update { it.copy(ratingsLoading = true, ratingsError = null) }

        viewModelScope.launch {
            try {
                val ratings = accounts.ratings()
                _state.update { it.copy(ratingsLoading = false, ratings = ratings) }
            } catch (e: ApiException) {
                if (sessions.applyGate(e)) return@launch

                _state.update { it.copy(ratingsLoading = false, ratingsError = e.message) }
            }
        }
    }

    /**
     * Load the support contacts.
     *
     * Failure is deliberately silent: the screen already prints the one thing
     * that is true without the network — that a rider can write to the address
     * on the platform's own site — and an error banner over a help panel is
     * the least useful place in the app to put one.
     */
    fun loadSupport() {
        if (_state.value.support != null) return

        viewModelScope.launch {
            runCatching { accounts.support() }
                .onSuccess { support -> _state.update { it.copy(support = support) } }
        }
    }

}
