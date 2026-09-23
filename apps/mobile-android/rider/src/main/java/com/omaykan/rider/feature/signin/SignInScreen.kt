package com.omaykan.rider.feature.signin

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Route
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.omaykan.rider.R
import com.omaykan.rider.core.designsystem.ButtonShape
import com.omaykan.rider.core.designsystem.CardShape
import com.omaykan.rider.core.designsystem.LightStatusBarIcons
import com.omaykan.rider.core.designsystem.PrimaryButton
import com.omaykan.rider.core.designsystem.LockupEmber
import com.omaykan.rider.core.designsystem.RiderTextField
import com.omaykan.rider.core.designsystem.RiderTextStyles
import com.omaykan.rider.core.designsystem.RiderTheme
import com.omaykan.rider.core.designsystem.SIGN_OFF
import com.omaykan.rider.core.designsystem.SerifFamily
import com.omaykan.rider.core.designsystem.SpacedCaps
import com.omaykan.rider.core.designsystem.WeaveGround
import com.omaykan.rider.core.designsystem.WovenBand
import com.omaykan.rider.feature.common.Notice

/**
 * The welcome screen, and behind its "Log in" button the sign-in itself.
 *
 * One screen with two faces rather than two screens, for the reason
 * MainActivity gives: signed out is a single state, and a back stack inside it
 * would only add a way to press Back onto a page you have already left. The
 * form keeps everything it had — email, password with a reveal, the forgotten
 * password route — and now opens on a card over a rider on a mountain road
 * instead of under a plain green block.
 *
 * The photograph (`hero_rider`) is a rider with a green top box stopped on a
 * mountain road at sunrise, looking out over terraces and cloud — the
 * reference's composition, with open sky at the top for the wordmark and dark
 * road at the bottom for the buttons.
 */
@Composable
fun SignInScreen(
    onRegister: () -> Unit,
    onForgotPassword: () -> Unit,
    viewModel: SignInViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showForm by rememberSaveable { mutableStateOf(false) }
    val forest = RiderTheme.colors.canopy

    BackHandler(enabled = showForm && !state.submitting) { showForm = false }
    LightStatusBarIcons()

    Box(
        Modifier
            .fillMaxSize()
            .background(forest),
    ) {
        Image(
            painter = painterResource(R.drawable.hero_rider),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            // A 2:3 photograph on a phone crops the sides. The rider and the
            // box are left of centre, so the crop leans left to keep them.
            alignment = BiasAlignment(-0.5f, 0f),
            modifier = Modifier.fillMaxSize(),
        )
        // Forest over the photograph: dense at the top for the wordmark,
        // lighter across the middle so the rider shows, dense again at the
        // bottom where the buttons are.
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to forest.copy(alpha = 0.90f),
                        0.40f to forest.copy(alpha = 0.40f),
                        0.62f to forest.copy(alpha = 0.55f),
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
                Brand(compact = showForm)

                if (showForm) {
                    FormCard(
                        state = state,
                        viewModel = viewModel,
                        onForgotPassword = onForgotPassword,
                        onBack = { showForm = false },
                    )
                    Spacer(Modifier.height(32.dp))
                } else {
                    // Weighted, so the spacer inside has the rest of the screen
                    // to grow into and the buttons sit low, where a thumb is.
                    Welcome(
                        onLogIn = { showForm = true },
                        onRegister = onRegister,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        if (!showForm) {
            Column(Modifier.align(Alignment.BottomCenter)) {
                WovenBand(height = 44.dp)
                Box(
                    Modifier
                        .fillMaxWidth()
                        .background(WeaveGround)
                        .navigationBarsPadding(),
                )
            }
        }
    }
}

/**
 * The painted mark and wordmark (`brand_mountain_mark`, `brand_wordmark`) over
 * "Rider" in the serif. Artwork rather than the drawn `MountainMark` and `Wordmark` here
 * because this is the one screen big enough to show the texture; the smaller
 * lockups elsewhere keep the drawn versions.
 */
@Composable
private fun Brand(compact: Boolean) {
    Column(
        Modifier.padding(top = if (compact) 20.dp else 44.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(
            painter = painterResource(R.drawable.brand_mountain_mark),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.width(if (compact) 96.dp else 150.dp),
        )
        Image(
            painter = painterResource(R.drawable.brand_wordmark),
            contentDescription = "Omaykan",
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .padding(top = if (compact) 8.dp else 14.dp)
                .width(if (compact) 190.dp else 270.dp),
        )
        Text(
            text = "Rider",
            color = LockupEmber,
            style = TextStyle(
                fontFamily = SerifFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = if (compact) 32.sp else 46.sp,
            ),
            maxLines = 1,
            modifier = Modifier.padding(top = 2.dp),
        )
        if (!compact) {
            Text(
                text = "DELIVERING GOOD THINGS\nTO HIGHER PLACES",
                style = SpacedCaps.copy(fontSize = 13.sp, lineHeight = 21.sp),
                color = RiderTheme.colors.onCanopy,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 16.dp),
            )
        }
    }
}

/**
 * Three reasons, two doors, and the sign-off.
 *
 * The reasons are the three things the old sign-in canopy and the onboarding
 * pages already promised, set in the reference's shape: every shop on one
 * board, the whole fee, and the choice of which jobs to take. Nothing on this
 * screen is a claim the app does not keep.
 */
@Composable
private fun Welcome(
    onLogIn: () -> Unit,
    onRegister: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            Modifier.padding(top = 36.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Reason(Icons.Outlined.Storefront, "Jobs from every shop on Omaykan")
            Reason(Icons.Outlined.Payments, "The whole delivery fee is yours")
            Reason(Icons.Outlined.Route, "Take the jobs that suit your route")
        }

        Spacer(Modifier.height(48.dp))
        Spacer(Modifier.weight(1f))

        PrimaryButton(text = "Log in", onClick = onLogIn)

        Box(
            Modifier
                .padding(top = 12.dp)
                .fillMaxWidth()
                .height(52.dp)
                .clip(ButtonShape)
                .border(1.dp, Color.White.copy(alpha = 0.85f), ButtonShape)
                .clickable(onClick = onRegister),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "Create an account",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
            )
        }

        Text(
            // Said here rather than discovered at the end of a form. The review
            // is a human looking at a licence, it is not instant, and somebody
            // signing up at midnight should know that before they photograph
            // anything.
            text = "You will need your driver's licence and your plate. " +
                "Someone checks both before you can take jobs.",
            style = MaterialTheme.typography.bodySmall,
            color = RiderTheme.colors.onCanopyMuted,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 12.dp),
        )

        Text(
            text = SIGN_OFF,
            style = RiderTextStyles.serifQuote.copy(letterSpacing = 0.5.sp),
            color = RiderTheme.colors.onCanopy,
            // Clear of the woven band, which is drawn over the bottom edge.
            modifier = Modifier.padding(top = 20.dp, bottom = 64.dp),
        )
    }
}

@Composable
private fun Reason(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            icon,
            contentDescription = null,
            tint = RiderTheme.colors.onCanopy,
            modifier = Modifier.size(24.dp),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = Color.White,
            modifier = Modifier.padding(start = 14.dp),
        )
    }
}

/**
 * "Log in to ride." Two fields, on a phone, probably outdoors.
 *
 * The forgotten-password route is under the button rather than beside the
 * password field: a rider who is about to succeed should not be offered a
 * detour, and one who has just failed is looking exactly here.
 */
@Composable
private fun FormCard(
    state: SignInUiState,
    viewModel: SignInViewModel,
    onForgotPassword: () -> Unit,
    onBack: () -> Unit,
) {
    var revealed by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 24.dp)
            .clip(CardShape)
            .background(MaterialTheme.colorScheme.surface)
            .padding(20.dp),
    ) {
        Text(
            text = "Log in to ride",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = "The email and password you applied with.",
            style = MaterialTheme.typography.bodySmall,
            color = RiderTheme.colors.textTertiary,
            modifier = Modifier.padding(top = 2.dp, bottom = 14.dp),
        )

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
                // Worth the pixels here. A password typed on a phone screen
                // outdoors, wrong twice, is the fastest way to lose somebody
                // before their first shift.
                IconButton(onClick = { revealed = !revealed }) {
                    Icon(
                        imageVector = if (revealed) {
                            Icons.Filled.VisibilityOff
                        } else {
                            Icons.Filled.Visibility
                        },
                        contentDescription = if (revealed) "Hide password" else "Show password",
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

        state.error?.let { Notice(it, warning = true) }

        PrimaryButton(
            text = "Log in",
            onClick = viewModel::submit,
            enabled = state.canSubmit,
            busy = state.submitting,
            modifier = Modifier.padding(top = 20.dp),
        )

        TextButton(
            onClick = onForgotPassword,
            enabled = !state.submitting,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
        ) {
            Text("Forgotten your password?", style = MaterialTheme.typography.bodyMedium)
        }

        TextButton(
            onClick = onBack,
            enabled = !state.submitting,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        ) {
            Text(
                "Back",
                style = MaterialTheme.typography.bodyMedium,
                color = RiderTheme.colors.textSecondary,
            )
        }
    }
}
