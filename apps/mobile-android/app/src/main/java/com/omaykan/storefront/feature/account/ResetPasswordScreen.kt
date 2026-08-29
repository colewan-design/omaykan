package com.omaykan.storefront.feature.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.omaykan.storefront.core.designsystem.OmaykanTheme

/**
 * "Choose a new password", at the far end of a reset email.
 *
 * No email field: the token was issued for one address and the server checks
 * the pair, so offering to change it would only be offering a way to fail. It
 * is shown, fixed, so the shopper can see which account this is — which
 * matters on a phone with two of them.
 *
 * @param onDone dismisses the screen once the reset has gone through. The
 *   shopper is signed in by then, so this returns them to the market rather
 *   than to a sign-in form.
 */
@Composable
fun ResetPasswordScreen(
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ResetPasswordViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val keyboard = LocalSoftwareKeyboardController.current

    LaunchedEffect(state.done) {
        if (state.done != null) onDone()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            "Choose a new password",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = OmaykanTheme.colors.ink,
        )
        Text(
            "For ${state.email}.",
            style = MaterialTheme.typography.bodyMedium,
            color = OmaykanTheme.colors.textSecondary,
        )

        Spacer(Modifier.height(4.dp))

        val dead = state.linkDead
        if (dead != null) {
            // The form stays on screen but inert: the sentence explains itself
            // better standing next to the thing it is about than it would as a
            // toast over an empty page.
            Text(
                dead,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
            TextButton(onClick = onDone, modifier = Modifier.align(Alignment.Start)) {
                Text("Ask for a new link", color = MaterialTheme.colorScheme.primary)
            }
        }

        OutlinedTextField(
            value = state.password,
            onValueChange = viewModel::onPassword,
            label = { Text("New password") },
            singleLine = true,
            enabled = !state.busy && dead == null,
            visualTransformation = if (state.showPassword) {
                VisualTransformation.None
            } else {
                PasswordVisualTransformation()
            },
            trailingIcon = {
                IconButton(onClick = viewModel::togglePasswordVisible) {
                    Icon(
                        imageVector = if (state.showPassword) {
                            Icons.Filled.VisibilityOff
                        } else {
                            Icons.Filled.Visibility
                        },
                        contentDescription = if (state.showPassword) "Hide password" else "Show password",
                        tint = OmaykanTheme.colors.textTertiary,
                    )
                }
            },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Next,
            ),
            supportingText = { Text("At least 8 characters.") },
            modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(
            value = state.confirmation,
            onValueChange = viewModel::onConfirmation,
            label = { Text("Type it again") },
            singleLine = true,
            enabled = !state.busy && dead == null,
            isError = state.confirmation.isNotEmpty() && state.confirmation != state.password,
            visualTransformation = if (state.showPassword) {
                VisualTransformation.None
            } else {
                PasswordVisualTransformation()
            },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(
                onDone = {
                    keyboard?.hide()
                    viewModel.submit()
                },
            ),
            supportingText = {
                val problem = state.localProblem
                if (state.confirmation.isNotEmpty() && problem != null) Text(problem)
            },
            modifier = Modifier.fillMaxWidth(),
        )

        state.error?.let { message ->
            Text(
                message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }

        Spacer(Modifier.height(4.dp))

        Button(
            onClick = {
                keyboard?.hide()
                viewModel.submit()
            },
            enabled = state.canSubmit,
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = OmaykanTheme.colors.ink,
                contentColor = OmaykanTheme.colors.onInk,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
        ) {
            if (state.busy) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = OmaykanTheme.colors.onInk,
                )
            } else {
                Text("Save and sign in", fontWeight = FontWeight.SemiBold)
            }
        }

        // Said plainly, because it is a consequence people do not expect: the
        // server drops every other token on the account as part of the reset.
        Text(
            "Changing your password signs you out on your other devices.",
            style = MaterialTheme.typography.bodySmall,
            color = OmaykanTheme.colors.textTertiary,
        )
    }
}
