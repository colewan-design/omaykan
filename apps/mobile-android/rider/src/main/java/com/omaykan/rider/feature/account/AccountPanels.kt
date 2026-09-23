package com.omaykan.rider.feature.account

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.MailOutline
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.omaykan.rider.core.data.PickedImage
import com.omaykan.rider.core.data.readPickedImage
import com.omaykan.rider.core.designsystem.PillShape
import com.omaykan.rider.core.designsystem.PrimaryButton
import com.omaykan.rider.core.designsystem.RiderAvatar
import com.omaykan.rider.core.designsystem.RiderCard
import com.omaykan.rider.core.designsystem.RiderTextField
import com.omaykan.rider.core.designsystem.RiderTheme
import com.omaykan.rider.core.designsystem.SectionLabel
import com.omaykan.rider.core.model.RatingSummary
import com.omaykan.rider.core.model.RiderProfile
import com.omaykan.rider.core.model.VehicleType
import com.omaykan.rider.feature.common.Notice

/**
 * The four panels the account screen grew in 2026-09: the bike, the face, what
 * customers said, and how to reach a human.
 *
 * Split out of AccountScreen rather than added to it because that file was
 * already three forms long, and these are four more. Nothing here holds state —
 * each takes the screen's [AccountUiState] and the view model, exactly as the
 * panels that stayed behind do.
 */

// -- The bike ---------------------------------------------------------------

/**
 * What the rider rides.
 *
 * Separate from "Edit profile" even though the plate lives over there, and the
 * split is the honest one: the plate is a *document* an operator photographed,
 * and this is a description the rider is free to change at any time without
 * anybody re-approving anything. Putting them on one form would suggest they
 * carry the same weight.
 */
@Composable
internal fun VehiclePanel(state: AccountUiState, viewModel: AccountViewModel) {
    SectionLabel("What you ride", Modifier.padding(top = 20.dp, bottom = 12.dp))

    RiderCard {
        Text(
            text = "The shop reads this at the counter and the customer reads it at the kerb. " +
                "Colour helps most — it is the only part of it readable from a window.",
            style = MaterialTheme.typography.bodySmall,
            color = RiderTheme.colors.textTertiary,
            modifier = Modifier.padding(bottom = 14.dp),
        )

        VehicleTypePicker(
            selected = state.vehicleType,
            enabled = !state.savingVehicle,
            onSelect = viewModel::onVehicleTypeChange,
        )

        RiderTextField(
            value = state.vehicleColor,
            onValueChange = viewModel::onVehicleColorChange,
            label = "Colour",
            enabled = !state.savingVehicle,
            hint = "Red, black, blue. What somebody would say looking at it.",
            modifier = Modifier.padding(top = 14.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
        )

        RiderTextField(
            value = state.vehicleMake,
            onValueChange = viewModel::onVehicleMakeChange,
            label = "Make",
            enabled = !state.savingVehicle,
            hint = "Honda, Yamaha, Suzuki. Leave it blank if you would rather not.",
            modifier = Modifier.padding(top = 12.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
        )

        RiderTextField(
            value = state.vehicleModel,
            onValueChange = viewModel::onVehicleModelChange,
            label = "Model",
            enabled = !state.savingVehicle,
            modifier = Modifier.padding(top = 12.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        )
    }

    state.vehicleError?.let { Notice(it, warning = true) }
    if (state.vehicleSaved) Notice("Saved.")

    PrimaryButton(
        text = "Save bike",
        onClick = viewModel::saveVehicle,
        enabled = state.canSaveVehicle,
        busy = state.savingVehicle,
        modifier = Modifier.padding(top = 16.dp),
    )

    Spacer(Modifier.height(20.dp))
}

/**
 * The six shapes, as a wrapping row of pills.
 *
 * A row of taps rather than a dropdown: there are six, they all fit, and a
 * dropdown would hide five of them behind a tap for no gain. It is also the
 * only control on this screen a rider might use while wearing gloves.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun VehicleTypePicker(
    selected: VehicleType,
    enabled: Boolean,
    onSelect: (VehicleType) -> Unit,
) {
    Text(
        text = "Type",
        style = MaterialTheme.typography.bodySmall,
        color = RiderTheme.colors.textSecondary,
        modifier = Modifier.padding(bottom = 8.dp),
    )

    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        VehicleType.entries.forEach { type ->
            VehicleTypeChip(
                type = type,
                selected = type == selected,
                enabled = enabled,
                onSelect = { onSelect(type) },
            )
        }
    }
}

@Composable
private fun VehicleTypeChip(
    type: VehicleType,
    selected: Boolean,
    enabled: Boolean,
    onSelect: () -> Unit,
) {
    val container = if (selected) MaterialTheme.colorScheme.primary else RiderTheme.colors.fill
    val content =
        if (selected) MaterialTheme.colorScheme.onPrimary else RiderTheme.colors.textSecondary

    Row(
        modifier = Modifier
            .clip(PillShape)
            .background(container)
            .selectable(
                selected = selected,
                enabled = enabled,
                role = Role.RadioButton,
                onClick = onSelect,
            )
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(type.label, style = MaterialTheme.typography.bodySmall, color = content)
    }
}

// -- The photograph ---------------------------------------------------------

/**
 * The rider's face.
 *
 * The consent argument is on the screen rather than in a comment, because it is
 * the rider's decision to make and they can only make it if they are told what
 * happens: the photo is shown to the shop and to the customer *while an order
 * is in the rider's hands*, and to nobody afterwards. That is enforced
 * server-side by Order::riderProfileForCustomer, and it is the whole reason
 * this is safe to offer.
 */
@Composable
internal fun PhotoPanel(
    rider: RiderProfile,
    state: AccountUiState,
    viewModel: AccountViewModel,
) {
    val context = LocalContext.current

    // `PickVisualMedia` rather than an intent for the gallery: it needs no
    // storage permission, and it hands back a grant for exactly the one file
    // the rider chose. The same contract the licence upload uses.
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult

        when (val picked = context.readPickedImage(uri, fallbackName = "photo.jpg")) {
            is PickedImage.Ok -> viewModel.uploadPhoto(picked.upload)
            is PickedImage.TooBig ->
                viewModel.onPhotoUnreadable("That photo is ${picked.megabytes}MB. Pick one under 8MB.")
            PickedImage.Unreadable ->
                viewModel.onPhotoUnreadable("That photo could not be read. Pick it again.")
        }
    }

    SectionLabel("Your photo", Modifier.padding(top = 20.dp, bottom = 12.dp))

    RiderCard {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp),
        ) {
            RiderAvatar(
                name = rider.name,
                photoUrl = rider.photoUrl,
                size = 72.dp,
                container = RiderTheme.colors.accentSoft,
                content = MaterialTheme.colorScheme.primary,
            )

            Text(
                text = if (rider.photoUrl == null) {
                    "You have not added one. Shops and customers see the first letter of " +
                        "your name instead."
                } else {
                    "Shops and customers see this while you are carrying their order."
                },
                style = MaterialTheme.typography.bodySmall,
                color = RiderTheme.colors.textSecondary,
                modifier = Modifier.weight(1f),
            )
        }

        Text(
            text = "It is shown only while an order is in your hands, and disappears from " +
                "the customer's screen once you have delivered it. It is never on the job " +
                "board and never public.",
            style = MaterialTheme.typography.bodySmall,
            color = RiderTheme.colors.textTertiary,
        )
    }

    state.photoError?.let { Notice(it, warning = true) }

    PrimaryButton(
        text = if (rider.photoUrl == null) "Add a photo" else "Change photo",
        onClick = {
            launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        },
        enabled = !state.savingPhoto,
        busy = state.savingPhoto,
        modifier = Modifier.padding(top = 16.dp),
    )

    if (rider.photoUrl != null) {
        PrimaryButton(
            text = "Remove photo",
            onClick = viewModel::removePhoto,
            enabled = !state.savingPhoto,
            modifier = Modifier.padding(top = 10.dp),
        )
    }

    Spacer(Modifier.height(20.dp))
}

// -- Ratings ----------------------------------------------------------------

/**
 * What customers said.
 *
 * Two rules are enforced here rather than server-side, because they are about
 * what a sentence *means* rather than about what is true:
 *
 * - A rider nobody has rated is told exactly that, and never shown 0.0.
 * - Under five ratings the count leads and the average follows in smaller
 *   type, because one bad night out of three is not a rider's average — it is
 *   one bad night, and a number that says otherwise is worth arguing with.
 */
@Composable
internal fun RatingsPanel(state: AccountUiState) {
    SectionLabel("What customers said", Modifier.padding(top = 20.dp, bottom = 12.dp))

    when {
        state.ratingsLoading && state.ratings == null -> {
            RiderCard {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text("Loading…", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        state.ratingsError != null -> Notice(state.ratingsError, warning = true)

        else -> {
            val summary = state.ratings?.summary
            val average = summary?.average
            val count = summary?.count ?: 0

            RiderCard {
                if (average == null || count == 0) {
                    Text(
                        text = "No ratings yet.",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = "Customers can rate a delivery once you have handed it over.",
                        style = MaterialTheme.typography.bodySmall,
                        color = RiderTheme.colors.textSecondary,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            Icons.Filled.Star,
                            contentDescription = null,
                            tint = RiderTheme.colors.cash,
                            modifier = Modifier.size(26.dp),
                        )
                        Text(
                            text = String.format("%.1f", average),
                            style = MaterialTheme.typography.headlineMedium,
                        )
                        Text(
                            text = if (count == 1) "1 rating" else "$count ratings",
                            style = MaterialTheme.typography.bodyMedium,
                            color = RiderTheme.colors.textSecondary,
                        )
                    }

                    if (count < RatingSummary.CONFIDENCE_THRESHOLD) {
                        Text(
                            text = "Early days — an average this new moves a long way on one " +
                                "delivery.",
                            style = MaterialTheme.typography.bodySmall,
                            color = RiderTheme.colors.textTertiary,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
            }

            val remarks = state.ratings?.ratings.orEmpty().filter { !it.comment.isNullOrBlank() }

            if (remarks.isNotEmpty()) {
                SectionLabel("Remarks", Modifier.padding(top = 24.dp, bottom = 12.dp))

                remarks.forEach { rating ->
                    RiderCard(modifier = Modifier.padding(bottom = 10.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Icon(
                                Icons.Filled.Star,
                                contentDescription = null,
                                tint = RiderTheme.colors.cash,
                                modifier = Modifier.size(15.dp),
                            )
                            Text(
                                text = "${rating.score}",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                        Text(
                            text = rating.comment.orEmpty(),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                }
            }
        }
    }

    Spacer(Modifier.height(20.dp))
}

// -- Support ----------------------------------------------------------------

/**
 * How to reach a human.
 *
 * Every channel is rendered only if the server sent one. An unset
 * `SUPPORT_PHONE` produces no call button rather than a button that dials
 * nowhere, which is the whole reason the config has no default — see
 * config/support.php.
 */
@Composable
internal fun SupportPanel(state: AccountUiState) {
    val context = LocalContext.current
    val support = state.support

    SectionLabel("Help and support", Modifier.padding(top = 20.dp, bottom = 12.dp))

    RiderCard {
        Text(
            text = "If something has gone wrong on a delivery — the shop is closed, nobody is " +
                "at the door, you have had an accident — tell us and we will sort it with " +
                "the shop.",
            style = MaterialTheme.typography.bodySmall,
            color = RiderTheme.colors.textSecondary,
        )

        support?.hours?.takeIf { it.isNotBlank() }?.let { hours ->
            Text(
                text = hours,
                style = MaterialTheme.typography.bodySmall,
                color = RiderTheme.colors.textTertiary,
                modifier = Modifier.padding(top = 10.dp),
            )
        }
    }

    support?.phone?.takeIf { it.isNotBlank() }?.let { phone ->
        SupportRow(
            label = "Call $phone",
            icon = Icons.Outlined.Phone,
            onClick = { context.openSafely(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone"))) },
        )
    }

    support?.email?.takeIf { it.isNotBlank() }?.let { email ->
        SupportRow(
            label = email,
            icon = Icons.Outlined.MailOutline,
            onClick = { context.openSafely(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$email"))) },
        )
    }

    Spacer(Modifier.height(20.dp))
}

@Composable
private fun SupportRow(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
) {
    RiderCard(modifier = Modifier.padding(top = 12.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp),
            )
            Text(label, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/**
 * Open an intent, or do nothing.
 *
 * A phone with no dialler and no mail client is unusual but real — a
 * data-only tablet, a stripped ROM — and crashing a rider's app because they
 * tapped an email address is not an acceptable answer to it.
 */
private fun android.content.Context.openSafely(intent: Intent) {
    try {
        startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        // Nothing sensible to say: the row simply does not respond.
    }
}
