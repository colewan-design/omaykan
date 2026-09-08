package com.omaykan.seller.feature.signin

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.omaykan.seller.R
import com.omaykan.seller.core.designsystem.CardShape
import com.omaykan.seller.core.designsystem.HeroShape
import com.omaykan.seller.core.designsystem.PrimaryButton
import com.omaykan.seller.core.designsystem.SectionLabel
import com.omaykan.seller.core.designsystem.SellerTheme
import com.omaykan.seller.core.model.StaffStore

/**
 * "Sign in."
 *
 * This screen used to ask for the shop's code: one field, no password, and
 * nothing to remember before opening. What it asks for now is a person, because
 * a code shared by everyone at the counter could not put a name on a settled
 * payment or take one leaver's access away. See SessionRepository for the whole
 * of that trade.
 *
 * The green panel at the top is the same one the orders screen wears, and that
 * is the point: the first thing a merchant sees is the shape of the screen they
 * are about to spend their day on, with the wordmark on it so a phone handed
 * round a shop says which app it is before anybody has typed anything.
 */
@Composable
fun SignInScreen(viewModel: SignInViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .navigationBarsPadding()
            // The keyboard covers the button on a short screen otherwise.
            .imePadding()
            .verticalScroll(rememberScrollState()),
    ) {
        Banner()

        Column(Modifier.padding(horizontal = 20.dp)) {
            if (state.choosingStore) {
                StoreChoiceCard(state = state, viewModel = viewModel)
            } else {
                FormCard(state = state, viewModel = viewModel)

                Text(
                    text = "Sign in with the same account you use on the register. " +
                        "Anything you mark here is recorded against your name.",
                    style = MaterialTheme.typography.bodySmall,
                    color = SellerTheme.colors.textTertiary,
                    modifier = Modifier.padding(top = 16.dp, start = 4.dp, end = 4.dp),
                )
            }
        }

        Spacer(Modifier.height(40.dp))
    }
}

/** The wordmark and the promise, on the app's green. */
@Composable
private fun Banner() {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(HeroShape)
            .background(SellerTheme.colors.canopy)
            .statusBarsPadding()
            .padding(start = 24.dp, end = 24.dp, top = 40.dp, bottom = 36.dp),
    ) {
        Image(
            // The light-on-dark colourway, which is what this asset is — the
            // same one the launcher icon uses over the same green.
            painter = painterResource(R.drawable.logo_wordmark_light),
            contentDescription = "Omaykan",
            modifier = Modifier.height(26.dp),
        )

        Text(
            text = "Seller",
            style = MaterialTheme.typography.headlineLarge,
            color = SellerTheme.colors.onCanopy,
            modifier = Modifier.padding(top = 18.dp),
        )

        Text(
            text = "Orders from your storefront, on your phone.",
            style = MaterialTheme.typography.bodyLarge,
            color = SellerTheme.colors.canopyMuted,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

/**
 * The form, on a card that overlaps nothing.
 *
 * Deliberately one white panel rather than fields floating on the ground: it
 * gives the sign-in screen the same "everything you can touch is on a card"
 * rule the orders screen follows, so the two screens teach each other.
 */
@Composable
private fun FormCard(state: SignInUiState, viewModel: SignInViewModel) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 24.dp)
            .clip(CardShape)
            .background(MaterialTheme.colorScheme.surface)
            .padding(20.dp),
    ) {
        SectionLabel("Username or email")

        OutlinedTextField(
            value = state.identifier,
            onValueChange = viewModel::onIdentifierChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp),
            placeholder = { Text("How you sign in to the register") },
            singleLine = true,
            enabled = !state.submitting,
            isError = state.identifierError != null,
            supportingText = state.identifierError?.let { { Text(it) } },
            shape = MaterialTheme.shapes.large,
            keyboardOptions = KeyboardOptions(
                // No auto-capitalisation: usernames are stored lowercase and an
                // email is never capitalised. A field that fights the value it
                // is about to reject is worse than no help at all.
                capitalization = KeyboardCapitalization.None,
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Next,
            ),
        )

        SectionLabel("Password", Modifier.padding(top = 16.dp))

        OutlinedTextField(
            value = state.password,
            onValueChange = viewModel::onPasswordChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp),
            singleLine = true,
            enabled = !state.submitting,
            visualTransformation = PasswordVisualTransformation(),
            shape = MaterialTheme.shapes.large,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Go,
            ),
            keyboardActions = KeyboardActions(onGo = { viewModel.submit() }),
        )

        state.error?.let { Notice(it, warning = true) }

        PrimaryButton(
            label = "Sign in",
            onClick = viewModel::submit,
            enabled = state.canSubmit,
            busy = state.submitting,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 20.dp),
        )

        // Absent entirely on a build with no OAuth client id, rather than a
        // button that cannot work. See GoogleAuthFlow.
        if (viewModel.googleAvailable) {
            val context = LocalContext.current

            OutlinedButton(
                onClick = { viewModel.signInWithGoogle(context) },
                enabled = !state.submitting,
                shape = MaterialTheme.shapes.large,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
            ) {
                Text("Continue with Google")
            }
        }
    }
}

/**
 * Which shop, for somebody who works at more than one.
 *
 * Only ever shown when the answer is genuinely ambiguous. Picking the first for
 * them would mean a manager advancing orders at the wrong counter and having no
 * way to see that they had.
 */
@Composable
private fun StoreChoiceCard(state: SignInUiState, viewModel: SignInViewModel) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 24.dp)
            .clip(CardShape)
            .background(MaterialTheme.colorScheme.surface)
            .padding(20.dp),
    ) {
        SectionLabel("Which shop is this?")

        Text(
            text = "You work at more than one. This phone opens the one you pick.",
            style = MaterialTheme.typography.bodyMedium,
            color = SellerTheme.colors.textTertiary,
            modifier = Modifier.padding(top = 6.dp),
        )

        state.storeChoices.forEach { store ->
            StoreRow(store = store, enabled = !state.submitting) { viewModel.chooseStore(store) }
        }

        state.error?.let { Notice(it, warning = true) }

        TextButton(
            onClick = viewModel::cancelStoreChoice,
            enabled = !state.submitting,
            modifier = Modifier.padding(top = 8.dp),
        ) {
            Text("Sign in as someone else")
        }
    }
}

@Composable
private fun StoreRow(store: StaffStore, enabled: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
            .clip(CardShape)
            .background(SellerTheme.colors.canopy.copy(alpha = 0.08f))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Filled.Storefront,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = SellerTheme.colors.canopy,
        )
        Column(Modifier.padding(start = 12.dp)) {
            Text(
                text = store.name,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = store.code,
                style = MaterialTheme.typography.bodySmall,
                color = SellerTheme.colors.textTertiary,
            )
        }
    }
}

/**
 * A sentence that is not a field error.
 *
 * Kept visually distinct from the red under a text field, because these two are
 * different claims: an error says "what you typed is wrong", and this says
 * "something happened that you should know about". "Verify your email first" is
 * exactly the second — nothing on the form is a mistake.
 */
@Composable
private fun Notice(text: String, warning: Boolean = false) {
    val tint = if (warning) SellerTheme.colors.danger else SellerTheme.colors.warning

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
            .clip(CardShape)
            .background(tint.copy(alpha = 0.12f))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Filled.Info,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = tint,
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(start = 10.dp),
        )
    }
}
