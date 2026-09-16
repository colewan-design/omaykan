package com.omaykan.seller.feature.signin

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material.icons.filled.Diversity3
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.omaykan.seller.R
import com.omaykan.seller.core.designsystem.ButtonShape
import com.omaykan.seller.core.designsystem.CardShape
import com.omaykan.seller.core.designsystem.MountainMark
import com.omaykan.seller.core.designsystem.PrimaryButton
import com.omaykan.seller.core.designsystem.SectionLabel
import com.omaykan.seller.core.designsystem.SellerLockup
import com.omaykan.seller.core.designsystem.SellerTheme
import com.omaykan.seller.core.designsystem.SerifFamily
import com.omaykan.seller.core.designsystem.SpacedCaps
import com.omaykan.seller.core.designsystem.WovenBand
import com.omaykan.seller.core.model.StaffStore
import com.omaykan.seller.feature.shell.SIGNUP_URL
import com.omaykan.seller.feature.shell.openInBrowser

/**
 * The welcome screen, and behind its "Log In" button the sign-in itself.
 *
 * One screen with two faces rather than two screens, for the reason
 * MainActivity gives: signed out is a single state, and a back stack inside it
 * would only add a way to press Back onto a page you have already left. The
 * form keeps everything it had — a person, then a shop, the Google button when
 * the build has a client id — and now opens over the shop photograph instead
 * of a plain green panel.
 */
@Composable
fun SignInScreen(viewModel: SignInViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showForm by rememberSaveable { mutableStateOf(false) }
    val formVisible = showForm || state.choosingStore
    val forest = SellerTheme.colors.canopy

    BackHandler(enabled = formVisible && !state.submitting) {
        if (state.choosingStore) viewModel.cancelStoreChoice() else showForm = false
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(forest),
    ) {
        Image(
            painter = painterResource(R.drawable.hero_vendor),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            // The vendor stands at the centre with open sky above her, which
            // is where the wordmark goes; centred keeps her in frame on a
            // tall, narrow phone.
            alignment = BiasAlignment(0f, 0f),
            modifier = Modifier.fillMaxSize(),
        )
        // Forest over the photograph: dense at the top for the wordmark,
        // lighter across the middle so the shop shows, dense again at the
        // bottom where the buttons are.
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to forest.copy(alpha = 0.88f),
                        0.42f to forest.copy(alpha = 0.50f),
                        0.72f to forest.copy(alpha = 0.78f),
                        1f to forest.copy(alpha = 0.97f),
                    ),
                ),
        )

        BoxWithConstraints(Modifier.fillMaxSize()) {
            val minHeight = maxHeight
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .heightIn(min = minHeight)
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Brand(compact = formVisible)

                if (formVisible) {
                    if (state.choosingStore) {
                        StoreChoiceCard(state = state, viewModel = viewModel)
                    } else {
                        FormCard(state = state, viewModel = viewModel, onBack = { showForm = false })
                    }
                    Spacer(Modifier.height(32.dp))
                } else {
                    // Weighted here, so the spacer inside it has the rest of
                    // the screen to grow into and the buttons sit low, where
                    // a thumb is. Inside a wrap-content column it had nothing.
                    Welcome(onLogIn = { showForm = true }, modifier = Modifier.weight(1f))
                }
            }
        }

        if (!formVisible) {
            Column(Modifier.align(Alignment.BottomCenter)) {
                WovenBand(height = 18.dp)
                Box(
                    Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF3A1D16))
                        .navigationBarsPadding(),
                )
            }
        }
    }
}

@Composable
private fun Brand(compact: Boolean) {
    Column(
        Modifier.padding(top = if (compact) 20.dp else 44.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        MountainMark(
            Modifier.size(
                width = if (compact) 72.dp else 108.dp,
                height = if (compact) 48.dp else 72.dp,
            ),
            mountain = Color.Transparent,
            line = SellerTheme.colors.onCanopy,
        )
        SellerLockup(
            stacked = true,
            fontSize = if (compact) 28.sp else 40.sp,
            modifier = Modifier.padding(top = 10.dp),
        )
        if (!compact) {
            Text(
                text = "LOCAL PRODUCTS.\nBIGGER OPPORTUNITIES.",
                style = SpacedCaps.copy(fontSize = 13.sp, lineHeight = 21.sp),
                color = SellerTheme.colors.onCanopy,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 16.dp),
            )
        }
    }
}

/**
 * Three reasons, two doors, and the sign-off.
 *
 * "Create a Seller Account" opens the web signup rather than a form here:
 * signing up makes an organization, a shop and an owner account in one go,
 * and that page already does it — with the business-type choices and the
 * checks a phone form would have to duplicate.
 */
@Composable
private fun Welcome(onLogIn: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current

    Column(
        modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            Modifier.padding(top = 36.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Reason(Icons.Filled.Storefront, "Sell to more customers nearby")
            Reason(Icons.Filled.Insights, "Grow your business")
            Reason(Icons.Filled.Diversity3, "Be part of a stronger local community")
        }

        Spacer(Modifier.height(48.dp))
        Spacer(Modifier.weight(1f))

        PrimaryButton(
            label = "Log In",
            onClick = onLogIn,
            modifier = Modifier.fillMaxWidth(),
            height = 52,
        )

        Box(
            Modifier
                .padding(top = 12.dp)
                .fillMaxWidth()
                .height(52.dp)
                .clip(ButtonShape)
                .border(1.dp, Color.White.copy(alpha = 0.85f), ButtonShape)
                .clickable { context.openInBrowser(SIGNUP_URL) },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "Create a Seller Account",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
            )
        }

        Text(
            text = "Likha. Kultura. Kabuhayan.",
            style = TextStyle(
                fontFamily = SerifFamily,
                fontStyle = FontStyle.Italic,
                fontSize = 17.sp,
                letterSpacing = 0.5.sp,
            ),
            color = SellerTheme.colors.onCanopy,
            modifier = Modifier.padding(top = 24.dp, bottom = 44.dp),
        )
    }
}

@Composable
private fun Reason(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = SellerTheme.colors.onCanopy, modifier = Modifier.size(24.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = Color.White,
            modifier = Modifier.padding(start = 14.dp),
        )
    }
}

/**
 * The form, on a white card over the photograph.
 */
@Composable
private fun FormCard(state: SignInUiState, viewModel: SignInViewModel, onBack: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 24.dp)
            .clip(CardShape)
            .background(MaterialTheme.colorScheme.surface)
            .padding(20.dp),
    ) {
        Text(
            text = "Log in to your shop",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = "Use the same account you use on the register.",
            style = MaterialTheme.typography.bodySmall,
            color = SellerTheme.colors.textTertiary,
            modifier = Modifier.padding(top = 2.dp, bottom = 12.dp),
        )

        SectionLabel("Username or email")

        OutlinedTextField(
            value = state.identifier,
            onValueChange = viewModel::onIdentifierChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            placeholder = { Text("How you sign in to the register") },
            singleLine = true,
            enabled = !state.submitting,
            isError = state.identifierError != null,
            supportingText = state.identifierError?.let { { Text(it) } },
            shape = ButtonShape,
            keyboardOptions = KeyboardOptions(
                // Usernames are stored lowercase and an email is never
                // capitalised; a field that fights the value is no help.
                capitalization = KeyboardCapitalization.None,
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Next,
            ),
        )

        SectionLabel("Password", Modifier.padding(top = 14.dp))

        OutlinedTextField(
            value = state.password,
            onValueChange = viewModel::onPasswordChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            singleLine = true,
            enabled = !state.submitting,
            visualTransformation = PasswordVisualTransformation(),
            shape = ButtonShape,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Go,
            ),
            keyboardActions = KeyboardActions(onGo = { viewModel.submit() }),
        )

        state.error?.let { Notice(it) }

        PrimaryButton(
            label = "Log In",
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
                shape = ButtonShape,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp)
                    .height(50.dp),
            ) {
                Text("Continue with Google")
            }
        }

        Text(
            text = "Anything you mark in this app is recorded against your name.",
            style = MaterialTheme.typography.bodySmall,
            color = SellerTheme.colors.textTertiary,
            modifier = Modifier.padding(top = 14.dp),
        )

        TextButton(
            onClick = onBack,
            enabled = !state.submitting,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        ) {
            Text("Back")
        }
    }
}

/**
 * Which shop, for somebody who works at more than one.
 *
 * Only ever shown when the answer is genuinely ambiguous. Picking the first for
 * them would mean a manager advancing orders at the wrong counter.
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

        state.error?.let { Notice(it) }

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
            .background(SellerTheme.colors.accentSoft)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Filled.Storefront,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = SellerTheme.colors.onAccentSoft,
        )
        Column(Modifier.padding(start = 12.dp)) {
            Text(
                text = store.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
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
 * A sentence that is not a field error — "verify your email first" is not a
 * mistake on the form, so it is not drawn like one.
 */
@Composable
private fun Notice(text: String) {
    val tint = SellerTheme.colors.danger

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
            .clip(ButtonShape)
            .background(tint.copy(alpha = 0.10f))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Info, contentDescription = null, modifier = Modifier.size(18.dp), tint = tint)
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(start = 10.dp),
        )
    }
}
