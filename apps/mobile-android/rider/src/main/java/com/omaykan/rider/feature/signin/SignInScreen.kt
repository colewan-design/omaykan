package com.omaykan.rider.feature.signin

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.TwoWheeler
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.omaykan.rider.core.designsystem.Canopy
import com.omaykan.rider.core.designsystem.GhostButton
import com.omaykan.rider.core.designsystem.IconBadge
import com.omaykan.rider.core.designsystem.PrimaryButton
import com.omaykan.rider.core.designsystem.RiderCard
import com.omaykan.rider.core.designsystem.RiderTextField
import com.omaykan.rider.core.designsystem.RiderTheme
import com.omaykan.rider.feature.common.Notice

/**
 * "Sign in to ride."
 *
 * Two fields, on a phone, probably outdoors. Everything below the button is the
 * route for somebody who does not have an account yet — which, for a while
 * after launch, is most people who open this app.
 *
 * The canopy carries the promise rather than a logo. This is the first screen a
 * rider ever sees and the one sentence on it that matters is the one an
 * aggregator cannot say: the whole delivery fee is theirs. It sits in the dark
 * block above the fields, at the size of a headline, because that is the claim
 * the app is asking them to believe before they type anything.
 */
@Composable
fun SignInScreen(
    onRegister: () -> Unit,
    onForgotPassword: () -> Unit,
    viewModel: SignInViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var revealed by remember { mutableStateOf(false) }

    // The canopy is outside the scroll and the form is inside it. Scrolling the
    // whole screen would slide the dark block up under a transparent status bar
    // and leave a line of body text sitting on top of the clock; this way the
    // block is the fixed top of the screen it is drawn to look like, and only
    // the fields move.
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            // The keyboard covers the button on a short screen otherwise.
            .imePadding(),
    ) {
        Canopy {
            Spacer(Modifier.height(40.dp))

            IconBadge(
                icon = Icons.Filled.TwoWheeler,
                tint = RiderTheme.colors.onCanopy,
                container = RiderTheme.colors.canopyFill,
                size = 52.dp,
            )

            Text(
                text = "Omaykan Rider",
                style = MaterialTheme.typography.headlineMedium,
                color = RiderTheme.colors.onCanopy,
                modifier = Modifier.padding(top = 20.dp),
            )

            Text(
                text = "Jobs from every shop on the platform. The whole delivery fee is yours.",
                style = MaterialTheme.typography.bodyMedium,
                color = RiderTheme.colors.onCanopyMuted,
                modifier = Modifier.padding(top = 6.dp, bottom = 32.dp),
            )
        }

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 20.dp),
        ) {
            RiderCard {
                RiderTextField(
                    value = state.email,
                    onValueChange = viewModel::onEmailChange,
                    label = "Email",
                    enabled = !state.submitting,
                    error = state.emailError,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Email,
                        imeAction = ImeAction.Next,
                    ),
                )

                RiderTextField(
                    value = state.password,
                    onValueChange = viewModel::onPasswordChange,
                    label = "Password",
                    enabled = !state.submitting,
                    modifier = Modifier.padding(top = 12.dp),
                    visualTransformation = if (revealed) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    trailingIcon = {
                        // Worth the pixels here. A password typed on a phone
                        // screen outdoors, wrong twice, is the fastest way to
                        // lose somebody before their first shift.
                        IconButton(onClick = { revealed = !revealed }) {
                            Icon(
                                imageVector = if (revealed) {
                                    Icons.Filled.VisibilityOff
                                } else {
                                    Icons.Filled.Visibility
                                },
                                contentDescription =
                                    if (revealed) "Hide password" else "Show password",
                                tint = RiderTheme.colors.textSecondary,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Go,
                    ),
                    keyboardActions = KeyboardActions(onGo = { viewModel.submit() }),
                )
            }

            state.error?.let { Notice(it, warning = true) }

            PrimaryButton(
                text = "Sign in",
                onClick = viewModel::submit,
                enabled = state.canSubmit,
                busy = state.submitting,
                modifier = Modifier.padding(top = 20.dp),
            )

            /*
             * Under the button, not beside the password field. A rider who is
             * about to succeed should not be offered a detour; one who has just
             * failed is looking exactly here.
             */
            TextButton(
                onClick = onForgotPassword,
                enabled = !state.submitting,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
            ) {
                Text(
                    text = "Forgotten your password?",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "New here?",
                    style = MaterialTheme.typography.bodyMedium,
                    color = RiderTheme.colors.textSecondary,
                )

                GhostButton(
                    text = "Apply to ride",
                    icon = null,
                    onClick = onRegister,
                    enabled = !state.submitting,
                    modifier = Modifier.fillMaxWidth(),
                )

                Text(
                    // Said here rather than discovered at the end of a form.
                    // The review is a human looking at a licence, it is not
                    // instant, and somebody signing up at midnight should know
                    // that before they photograph anything.
                    text = "You will need your driver's licence and your plate. " +
                        "Someone checks both before you can take jobs.",
                    style = MaterialTheme.typography.bodySmall,
                    color = RiderTheme.colors.textTertiary,
                    textAlign = TextAlign.Center,
                )
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}
