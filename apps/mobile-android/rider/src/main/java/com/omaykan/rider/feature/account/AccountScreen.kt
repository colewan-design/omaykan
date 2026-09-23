package com.omaykan.rider.feature.account

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.outlined.SupportAgent
import androidx.compose.material.icons.outlined.TwoWheeler
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.omaykan.rider.core.designsystem.ButtonShape
import com.omaykan.rider.core.designsystem.ForestTopBar
import com.omaykan.rider.core.designsystem.PillShape
import com.omaykan.rider.core.designsystem.PrimaryButton
import com.omaykan.rider.core.designsystem.RiderAvatar
import com.omaykan.rider.core.designsystem.RiderCard
import com.omaykan.rider.core.designsystem.RiderTextField
import com.omaykan.rider.core.designsystem.RiderTextStyles
import com.omaykan.rider.core.designsystem.RiderTheme
import com.omaykan.rider.core.designsystem.SectionLabel
import com.omaykan.rider.core.model.RatingSummary
import com.omaykan.rider.core.model.RiderProfile
import com.omaykan.rider.feature.common.Notice
import java.util.Locale

/*
 * The panels behind the menu. Each key is also the bar's title while its panel
 * is open, so it is written the way the menu row reads.
 */
private const val BIKE = "Vehicle information"
private const val DETAILS = "Personal details"
private const val DOCUMENTS = "My documents"
private const val PHOTO = "Your photo"
private const val RATINGS = "Ratings"
private const val PASSWORD = "Password"
private const val SUPPORT = "Help & support"

/**
 * "My profile": who the rider is to a shop and a customer, and the doors to
 * change it.
 *
 * ## The reference, and what stands in its slots
 *
 * - **Rider ID** is the plate. The account's id is a UUID nobody could read
 *   out, and the plate is the identifier a shop actually asks for.
 * - **"(128 deliveries)"** beside the stars is the rating count, because that
 *   is what the average is an average *of*. The delivery count is on Earnings.
 * - **My Documents · All Verified** is true of anyone who can see this screen:
 *   only an approved rider reaches the tab bar, and approval is somebody having
 *   checked the licence and the plate.
 * - **App Settings** and **Safety Guidelines** are left out. The only settings
 *   this app has are the two switches on Home, and there is no rider code on
 *   the platform to link to — a row that opens nothing is worse than no row.
 */
@Composable
fun AccountScreen(
    rider: RiderProfile,
    onSignOut: () -> Unit,
    viewModel: AccountViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    var section by rememberSaveable { mutableStateOf<String?>(null) }
    BackHandler(enabled = section != null) { section = null }

    LaunchedEffect(rider.id) {
        viewModel.seed(rider)
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        ForestTopBar(
            title = section ?: "My profile",
            onBack = section?.let { { section = null } },
        )

        Column(
            Modifier
                .weight(1f)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            when (section) {
                null -> {
                    ProfileHeader(rider = rider, onEditPhoto = { section = PHOTO })
                    AccountMenu(rider = rider, onSelect = { section = it }, onSignOut = onSignOut)
                }

                DETAILS -> DetailsPanel(state, viewModel)
                DOCUMENTS -> DocumentsPanel(rider)
                BIKE -> VehiclePanel(state, viewModel)
                PHOTO -> PhotoPanel(rider, state, viewModel)

                RATINGS -> {
                    LaunchedEffect(Unit) { viewModel.loadRatings() }
                    RatingsPanel(state)
                }

                SUPPORT -> {
                    LaunchedEffect(Unit) { viewModel.loadSupport() }
                    SupportPanel(state)
                }

                PASSWORD -> PasswordPanel(state, viewModel)
            }

            Spacer(Modifier.height(28.dp))
        }
    }
}

/** The face, the name, the plate, the stars, and a line to be felt. */
@Composable
private fun ProfileHeader(rider: RiderProfile, onEditPhoto: () -> Unit) {
    val colors = RiderTheme.colors

    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box {
            RiderAvatar(
                name = rider.name,
                photoUrl = rider.photoUrl,
                size = 96.dp,
                container = colors.accentSoft,
                content = MaterialTheme.colorScheme.primary,
            )
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .size(32.dp)
                    .clip(PillShape)
                    .background(colors.cta)
                    .border(2.dp, MaterialTheme.colorScheme.background, PillShape)
                    .clickable(onClickLabel = "Change your photo", onClick = onEditPhoto),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.Edit,
                    contentDescription = "Change your photo",
                    tint = colors.onCta,
                    modifier = Modifier.size(16.dp),
                )
            }
        }

        Text(
            text = rider.name,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 12.dp),
        )
        Text(
            text = "Plate ${rider.plateNumber}",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary,
        )

        RatingLine(rider.rating, Modifier.padding(top = 6.dp))

        Text(
            // "The farther you go, the more good stories." A line to be felt,
            // in the serif, like the greeting on Home.
            text = "“Mas malayo, mas maraming magandang kwento.”",
            style = RiderTextStyles.serifQuote,
            color = colors.textSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 10.dp, start = 12.dp, end = 12.dp),
        )
    }
}

/**
 * The stars, under the two rules RatingsPanel keeps: never 0.0 for a rider
 * nobody has rated, and the count always beside the average.
 */
@Composable
private fun RatingLine(rating: RatingSummary, modifier: Modifier = Modifier) {
    val colors = RiderTheme.colors
    val average = rating.average

    if (average == null || rating.count == 0) {
        Text(
            text = "No ratings yet",
            style = MaterialTheme.typography.bodySmall,
            color = colors.textTertiary,
            modifier = modifier,
        )
        return
    }

    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Filled.Star, contentDescription = null, tint = colors.gold, modifier = Modifier.size(18.dp))
        Text(
            text = String.format(Locale.US, "%.1f", average),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(start = 4.dp),
        )
        Text(
            text = " (" + (if (rating.count == 1) "1 rating" else "${rating.count} ratings") +
                (if (rating.isConfident) "" else " · early days") + ")",
            style = MaterialTheme.typography.bodySmall,
            color = colors.textSecondary,
        )
    }
}

@Composable
private fun AccountMenu(
    rider: RiderProfile,
    onSelect: (String) -> Unit,
    onSignOut: () -> Unit,
) {
    val colors = RiderTheme.colors
    val vehicle = rider.vehicle.label.ifBlank { rider.vehicle.type.label }
        .replaceFirstChar { it.titlecase(Locale.ENGLISH) }
    val rating = rider.rating.average

    RiderCard(modifier = Modifier.padding(top = 20.dp), padding = 0.dp) {
        MenuRow(Icons.Outlined.TwoWheeler, BIKE, "$vehicle · ${rider.plateNumber}", onSelect)
        MenuDivider()
        MenuRow(Icons.Outlined.PersonOutline, DETAILS, "${rider.name} · ${rider.phone}", onSelect)
        MenuDivider()
        MenuRow(Icons.Outlined.Description, DOCUMENTS, "Driver's licence · Plate", onSelect) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Verified", style = MaterialTheme.typography.bodySmall, color = colors.success)
                Icon(
                    Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = colors.success,
                    modifier = Modifier
                        .padding(start = 4.dp)
                        .size(16.dp),
                )
            }
        }
        MenuDivider()
        MenuRow(
            Icons.Outlined.PhotoCamera,
            PHOTO,
            if (rider.photoUrl == null) "Not added — shops see your initial" else "Shown only while you carry an order",
            onSelect,
        )
        MenuDivider()
        MenuRow(
            Icons.Outlined.StarOutline,
            RATINGS,
            if (rating == null || rider.rating.count == 0) {
                "No ratings yet"
            } else {
                String.format(Locale.US, "%.1f from %d", rating, rider.rating.count) +
                    if (rider.rating.count == 1) " rating" else " ratings"
            },
            onSelect,
        )
        MenuDivider()
        MenuRow(Icons.Outlined.Lock, PASSWORD, "Change your password", onSelect)
        MenuDivider()
        MenuRow(Icons.Outlined.SupportAgent, SUPPORT, "Call or email the team", onSelect)
    }

    OutlinedButton(
        onClick = onSignOut,
        modifier = Modifier
            .padding(top = 20.dp)
            .fillMaxWidth()
            .height(52.dp),
        shape = ButtonShape,
        border = BorderStroke(1.dp, colors.cta),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.cta),
    ) {
        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
        Text(
            "Sign out",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

@Composable
private fun MenuDivider() {
    HorizontalDivider(Modifier.padding(start = 58.dp), color = RiderTheme.colors.hairline)
}

@Composable
private fun MenuRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onSelect: (String) -> Unit,
    trailing: (@Composable () -> Unit)? = null,
) {
    val colors = RiderTheme.colors

    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onSelect(title) }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(26.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        trailing?.invoke()
        Icon(
            Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = colors.textTertiary,
            modifier = Modifier.size(20.dp),
        )
    }
}

// -- Panels that live in this file ------------------------------------------

@Composable
private fun DetailsPanel(state: AccountUiState, viewModel: AccountViewModel) {
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
}

/**
 * What was checked, read-only. The reference's "My Documents"; the files
 * themselves never reach the app — `Rider::toPortalArray` omits the paths.
 */
@Composable
private fun DocumentsPanel(rider: RiderProfile) {
    SectionLabel("Verified account", Modifier.padding(top = 20.dp, bottom = 12.dp))

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

        RiderTextField(
            value = rider.plateNumber,
            onValueChange = {},
            label = "Plate",
            enabled = false,
            hint = "Checked against the photo of your plate. Change it under Personal details.",
            modifier = Modifier.padding(top = 12.dp),
        )
    }
}

@Composable
private fun PasswordPanel(state: AccountUiState, viewModel: AccountViewModel) {
    SectionLabel("Password", Modifier.padding(top = 20.dp, bottom = 12.dp))

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
}
