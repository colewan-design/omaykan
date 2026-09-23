package com.omaykan.seller.feature.shell

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState

/**
 * The order-alert switch's behaviour, shared by the home card and the account
 * row so the two can never ask for the permission differently.
 *
 * The notification permission is asked for when the switch is turned on, not
 * at launch. Asking at launch is asking before the merchant has seen what the
 * app is for, and a prompt dismissed then is dismissed for good; asking here
 * means the question arrives the moment they have said they want to be told
 * about orders.
 *
 * Denied is a supported state: the watcher still runs and the list still
 * updates. What is lost is the sound, not the work.
 */
@Composable
fun rememberAlertToggle(setWatching: (Boolean) -> Unit): (Boolean) -> Unit {
    val current = rememberUpdatedState(setWatching)
    val permission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { current.value(true) }

    return remember(permission) {
        { wanted: Boolean ->
            when {
                !wanted -> current.value(false)
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ->
                    permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                else -> current.value(true)
            }
        }
    }
}
