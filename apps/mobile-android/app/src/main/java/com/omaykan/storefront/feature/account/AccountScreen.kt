package com.omaykan.storefront.feature.account

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.omaykan.storefront.core.designsystem.LoadingState
import com.omaykan.storefront.core.designsystem.OmaykanTheme
import com.omaykan.storefront.core.model.CustomerAccount
import com.omaykan.storefront.core.model.OrderStage
import com.omaykan.storefront.core.model.SessionState
import com.omaykan.storefront.core.designsystem.ForestTopBar
import com.omaykan.storefront.navigation.ABOUT_URL
import com.omaykan.storefront.navigation.openInBrowser

/**
 * The account tab.
 *
 * It sells signing in rather than demanding it, and the copy has to keep doing
 * so: ordering from this app works without an account and always will
 * (mobile-plan.md §5, fact 3). What an account buys is a contact form already
 * filled in and an order history that survives a new phone — so that is what
 * the screen says, instead of the usual wall.
 *
 * The password form is always here, and Google sits above it only when the
 * build has an OAuth client id. That order is deliberate: a build with no
 * client id must still offer a way in, and the accounts are the same accounts
 * either way — one made on the web storefront signs in here on the same
 * address, with the same password.
 */
@Composable
fun AccountScreen(
    modifier: Modifier = Modifier,
    /**
     * Non-null when this is a pushed destination rather than the Account tab.
     * The tab has the bottom bar for navigation and needs no back arrow.
     */
    onBack: (() -> Unit)? = null,
    /**
     * Fired the moment a session exists, and only when somebody is waiting on
     * one — checkout sent them here to sign in, and the errand is done.
     */
    onSignedIn: (() -> Unit)? = null,
    /*
     * The account portal's sections, as the tab that hosts them supplies them.
     *
     * Defaulted to nothing so the pushed sign-in destination — reached from
     * checkout, whose errand is a session and nothing else — does not offer a
     * menu into screens the shopper did not come here for.
     */
    onOpenProfile: (() -> Unit)? = null,
    onOpenAddresses: (() -> Unit)? = null,
    onOpenPayment: (() -> Unit)? = null,
    onOpenSecurity: (() -> Unit)? = null,
    onOpenSaved: (() -> Unit)? = null,
    onOpenOrders: ((OrderStage?) -> Unit)? = null,
    onTrackOrder: ((String) -> Unit)? = null,
    viewModel: AccountViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val signedIn = state.session is SessionState.SignedIn

    LaunchedEffect(signedIn) {
        if (signedIn) onSignedIn?.invoke()
    }

    /*
     * Custom Tabs never says it was dismissed.
     *
     * Backing out of Google's page just resumes the activity underneath, with
     * no result and no callback — so without this the screen would spin on a
     * sign-in that ended a minute ago. Coming back to the foreground with a
     * request still outstanding is read as the shopper having changed their
     * mind; a redirect that *did* land has already cleared the request by the
     * time this runs, so a real sign-in is never mistaken for a cancelled one.
     */
    LifecycleResumeEffect(Unit) {
        viewModel.onFlowAbandoned()
        // Also the moment to recount: an order placed, tracked or received
        // since this tab was last in front changes every number on it.
        viewModel.refreshSummary()
        onPauseOrDispose { }
    }

    Column(modifier.fillMaxSize()) {
        ForestTopBar(
            title = if (onBack != null) "Your Account" else "Account",
            subtitle = if (onBack != null) "Sign in to keep your orders together" else null,
            onBack = onBack,
        )

        when (val session = state.session) {
            is SessionState.Restoring -> LoadingState()

            is SessionState.SignedOut -> SignedOut(
                state = state,
                // The Activity, not the composition-local context, which under
                // Hilt is a wrapper around it. A Custom Tab launched from
                // anything but an Activity opens in a task of its own and comes
                // back as a second Omaykan in the shopper's recents.
                onGoogle = { viewModel.onSignInPressed(context.findActivity()) },
                onName = viewModel::onNameChange,
                onEmail = viewModel::onEmailChange,
                onPhone = viewModel::onPhoneChange,
                onPassword = viewModel::onPasswordChange,
                onTogglePassword = viewModel::togglePasswordVisible,
                onSwitch = viewModel::switchTo,
                onSubmit = viewModel::submit,
                onDismissError = viewModel::dismissError,
                onDismissNotice = viewModel::dismissNotice,
                savedCount = state.summary.saved,
                onOpenSaved = onOpenSaved,
                onOpenOrders = onOpenOrders?.let { open -> { open(null) } },
                onOpenAbout = { context.openInBrowser(ABOUT_URL) },
            )

            is SessionState.SignedIn -> AccountHome(
                account = session.account,
                summary = state.summary,
                busy = state.busy,
                onSignOut = viewModel::signOut,
                onOpenProfile = onOpenProfile,
                onOpenAddresses = onOpenAddresses,
                onOpenPayment = onOpenPayment,
                onOpenSecurity = onOpenSecurity,
                onOpenSaved = onOpenSaved,
                onOpenOrders = onOpenOrders,
                // Nothing to configure and nothing to sign in for: the public
                // page the web serves, opened in a tab over this one.
                onOpenAbout = { context.openInBrowser(ABOUT_URL) },
                onTrackOrder = onTrackOrder,
                onRefresh = { viewModel.refreshSummary(shown = true) },
            )
        }
    }
}

// -- Signed out ---------------------------------------------------------------

private data class ModeCopy(val title: String, val sub: String, val submit: String)

private fun copyFor(mode: AuthMode) = when (mode) {
    AuthMode.Register -> ModeCopy(
        title = "Create your account",
        sub = "Save your address and your usual payment, and checkout becomes two taps instead " +
            "of a form.",
        submit = "Create account",
    )

    AuthMode.Forgot -> ModeCopy(
        title = "Reset your password",
        sub = "Tell us the email on the account and we will send a link to choose a new password.",
        submit = "Send reset link",
    )

    AuthMode.SignIn -> ModeCopy(
        title = "Keep your orders together",
        sub = "Sign in and checkout already knows your name, your number and where to deliver — " +
            "and every order stays in one list, on whichever phone you are holding.",
        submit = "Sign in",
    )
}

@Composable
private fun SignedOut(
    state: AccountUiState,
    onGoogle: () -> Unit,
    onName: (String) -> Unit,
    onEmail: (String) -> Unit,
    onPhone: (String) -> Unit,
    onPassword: (String) -> Unit,
    onTogglePassword: () -> Unit,
    onSwitch: (AuthMode) -> Unit,
    onSubmit: () -> Unit,
    onDismissError: () -> Unit,
    onDismissNotice: () -> Unit,
    savedCount: Int = 0,
    onOpenSaved: (() -> Unit)? = null,
    onOpenOrders: (() -> Unit)? = null,
    onOpenAbout: (() -> Unit)? = null,
) {
    val copy = copyFor(state.mode)
    val keyboard = LocalSoftwareKeyboardController.current

    // Hiding the keyboard first is not cosmetic: the reply lands under the
    // button, and on a short phone the keyboard is sitting on top of it.
    val submit: () -> Unit = {
        keyboard?.hide()
        onSubmit()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Filled.Person,
            contentDescription = null,
            modifier = Modifier.size(40.dp),
            tint = OmaykanTheme.colors.textTertiary,
        )

        Text(
            text = copy.title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 14.dp),
        )

        Text(
            text = copy.sub,
            style = MaterialTheme.typography.bodyMedium,
            color = OmaykanTheme.colors.textSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp),
        )

        // Google only where it answers the question on screen. On the reset
        // card the shopper is midway through recovering a password, and a
        // second, unrelated way in is a distraction at the worst moment.
        if (state.googleAvailable && state.mode != AuthMode.Forgot) {
            Button(
                onClick = onGoogle,
                enabled = !state.busy,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                ),
                modifier = Modifier
                    .padding(top = 24.dp)
                    .fillMaxWidth()
                    .height(52.dp),
            ) {
                Text("Continue with Google", fontWeight = FontWeight.SemiBold)
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 20.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                HorizontalDivider(modifier = Modifier.weight(1f))
                Text(
                    text = "or",
                    style = MaterialTheme.typography.bodySmall,
                    color = OmaykanTheme.colors.textTertiary,
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
                HorizontalDivider(modifier = Modifier.weight(1f))
            }
        } else {
            Spacer(Modifier.height(20.dp))
        }

        if (state.mode == AuthMode.Register) {
            OutlinedTextField(
                value = state.name,
                onValueChange = onName,
                label = { Text("Your name") },
                singleLine = true,
                enabled = !state.busy,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Next,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
            )
        }

        OutlinedTextField(
            value = state.email,
            onValueChange = onEmail,
            label = { Text("Email") },
            singleLine = true,
            enabled = !state.busy,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                imeAction = if (state.mode == AuthMode.Forgot) ImeAction.Done else ImeAction.Next,
            ),
            keyboardActions = KeyboardActions(onDone = { submit() }),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
        )

        if (state.mode == AuthMode.Register) {
            OutlinedTextField(
                value = state.phone,
                onValueChange = onPhone,
                label = { Text("Phone (optional)") },
                supportingText = { Text("How a rider reaches you if they cannot find the door.") },
                singleLine = true,
                enabled = !state.busy,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Phone,
                    imeAction = ImeAction.Next,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
            )
        }

        if (state.mode != AuthMode.Forgot) {
            OutlinedTextField(
                value = state.password,
                onValueChange = onPassword,
                label = { Text("Password") },
                singleLine = true,
                enabled = !state.busy,
                visualTransformation = if (state.showPassword) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
                trailingIcon = {
                    IconButton(onClick = onTogglePassword) {
                        Icon(
                            imageVector = if (state.showPassword) {
                                Icons.Filled.VisibilityOff
                            } else {
                                Icons.Filled.Visibility
                            },
                            contentDescription = if (state.showPassword) {
                                "Hide password"
                            } else {
                                "Show password"
                            },
                        )
                    }
                },
                supportingText = if (state.mode == AuthMode.Register) {
                    { Text("At least 8 characters.") }
                } else {
                    null
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { submit() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
            )
        }

        Button(
            onClick = submit,
            enabled = state.canSubmit,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .padding(top = 20.dp)
                .fillMaxWidth()
                .height(52.dp),
        ) {
            if (state.busy) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            } else {
                Text(copy.submit, fontWeight = FontWeight.SemiBold)
            }
        }

        val error = state.error
        val notice = state.notice

        if (error != null) {
            Flash(
                text = error,
                container = MaterialTheme.colorScheme.errorContainer,
                content = MaterialTheme.colorScheme.onErrorContainer,
                onDismiss = onDismissError,
            )
        } else if (notice != null) {
            // Never both at once: a notice is the reply to something that
            // worked, and showing it beside a failure reads as contradiction.
            Flash(
                text = notice,
                container = OmaykanTheme.colors.fill,
                content = OmaykanTheme.colors.ink,
                onDismiss = onDismissNotice,
            )
        }

        // Sign-in is the only card that offers the reset route: from the others
        // it is either where you came from or where you already are.
        if (state.mode == AuthMode.SignIn) {
            TextButton(
                onClick = { onSwitch(AuthMode.Forgot) },
                enabled = !state.busy,
                modifier = Modifier.padding(top = 4.dp),
            ) {
                Text("Forgot your password?")
            }
        }

        Row(
            modifier = Modifier.padding(top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            when (state.mode) {
                AuthMode.SignIn -> {
                    Text(
                        text = "New here?",
                        style = MaterialTheme.typography.bodyMedium,
                        color = OmaykanTheme.colors.textSecondary,
                    )
                    TextButton(onClick = { onSwitch(AuthMode.Register) }, enabled = !state.busy) {
                        Text("Create an account")
                    }
                }

                AuthMode.Register -> {
                    Text(
                        text = "Already have an account?",
                        style = MaterialTheme.typography.bodyMedium,
                        color = OmaykanTheme.colors.textSecondary,
                    )
                    TextButton(onClick = { onSwitch(AuthMode.SignIn) }, enabled = !state.busy) {
                        Text("Sign in")
                    }
                }

                AuthMode.Forgot -> {
                    TextButton(onClick = { onSwitch(AuthMode.SignIn) }, enabled = !state.busy) {
                        Text("Back to sign in")
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // The line that has to stay true. Nothing on this screen is a gate.
        Text(
            text = "You do not need an account to order from the app. Browsing and checkout work " +
                "exactly the same without one.",
            style = MaterialTheme.typography.bodySmall,
            color = OmaykanTheme.colors.textTertiary,
            textAlign = TextAlign.Center,
        )

        // And the proof of it, rather than only the claim.
        AccountGuestShortcuts(
            savedCount = savedCount,
            onOpenSaved = onOpenSaved,
            onOpenOrders = onOpenOrders,
            onOpenAbout = onOpenAbout,
            modifier = Modifier.padding(top = 20.dp),
        )
    }
}

/** One shape for both replies, so a failure and a confirmation sit in the same place. */
@Composable
private fun Flash(
    text: String,
    container: Color,
    content: Color,
    onDismiss: () -> Unit,
) {
    Row(
        modifier = Modifier
            .padding(top = 16.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(container)
            .padding(start = 14.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = content,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onDismiss) { Text("Dismiss", color = content) }
    }
}

/**
 * Unwraps whatever the Activity has been wrapped in.
 *
 * `LocalContext` inside a Hilt-injected composable is a ContextWrapper around
 * the Activity rather than the Activity itself, and a Custom Tab launched from
 * one of those quietly lands in a new task.
 */
private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

