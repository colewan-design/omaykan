package com.omaykan.rider.feature.forgot

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MarkEmailRead
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.activity.compose.BackHandler
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.omaykan.rider.core.designsystem.Canopy
import com.omaykan.rider.core.designsystem.CanopyIconButton
import com.omaykan.rider.core.designsystem.GhostButton
import com.omaykan.rider.core.designsystem.IconBadge
import com.omaykan.rider.core.designsystem.PrimaryButton
import com.omaykan.rider.core.designsystem.RiderCard
import com.omaykan.rider.core.designsystem.RiderTextField
import com.omaykan.rider.core.designsystem.RiderTheme
import com.omaykan.rider.feature.common.Notice

/**
 * The way back in for a rider who cannot sign in.
 *
 * Before this existed the answer was "ask an operator to edit the database":
 * there was no reset endpoint, there is no operator screen that sets a
 * password, and registering again is impossible because the address is already
 * taken by the account they are locked out of.
 *
 * ## The link opens the web portal, not this app
 *
 * Deliberate, and the reason there is no deep link in the manifest. A reset
 * token that only an installed app could spend strands the rider who opened the
 * mail on a laptop, or on the phone they are locked out of and about to
 * reinstall. The portal does the same job in a browser, and after it the rider
 * comes back here and signs in normally.
 */
@Composable
fun ForgotPasswordScreen(
    onBack: () -> Unit,
    viewModel: ForgotPasswordViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    BackHandler(onBack = onBack)

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .imePadding(),
    ) {
        Canopy {
            Spacer(Modifier.height(20.dp))

            CanopyIconButton(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                description = "Back to sign in",
                onClick = onBack,
            )

            Text(
                text = "Forgotten password",
                style = MaterialTheme.typography.headlineMedium,
                color = RiderTheme.colors.onCanopy,
                modifier = Modifier.padding(top = 20.dp),
            )

            Text(
                text = "We will email you a link. It works once, and it stops working in an hour.",
                style = MaterialTheme.typography.bodyMedium,
                color = RiderTheme.colors.onCanopyMuted,
                modifier = Modifier.padding(top = 6.dp, bottom = 28.dp),
            )
        }

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 20.dp),
        ) {
            val sent = state.sent

            if (sent != null) {
                /*
                 * The form is gone once it has answered, rather than left on
                 * screen with a message above it. Leaving it invites a second
                 * and a third request, and this endpoint is throttled to five
                 * an hour — a rider who taps it four more times locks
                 * themselves out of the only door they have.
                 */
                RiderCard {
                    IconBadge(
                        icon = Icons.Filled.MarkEmailRead,
                        tint = MaterialTheme.colorScheme.primary,
                        container = RiderTheme.colors.fill,
                    )

                    Text(
                        // The server's own sentence, not a paraphrase of it.
                        text = sent,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(top = 14.dp),
                    )

                    Text(
                        text = "Open the link on any phone or laptop — it opens the rider " +
                            "portal in a browser. Come back here and sign in once it is done. " +
                            "Check the spam folder before asking again.",
                        style = MaterialTheme.typography.bodySmall,
                        color = RiderTheme.colors.textTertiary,
                        modifier = Modifier.padding(top = 10.dp),
                    )
                }

                PrimaryButton(
                    text = "Back to sign in",
                    onClick = onBack,
                    modifier = Modifier.padding(top = 20.dp),
                )
            } else {
                RiderCard {
                    RiderTextField(
                        value = state.email,
                        onValueChange = viewModel::onEmailChange,
                        label = "Email",
                        enabled = !state.submitting,
                        error = state.emailError,
                        hint = "The address you applied with.",
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Email,
                            imeAction = ImeAction.Go,
                        ),
                        keyboardActions = KeyboardActions(onGo = { viewModel.submit() }),
                    )
                }

                state.error?.let { Notice(it, warning = true) }

                PrimaryButton(
                    text = "Email me a link",
                    onClick = viewModel::submit,
                    enabled = state.canSubmit,
                    busy = state.submitting,
                    modifier = Modifier.padding(top = 20.dp),
                )

                GhostButton(
                    text = "Back to sign in",
                    icon = null,
                    onClick = onBack,
                    enabled = !state.submitting,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                )
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}
