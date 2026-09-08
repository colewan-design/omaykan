package com.omaykan.rider.feature.account

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.omaykan.rider.core.designsystem.Canopy
import com.omaykan.rider.core.designsystem.CanopyIconButton
import com.omaykan.rider.core.designsystem.PrimaryButton
import com.omaykan.rider.core.designsystem.RiderCard
import com.omaykan.rider.core.designsystem.RiderTextField
import com.omaykan.rider.core.designsystem.RiderTheme
import com.omaykan.rider.core.designsystem.SectionLabel
import com.omaykan.rider.core.model.RiderProfile
import com.omaykan.rider.feature.common.Notice

/**
 * The rider's own account.
 *
 * ## What is not here, and why it is shown anyway
 *
 * The email and the licence number are on screen, greyed, with the reason next
 * to each. The API refuses to change either — the email is the login handle and
 * the address a reset link goes to, and the licence number is the thing an
 * operator actually looked at a photograph of. Leaving them out entirely reads
 * as an oversight; offering them and failing on submit is worse. Saying so on
 * the field is the only version that answers the question a rider is actually
 * asking, which is "can I change this, and if not, who can".
 *
 * ## Where sign-out lives now
 *
 * Here, on the canopy, and no longer on the work screen. It was a button beside
 * Refresh on the job board — one thumb-width from the control a rider presses
 * every few minutes, on the screen they are looking at while moving. An account
 * screen is where you go on purpose.
 */
@Composable
fun AccountScreen(
    rider: RiderProfile,
    onSignOut: () -> Unit,
    viewModel: AccountViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(rider.id) {
        viewModel.seed(rider.name, rider.phone, rider.plateNumber)
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .imePadding(),
    ) {
        Canopy {
            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "Your account",
                        style = MaterialTheme.typography.headlineMedium,
                        color = RiderTheme.colors.onCanopy,
                    )
                    Text(
                        text = "The shop reads these off a screen when you arrive.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = RiderTheme.colors.onCanopyMuted,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }

                CanopyIconButton(
                    icon = Icons.AutoMirrored.Filled.Logout,
                    description = "Sign out",
                    onClick = onSignOut,
                )
            }

            Spacer(Modifier.height(24.dp))
        }

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            SectionLabel("Your details", Modifier.padding(top = 20.dp, bottom = 12.dp))

            RiderCard {
                RiderTextField(
                    value = state.name,
                    onValueChange = viewModel::onNameChange,
                    label = "Name",
                    enabled = !state.savingDetails,
                    error = state.fieldErrors["name"],
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                )

                RiderTextField(
                    value = state.phone,
                    onValueChange = viewModel::onPhoneChange,
                    label = "Phone",
                    enabled = !state.savingDetails,
                    error = state.fieldErrors["phone"],
                    modifier = Modifier.padding(top = 12.dp),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Phone,
                        imeAction = ImeAction.Next,
                    ),
                )

                RiderTextField(
                    value = state.plateNumber,
                    onValueChange = viewModel::onPlateChange,
                    label = "Plate number",
                    enabled = !state.savingDetails,
                    error = state.fieldErrors["plateNumber"],
                    hint = "Change this when you change bikes. The plate we have a photo of " +
                        "stays on file, so an operator can see they no longer match.",
                    modifier = Modifier.padding(top = 12.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                )
            }

            state.detailsError?.let { Notice(it, warning = true) }
            if (state.detailsSaved) Notice("Saved.")

            PrimaryButton(
                text = "Save details",
                onClick = viewModel::saveDetails,
                enabled = state.canSaveDetails,
                busy = state.savingDetails,
                modifier = Modifier.padding(top = 16.dp),
            )

            SectionLabel("Not yours to change", Modifier.padding(top = 28.dp, bottom = 12.dp))

            RiderCard {
                RiderTextField(
                    value = rider.email,
                    onValueChange = {},
                    label = "Email",
                    enabled = false,
                    hint = "How you sign in, and where a reset link goes. Message us to change it.",
                )

                RiderTextField(
                    value = rider.licenseNumber,
                    onValueChange = {},
                    label = "Licence number",
                    enabled = false,
                    hint = "Someone checked this against the photo you sent. Changing it means " +
                        "checking it again — message us and we will.",
                    modifier = Modifier.padding(top = 12.dp),
                )
            }

            SectionLabel("Password", Modifier.padding(top = 28.dp, bottom = 12.dp))

            RiderCard {
                RiderTextField(
                    value = state.currentPassword,
                    onValueChange = viewModel::onCurrentPasswordChange,
                    label = "Current password",
                    enabled = !state.savingPassword,
                    error = state.passwordFieldError,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Next,
                    ),
                )

                RiderTextField(
                    value = state.newPassword,
                    onValueChange = viewModel::onNewPasswordChange,
                    label = "New password",
                    enabled = !state.savingPassword,
                    hint = "At least ${AccountUiState.MIN_PASSWORD} characters.",
                    modifier = Modifier.padding(top = 12.dp),
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Next,
                    ),
                )

                RiderTextField(
                    value = state.confirmPassword,
                    onValueChange = viewModel::onConfirmPasswordChange,
                    label = "New password again",
                    enabled = !state.savingPassword,
                    modifier = Modifier.padding(top = 12.dp),
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done,
                    ),
                )

                Text(
                    text = "Changing it signs out every other phone you are signed in on. " +
                        "This one stays — you may be in the middle of a delivery.",
                    style = MaterialTheme.typography.bodySmall,
                    color = RiderTheme.colors.textTertiary,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }

            state.passwordError?.let { Notice(it, warning = true) }
            if (state.passwordChanged) Notice("Password changed. Your other phones are signed out.")

            PrimaryButton(
                text = "Change password",
                onClick = viewModel::savePassword,
                enabled = state.canSavePassword,
                busy = state.savingPassword,
                modifier = Modifier.padding(top = 16.dp),
            )

            Spacer(Modifier.height(28.dp))
        }
    }
}
