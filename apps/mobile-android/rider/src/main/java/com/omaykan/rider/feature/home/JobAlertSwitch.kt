package com.omaykan.rider.feature.home

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * The switch that tells a rider a job appeared while the app was closed.
 *
 * ## Why it heads the home screen
 *
 * It is the nearest true thing to the reference's "You're Online" toggle. There
 * is no online state on this platform — the board is the same board whether a
 * rider is looking or not — but "tell me when work appears, even with the app
 * shut" is exactly the decision a rider makes at the start and end of a shift,
 * which is when they are on this screen.
 *
 * ## Why it starts off
 *
 * A permanent notification and a poll every minute are not things to switch on
 * for somebody. A rider who has finished for the day and left the app installed
 * should not have their phone quietly working — and more to the point, an app
 * that decides on its own to run a service in the background is one people
 * uninstall.
 *
 * ## What the caption says when it is off
 *
 * The consequence, not the setting. "You only see new jobs while the app is
 * open" is the fact a rider needs to weigh; "off" tells them nothing they
 * cannot see from the switch itself.
 */
@Composable
fun JobAlertSwitch(
    watching: Boolean,
    onChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    // From API 33 the notification is itself a permission, and here it is not
    // merely cosmetic the way it is for the location service: a watcher that
    // cannot post is a watcher with nothing to show for itself. Asked at the
    // moment the rider says yes, which is the first moment there is anything to
    // notify about.
    val notificationRequest = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { _ ->
        // Started either way. A refused permission leaves the service running
        // and silent, which is a worse outcome than not starting — but it is
        // the rider's call to make from system settings, and refusing to start
        // would leave the switch fighting them.
        onChange(true)
    }

    ShiftToggleRow(
        on = watching,
        title = if (watching) "Watching for jobs" else "Job alerts are off",
        caption = if (watching) {
            "A notification for every new job, even with the app closed."
        } else {
            "You only see new jobs while the app is open."
        },
        modifier = modifier,
        onToggle = { wanted ->
            if (!wanted) {
                onChange(false)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                notificationRequest.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                onChange(true)
            }
        },
    )
}
