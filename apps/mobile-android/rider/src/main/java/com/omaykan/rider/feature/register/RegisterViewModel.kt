package com.omaykan.rider.feature.register

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omaykan.rider.core.data.DocumentUpload
import com.omaykan.rider.core.data.Registration
import com.omaykan.rider.core.data.SessionRepository
import com.omaykan.rider.core.model.VehicleType
import com.omaykan.rider.core.network.ApiException
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** One of the two photographs, once the rider has chosen it. */
data class ChosenDocument(
    val uri: Uri,
    val upload: DocumentUpload,
) {
    /** "licence.jpg · 1.4 MB" — enough to see the right file was picked. */
    val label: String
        get() {
            val mb = upload.bytes.size / 1_048_576.0
            val size = if (mb < 0.1) "under 0.1 MB" else String.format("%.1f MB", mb)
            return "${upload.fileName} · $size"
        }
}

data class RegisterUiState(
    val name: String = "",
    val email: String = "",
    val phone: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    val licenseNumber: String = "",
    val plateNumber: String = "",
    /**
     * The bike, asked for at sign-up so the first shop this rider collects from
     * already knows what to look for. Type defaults rather than starting empty:
     * a required selection with no default is one more thing between somebody
     * at a junction and a job board.
     */
    val vehicleType: VehicleType = VehicleType.Motorcycle,
    val vehicleMake: String = "",
    val vehicleModel: String = "",
    val vehicleColor: String = "",
    val licenseImage: ChosenDocument? = null,
    val plateImage: ChosenDocument? = null,
    val submitting: Boolean = false,
    /** Keyed by the API's field names, so a 422 maps straight onto the form. */
    val fieldErrors: Map<String, String> = emptyMap(),
    val error: String? = null,
) {
    /**
     * Checked here as well as on the server, because it is the one rule the
     * server states in a way that would be baffling on a form: `confirmed`
     * fails as "The password field confirmation does not match", attached to
     * the *password*, after a rider has waited for two photographs to upload.
     */
    val passwordsMatch: Boolean
        get() = confirmPassword.isEmpty() || password == confirmPassword

    val canSubmit: Boolean
        get() = !submitting &&
            name.isNotBlank() &&
            email.isNotBlank() &&
            phone.isNotBlank() &&
            password.length >= MIN_PASSWORD &&
            password == confirmPassword &&
            licenseNumber.isNotBlank() &&
            plateNumber.isNotBlank() &&
            licenseImage != null &&
            plateImage != null

    companion object {
        /** `PasswordRule::min(8)` on the server. Mirrored, not guessed. */
        const val MIN_PASSWORD = 8
    }
}

/**
 * Applying to ride.
 *
 * Eight fields and two photographs, against an endpoint throttled to four
 * attempts a minute — the hardest limit on the public API, because it creates
 * an account *and* accepts two uploads. That budget is why [canSubmit] mirrors
 * every rule the server states: a rider on a bad connection should not spend
 * one of four tries discovering that their passwords disagree.
 *
 * What is not mirrored is anything the server knows and the app cannot: whether
 * the email is taken, and whether the files really decode as images. Those come
 * back as a 422 with per-field messages, and they land under the field they
 * name.
 */
@HiltViewModel
class RegisterViewModel @Inject constructor(
    private val sessions: SessionRepository,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private companion object {
        /**
         * 8MB, matching `RiderAuthController::MAX_IMAGE_KB`.
         *
         * Checked before the upload rather than after it. A modern phone camera
         * clears this comfortably, but a rider who picks a screenshot of a
         * screenshot from a gallery app should be told in the second it takes
         * to read the file, not after ninety seconds of pushing it up a mobile
         * connection to be refused.
         */
        const val MAX_BYTES = 8L * 1024 * 1024
    }

    private val _state = MutableStateFlow(RegisterUiState())
    val state: StateFlow<RegisterUiState> = _state.asStateFlow()

    fun onNameChange(value: String) = edit("name") { it.copy(name = value) }

    fun onEmailChange(value: String) = edit("email") { it.copy(email = value) }

    fun onPhoneChange(value: String) = edit("phone") { it.copy(phone = value) }

    fun onPasswordChange(value: String) = edit("password") { it.copy(password = value) }

    fun onConfirmPasswordChange(value: String) =
        edit("password") { it.copy(confirmPassword = value) }

    fun onLicenseNumberChange(value: String) =
        edit("licenseNumber") { it.copy(licenseNumber = value) }

    fun onPlateNumberChange(value: String) = edit("plateNumber") { it.copy(plateNumber = value) }

    fun onVehicleTypeChange(value: VehicleType) = edit("vehicleType") { it.copy(vehicleType = value) }

    fun onVehicleMakeChange(value: String) = edit("vehicleMake") { it.copy(vehicleMake = value) }

    fun onVehicleModelChange(value: String) = edit("vehicleModel") { it.copy(vehicleModel = value) }

    fun onVehicleColorChange(value: String) = edit("vehicleColor") { it.copy(vehicleColor = value) }

    /**
     * Read a picked photo into memory.
     *
     * The whole file, held as a ByteArray until it is uploaded. That is
     * deliberate for an 8MB ceiling and two files: the alternative — streaming
     * from the content URI at request time — would mean holding a permission
     * grant across a screen rotation and re-opening a stream OkHttp may retry
     * against, and a `Uri` whose provider has since been revoked fails inside
     * the call with nothing useful to say. Read once, fail early, upload bytes.
     */
    fun onDocumentPicked(field: String, uri: Uri?) {
        if (uri == null) return

        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { readDocument(uri) }

            _state.update { current ->
                when (result) {
                    is DocumentResult.TooBig -> current.withFieldError(
                        field,
                        "That image is ${result.megabytes} MB. The limit is 8 MB — " +
                            "take a new photo rather than sending a file from your gallery.",
                    )
                    is DocumentResult.Unreadable -> current.withFieldError(
                        field,
                        "That file could not be opened. Try picking it again.",
                    )
                    is DocumentResult.Ok -> {
                        val chosen = ChosenDocument(uri, result.upload)
                        val cleared = current.copy(
                            fieldErrors = current.fieldErrors - field,
                            error = null,
                        )

                        if (field == "licenseImage") {
                            cleared.copy(licenseImage = chosen)
                        } else {
                            cleared.copy(plateImage = chosen)
                        }
                    }
                }
            }
        }
    }

    fun submit() {
        val current = _state.value
        val license = current.licenseImage ?: return
        val plate = current.plateImage ?: return
        if (!current.canSubmit) return

        _state.update { it.copy(submitting = true, fieldErrors = emptyMap(), error = null) }

        viewModelScope.launch {
            try {
                sessions.register(
                    Registration(
                        name = current.name,
                        email = current.email,
                        phone = current.phone,
                        password = current.password,
                        licenseNumber = current.licenseNumber,
                        plateNumber = current.plateNumber,
                        vehicleType = current.vehicleType,
                        vehicleMake = current.vehicleMake,
                        vehicleModel = current.vehicleModel,
                        vehicleColor = current.vehicleColor,
                        licenseImage = license.upload,
                        plateImage = plate.upload,
                    ),
                )
                // No navigation and no success state. Registering writes a
                // token and a pending profile, the session flow turns Gated,
                // and MainActivity replaces this screen with the status one —
                // which is the honest next thing to show somebody who has just
                // applied.
            } catch (e: ApiException) {
                _state.update { it.withFailure(e) }
            }
        }
    }

    private fun edit(field: String, block: (RegisterUiState) -> RegisterUiState) =
        _state.update { block(it).copy(fieldErrors = it.fieldErrors - field, error = null) }

    private fun RegisterUiState.withFieldError(field: String, message: String) =
        copy(fieldErrors = fieldErrors + (field to message))

    private fun RegisterUiState.withFailure(e: ApiException): RegisterUiState {
        val base = copy(submitting = false)

        return when (e) {
            /*
             * The server's per-field messages, straight onto the fields.
             *
             * This is the reason ApiException.Validation carries the whole map
             * rather than a flattened first message the way the storefront's
             * error does: eight fields and two files means "The plate image
             * must be an image" has to land under the plate image, not at the
             * top of a form the rider then has to re-read.
             */
            is ApiException.Validation -> base.copy(
                fieldErrors = e.errors.mapNotNull { (field, messages) ->
                    messages.firstOrNull()?.let { field to it }
                }.toMap(),
                // The banner only when nothing landed on a field, so a form
                // full of red is not also shouting at the top.
                error = if (e.errors.isEmpty()) e.message else null,
            )
            // 4/min, and a rider fixing a typo can reach it in under a minute.
            is ApiException.RateLimited -> base.copy(error = e.message)
            else -> base.copy(error = e.message)
        }
    }

    private sealed interface DocumentResult {
        data class Ok(val upload: DocumentUpload) : DocumentResult

        data class TooBig(val megabytes: String) : DocumentResult

        data object Unreadable : DocumentResult
    }

    private fun readDocument(uri: Uri): DocumentResult = try {
        val resolver = context.contentResolver
        val size = resolver.sizeOf(uri)

        when {
            size != null && size > MAX_BYTES ->
                DocumentResult.TooBig(String.format("%.1f", size / 1_048_576.0))

            else -> {
                val bytes = resolver.openInputStream(uri)?.use { it.readBytes() }

                when {
                    bytes == null -> DocumentResult.Unreadable
                    // Checked again on the bytes, because a provider that
                    // reports no size — plenty do — has just been waved
                    // through the check above.
                    bytes.size > MAX_BYTES ->
                        DocumentResult.TooBig(String.format("%.1f", bytes.size / 1_048_576.0))

                    else -> DocumentResult.Ok(
                        DocumentUpload(
                            bytes = bytes,
                            // The server validates the decoded image and
                            // accepts jpeg, png and webp; a provider that
                            // reports nothing gets jpeg, which is what a phone
                            // camera produces.
                            mimeType = resolver.getType(uri) ?: "image/jpeg",
                            fileName = resolver.nameOf(uri) ?: "document.jpg",
                        ),
                    )
                }
            }
        }
    } catch (e: Exception) {
        // A revoked grant, a provider that has gone away, a file on an
        // unmounted SD card. All of them mean the same thing to the rider:
        // pick it again.
        DocumentResult.Unreadable
    }
}

private fun android.content.ContentResolver.sizeOf(uri: Uri): Long? =
    query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
        val column = cursor.getColumnIndex(OpenableColumns.SIZE)
        if (column >= 0 && cursor.moveToFirst() && !cursor.isNull(column)) {
            cursor.getLong(column)
        } else {
            null
        }
    }

/**
 * The display name the provider reports, never used as a path.
 *
 * It reaches the server as the multipart filename and the server throws it
 * away — `RiderAuthController::storeDocument` names the file by a random id
 * precisely because an uploader-supplied name is attacker-chosen text. It is
 * here so the rider can see which file they picked.
 */
private fun android.content.ContentResolver.nameOf(uri: Uri): String? =
    query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        val column = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (column >= 0 && cursor.moveToFirst()) cursor.getString(column) else null
    }
