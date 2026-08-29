package com.omaykan.storefront.feature.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.omaykan.storefront.core.designsystem.LoadingState
import com.omaykan.storefront.core.designsystem.OmaykanTheme

/**
 * Email and password.
 *
 * Two forms, each saved on its own, because they fail for different reasons and
 * one Save for both would leave a shopper guessing which half went through.
 */
@Composable
fun SecurityScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SecurityViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    AccountSectionScaffold(
        title = "Sign-in details",
        onBack = onBack,
        modifier = modifier,
        notice = state.notice,
        onNoticeShown = viewModel::onNoticeShown,
    ) { inner ->
        if (!state.loaded) {
            LoadingState(inner)
            return@AccountSectionScaffold
        }

        Column(inner, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionHeading("Email")

            if (state.emailManagedByGoogle) {
                // The one place this screen refuses something, so it says what
                // opens it rather than only that it is shut.
                Text(
                    "You sign in with Google, so ${state.currentEmail} is managed there. " +
                        "Set a password below and you can change it here.",
                    style = MaterialTheme.typography.bodySmall,
                    color = OmaykanTheme.colors.textSecondary,
                )
            } else {
                OutlinedTextField(
                    value = state.email,
                    onValueChange = viewModel::onEmail,
                    label = { Text("Email") },
                    singleLine = true,
                    enabled = !state.emailBusy,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier.fillMaxWidth(),
                )

                OutlinedTextField(
                    value = state.emailPassword,
                    onValueChange = viewModel::onEmailPassword,
                    label = { Text("Your current password") },
                    singleLine = true,
                    enabled = !state.emailBusy,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    supportingText = {
                        Text("An unattended phone is otherwise one tap from becoming somebody else's account.")
                    },
                    modifier = Modifier.fillMaxWidth(),
                )

                SectionError(state.emailError)

                SectionPrimaryButton(
                    label = "Change email",
                    onClick = viewModel::saveEmail,
                    enabled = state.canSaveEmail,
                    busy = state.emailBusy,
                )
            }

            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = OmaykanTheme.colors.separator)
            Spacer(Modifier.height(12.dp))

            SectionHeading(if (state.hasPassword) "Password" else "Set a password")

            if (!state.hasPassword) {
                Text(
                    "You have only ever signed in with Google. Setting a password " +
                        "gives you a second way in — Google keeps working either way.",
                    style = MaterialTheme.typography.bodySmall,
                    color = OmaykanTheme.colors.textSecondary,
                )
            }

            if (state.hasPassword) {
                OutlinedTextField(
                    value = state.currentPassword,
                    onValueChange = viewModel::onCurrentPassword,
                    label = { Text("Current password") },
                    singleLine = true,
                    enabled = !state.passwordBusy,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            OutlinedTextField(
                value = state.newPassword,
                onValueChange = viewModel::onNewPassword,
                label = { Text(if (state.hasPassword) "New password" else "Password") },
                singleLine = true,
                enabled = !state.passwordBusy,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                supportingText = { Text("At least 8 characters.") },
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = state.confirmation,
                onValueChange = viewModel::onConfirmation,
                label = { Text("Type it again") },
                singleLine = true,
                enabled = !state.passwordBusy,
                isError = state.confirmation.isNotEmpty() && state.confirmation != state.newPassword,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                supportingText = {
                    val problem = state.passwordProblem
                    if (state.confirmation.isNotEmpty() && problem != null) Text(problem)
                },
                modifier = Modifier.fillMaxWidth(),
            )

            SectionError(state.passwordError)

            SectionPrimaryButton(
                label = if (state.hasPassword) "Change password" else "Set password",
                onClick = viewModel::savePassword,
                enabled = state.canSavePassword,
                busy = state.passwordBusy,
            )

            if (state.hasPassword) {
                // The consequence people do not expect, said before they act
                // rather than in the confirmation afterwards.
                Text(
                    "Changing your password signs you out on your other devices.",
                    style = MaterialTheme.typography.bodySmall,
                    color = OmaykanTheme.colors.textTertiary,
                )
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}
