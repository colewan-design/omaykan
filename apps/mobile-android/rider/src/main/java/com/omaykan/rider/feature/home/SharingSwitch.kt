package com.omaykan.rider.feature.home

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOff
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.omaykan.rider.core.designsystem.IconBadge
import com.omaykan.rider.core.designsystem.RiderCard
import com.omaykan.rider.core.designsystem.RiderTheme
import com.omaykan.rider.core.location.SharingState

/**
 * The switch that puts the rider on the customer's map.
 *
 * ## Why it is on the home screen
 *
 * It used to sit above the job board, which was the app's first screen. The
 * home screen is that screen now, and this belongs on whichever one a rider
 * lands on: somebody who wants to stop being watched should find the control
 * without going looking for it, and a settings page two taps away fails that
 * test in exactly the moment it matters.
 *
 * ## Why it is a switch and not a consequence of taking a job
 *
 * The tempting design is to start sharing automatically the moment a rider
 * accepts something — it is fewer taps and it guarantees the map works. It is
 * also the design where a person finds out their employer's app has been
 * reporting their position all afternoon because of a button they pressed for
 * a different reason. A rider's location is theirs, the platform's interest in
 * it is real but secondary, and the resolution of that is an explicit switch
 * that starts off.
 *
 * What the platform can fairly do is make the case, which is the line under the
 * switch: sharing is what lets the shop stop ringing to ask where you are.
 *
 * ## What it says when it is on
 *
 * Not just "on". The count of who is actually watching, because "sharing with
 * nobody" and "sharing with two customers waiting on you" are different facts,
 * and a rider between jobs should be able to see that the answer is nobody.
 */
@Composable
fun SharingSwitch(
    state: SharingState,
    locationGranted: Boolean,
    onChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Both permissions in one prompt. Android shows a single dialog with the
    // precise/approximate choice on it, and a rider who picks approximate gets
    // a working feature rather than a refusal.
    val locationRequest = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { granted ->
        // Either one is enough. Approximate location on a delivery map is a
        // neighbourhood instead of a street, which beats an empty map.
        if (granted.values.any { it }) onChange(true)
    }

    // From API 33 the standing notification is itself a permission. Asked here
    // rather than on launch, because this is the first moment there is
    // something to notify about — and denied is survivable: the service still
    // runs, Android just posts its own minimal notice in place of ours.
    val notificationRequest = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { _ -> }

    RiderCard(modifier = modifier, padding = 14.dp) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // The disc is lit only while the feed is actually alive, so the
            // card answers "am I on the map" from across a handlebar mount,
            // before the sentence under it has been read.
            IconBadge(
                icon = if (state.sharing) Icons.Filled.LocationOn else Icons.Filled.LocationOff,
                tint = if (state.sharing) {
                    MaterialTheme.colorScheme.primary
                } else {
                    RiderTheme.colors.textTertiary
                },
                container = if (state.sharing) {
                    RiderTheme.colors.accentSoft
                } else {
                    RiderTheme.colors.fill
                },
            )

            Column(Modifier.weight(1f)) {
                Text(
                    text = if (state.sharing) "Sharing your location" else "Share your location",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = state.caption(),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (state.error != null) {
                        MaterialTheme.colorScheme.error
                    } else {
                        RiderTheme.colors.textTertiary
                    },
                )
            }

            Switch(
                checked = state.sharing,
                onCheckedChange = { wanted ->
                    if (!wanted) {
                        onChange(false)
                        return@Switch
                    }

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        notificationRequest.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }

                    if (locationGranted) {
                        onChange(true)
                    } else {
                        locationRequest.launch(
                            arrayOf(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION,
                            ),
                        )
                    }
                },
            )
        }
    }
}

/**
 * The line under the switch.
 *
 * Four different sentences for four different states, because "Sharing: on" is
 * the version that leaves a rider unable to tell a working feed from a broken
 * one — and the person who suffers for that is the customer watching a marker
 * that stopped.
 */
private fun SharingState.caption(): String = when {
    error != null -> "Cannot reach the server. Still trying."
    !sharing -> "The shop and the customer can see where you are, so they stop ringing to ask."
    acquiring -> "Finding your position…"
    listeners == 0 -> "Nobody is watching — you are not carrying anything right now."
    listeners == 1 -> "Visible to the shop and customer on your current delivery."
    else -> "Visible to the shops and customers on your $listeners deliveries."
}
