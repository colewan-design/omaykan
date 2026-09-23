package com.omaykan.rider.feature.register

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.omaykan.rider.core.designsystem.Canopy
import com.omaykan.rider.core.designsystem.CanopyIconButton
import com.omaykan.rider.core.designsystem.IconBadge
import com.omaykan.rider.core.designsystem.PrimaryButton
import com.omaykan.rider.core.designsystem.RiderCard
import com.omaykan.rider.core.designsystem.RiderTextField
import com.omaykan.rider.core.designsystem.RiderTheme
import com.omaykan.rider.core.designsystem.SectionLabel
import com.omaykan.rider.core.designsystem.PillShape
import com.omaykan.rider.core.model.VehicleType
import com.omaykan.rider.feature.common.Notice

/**
 * Applying to ride: eight fields and two photographs.
 *
 * Long, and deliberately not split into steps. A wizard would hide the licence
 * and plate photos behind a "next", and those two are the reason the form
 * exists — somebody who does not have them to hand should find that out on the
 * first screen, not on the third.
 *
 * What the redesign does instead of a wizard is group the one list into three
 * cards with a label over each. The form is exactly as long as it was and
 * nothing is hidden; a rider can simply see, from one scroll, that there are
 * three things being asked for rather than eleven — and the two photographs are
 * a whole card of their own rather than the tail of a list.
 */
@Composable
fun RegisterScreen(
    onBack: () -> Unit,
    viewModel: RegisterViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // The system back gesture means the same thing as the arrow. Disabled while
    // an upload is in the air: leaving then would abandon a request that is
    // about to create an account, and the rider would come back to a sign-in
    // screen for an email the server now considers taken.
    BackHandler(enabled = !state.submitting, onBack = onBack)

    // Canopy fixed, form scrolling — see SignInScreen for why.
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .imePadding(),
    ) {
        Canopy {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CanopyIconButton(
                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                    description = "Back to sign in",
                    onClick = onBack,
                    enabled = !state.submitting,
                )
                Text(
                    text = "Apply to ride",
                    style = MaterialTheme.typography.headlineMedium,
                    color = RiderTheme.colors.onCanopy,
                    modifier = Modifier.padding(start = 12.dp),
                )
            }

            Text(
                text = "Someone checks your licence and plate before you can take jobs. " +
                    "You can sign in and watch for the answer in the meantime.",
                style = MaterialTheme.typography.bodyMedium,
                color = RiderTheme.colors.onCanopyMuted,
                modifier = Modifier.padding(top = 8.dp, bottom = 28.dp),
            )
        }

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SectionLabel("About you")

            RiderCard {
                Field(
                    value = state.name,
                    onChange = viewModel::onNameChange,
                    label = "Full name",
                    error = state.fieldErrors["name"],
                    enabled = !state.submitting,
                    capitalization = KeyboardCapitalization.Words,
                )

                Field(
                    value = state.email,
                    onChange = viewModel::onEmailChange,
                    label = "Email",
                    error = state.fieldErrors["email"],
                    enabled = !state.submitting,
                    keyboardType = KeyboardType.Email,
                )

                Field(
                    value = state.phone,
                    onChange = viewModel::onPhoneChange,
                    label = "Mobile number",
                    error = state.fieldErrors["phone"],
                    enabled = !state.submitting,
                    keyboardType = KeyboardType.Phone,
                    // The number a shop rings when an order is late, and the
                    // one the customer sees while the delivery is in flight.
                    // Worth saying.
                    hint = "Shops and customers see this while you are carrying an order.",
                )

                Field(
                    value = state.password,
                    onChange = viewModel::onPasswordChange,
                    label = "Password",
                    error = state.fieldErrors["password"],
                    enabled = !state.submitting,
                    keyboardType = KeyboardType.Password,
                    masked = true,
                    hint = "At least ${RegisterUiState.MIN_PASSWORD} characters.",
                )

                Field(
                    value = state.confirmPassword,
                    onChange = viewModel::onConfirmPasswordChange,
                    label = "Repeat password",
                    // Checked on the phone rather than left to the server's
                    // `confirmed` rule, which would spend one of four tries a
                    // minute and two photograph uploads to say the same thing.
                    error = if (state.passwordsMatch) null else "The two passwords do not match.",
                    enabled = !state.submitting,
                    keyboardType = KeyboardType.Password,
                    masked = true,
                    last = true,
                )
            }

            SectionLabel("Your bike", modifier = Modifier.padding(top = 8.dp))

            RiderCard {
                Field(
                    value = state.licenseNumber,
                    onChange = viewModel::onLicenseNumberChange,
                    label = "Driver's licence number",
                    error = state.fieldErrors["licenseNumber"],
                    enabled = !state.submitting,
                    capitalization = KeyboardCapitalization.Characters,
                )

                Field(
                    value = state.plateNumber,
                    onChange = viewModel::onPlateNumberChange,
                    label = "Plate number",
                    error = state.fieldErrors["plateNumber"],
                    enabled = !state.submitting,
                    capitalization = KeyboardCapitalization.Characters,
                )

                RegisterVehicleTypePicker(
                    selected = state.vehicleType,
                    enabled = !state.submitting,
                    onSelect = viewModel::onVehicleTypeChange,
                )

                Field(
                    value = state.vehicleColor,
                    onChange = viewModel::onVehicleColorChange,
                    label = "Colour (optional)",
                    error = state.fieldErrors["vehicleColor"],
                    enabled = !state.submitting,
                )

                Field(
                    value = state.vehicleMake,
                    onChange = viewModel::onVehicleMakeChange,
                    label = "Make (optional)",
                    error = state.fieldErrors["vehicleMake"],
                    enabled = !state.submitting,
                )

                Field(
                    value = state.vehicleModel,
                    onChange = viewModel::onVehicleModelChange,
                    label = "Model (optional)",
                    error = state.fieldErrors["vehicleModel"],
                    enabled = !state.submitting,
                    imeAction = ImeAction.Done,
                    last = true,
                )
            }

            SectionLabel("Proof", modifier = Modifier.padding(top = 8.dp))

            RiderCard(padding = 8.dp) {
                DocumentPicker(
                    title = "Photo of your licence",
                    subtitle = "Both the number and your face have to be readable.",
                    chosen = state.licenseImage?.label,
                    error = state.fieldErrors["licenseImage"],
                    enabled = !state.submitting,
                    onPicked = { viewModel.onDocumentPicked("licenseImage", it) },
                )

                DocumentPicker(
                    title = "Photo of your plate",
                    subtitle = "On the bike, with the whole plate in frame.",
                    chosen = state.plateImage?.label,
                    error = state.fieldErrors["plateImage"],
                    enabled = !state.submitting,
                    onPicked = { viewModel.onDocumentPicked("plateImage", it) },
                )
            }

            state.error?.let { Notice(it, warning = true) }

            PrimaryButton(
                text = "Send my application",
                onClick = viewModel::submit,
                enabled = state.canSubmit,
                busy = state.submitting,
                modifier = Modifier.padding(top = 14.dp),
            )

            if (state.submitting) {
                Text(
                    // Two photographs up a mobile connection is genuinely slow,
                    // and a button that has been spinning for forty seconds
                    // with no explanation is a button people press again.
                    text = "Sending your photos. This can take a minute on mobile data — " +
                        "leave the app open.",
                    style = MaterialTheme.typography.bodySmall,
                    color = RiderTheme.colors.textTertiary,
                )
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

/**
 * One field in a card of them.
 *
 * `last` drops the gap under the final field so a card does not end in a band of
 * empty space — the only thing this knows about layout, and the alternative is
 * every caller doing padding arithmetic.
 */
@Composable
private fun Field(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    error: String?,
    enabled: Boolean,
    keyboardType: KeyboardType = KeyboardType.Text,
    capitalization: KeyboardCapitalization = KeyboardCapitalization.None,
    imeAction: ImeAction = ImeAction.Next,
    masked: Boolean = false,
    hint: String? = null,
    last: Boolean = false,
) {
    RiderTextField(
        value = value,
        onValueChange = onChange,
        label = label,
        modifier = Modifier.padding(bottom = if (last) 0.dp else 10.dp),
        enabled = enabled,
        error = error,
        hint = hint,
        visualTransformation =
            if (masked) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(
            capitalization = capitalization,
            keyboardType = keyboardType,
            imeAction = imeAction,
        ),
    )
}

/**
 * One document, chosen through the platform's photo picker.
 *
 * `PickVisualMedia` rather than an intent for the gallery, and rather than the
 * camera: it needs **no permission at all** on any supported version, shows
 * only images, and hands back a URI scoped to this one pick. Asking a rider for
 * READ_MEDIA_IMAGES — on the screen where they are already being asked for a
 * photograph of their licence — is how an application gets abandoned.
 *
 * The trade is that they photograph the licence in their camera app first.
 * Worth it: the picker also lets them use a photo they already took, which is
 * what most people applying for delivery work already have on their phone.
 */
@Composable
private fun DocumentPicker(
    title: String,
    subtitle: String,
    chosen: String?,
    error: String?,
    enabled: Boolean,
    onPicked: (Uri?) -> Unit,
) {
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = onPicked,
    )

    Column(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable(enabled = enabled) {
                launcher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                )
            }
            .padding(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(
                icon = if (chosen != null) Icons.Filled.CheckCircle else Icons.Filled.PhotoCamera,
                tint = if (chosen != null) {
                    RiderTheme.colors.success
                } else {
                    RiderTheme.colors.textSecondary
                },
                container = if (chosen != null) {
                    RiderTheme.colors.successSoft
                } else {
                    RiderTheme.colors.fill
                },
            )
            Column(
                Modifier
                    .padding(start = 12.dp)
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    // The filename once one is picked, because two identical
                    // "Photo chosen" rows is how a licence ends up uploaded
                    // twice and a plate not at all.
                    text = chosen ?: subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = RiderTheme.colors.textSecondary,
                )
            }
            Text(
                text = if (chosen != null) "Change" else "Choose",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        error?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 8.dp, start = 48.dp),
            )
        }
    }
}

/**
 * The six shapes, on the way in.
 *
 * Its own copy rather than the account screen's, because the two forms are
 * built from different field primitives — this screen has a local `Field` with
 * its own spacing rules — and sharing the chip would mean sharing that too.
 * The list itself comes from VehicleType, so the two cannot drift on *what* is
 * offered, only on how it is drawn.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RegisterVehicleTypePicker(
    selected: VehicleType,
    enabled: Boolean,
    onSelect: (VehicleType) -> Unit,
) {
    Text(
        text = "Type",
        style = MaterialTheme.typography.bodySmall,
        color = RiderTheme.colors.textSecondary,
        modifier = Modifier.padding(top = 12.dp, bottom = 8.dp),
    )

    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        VehicleType.entries.forEach { type ->
            val container =
                if (type == selected) MaterialTheme.colorScheme.primary else RiderTheme.colors.fill
            val content = if (type == selected) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                RiderTheme.colors.textSecondary
            }

            Text(
                text = type.label,
                style = MaterialTheme.typography.bodySmall,
                color = content,
                modifier = Modifier
                    .clip(PillShape)
                    .background(container)
                    .selectable(
                        selected = type == selected,
                        enabled = enabled,
                        role = Role.RadioButton,
                        onClick = { onSelect(type) },
                    )
                    .padding(horizontal = 14.dp, vertical = 9.dp),
            )
        }
    }
}
