package com.omaykan.storefront.feature.update

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.omaykan.storefront.core.designsystem.OmaykanTheme

/**
 * "There's a newer version."
 *
 * A dialog rather than a banner, and this is the one place in the app that
 * interrupts: a sideloaded build has no store behind it, so an update the
 * shopper scrolls past is an update that never happens. It earns the
 * interruption by being rare — once per published version, then never again for
 * that version.
 *
 * Tapping through hands the APK to the browser and Android's own installer.
 * The app never downloads or installs anything itself, which keeps it out of
 * REQUEST_INSTALL_PACKAGES and out of the business of verifying a binary.
 */
@Composable
fun UpdatePrompt(viewModel: UpdateViewModel = hiltViewModel()) {
    val offer by viewModel.offer.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val update = offer ?: return
    val url = update.apkUrl ?: return

    AlertDialog(
        onDismissRequest = viewModel::dismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Text(
                "Version ${update.versionName} is out",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = OmaykanTheme.colors.ink,
            )
        },
        text = {
            Text(
                update.notes ?: "A newer build of Omaykan is ready to install.",
                style = MaterialTheme.typography.bodyMedium,
                color = OmaykanTheme.colors.textSecondary,
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val opened = runCatching {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, url.toUri())
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    }.exceptionOrNull() !is ActivityNotFoundException

                    // A phone with no browser cannot be helped from here, and
                    // leaving the dialog up is more honest than closing it on a
                    // tap that did nothing.
                    if (opened) viewModel.onOpened()
                },
            ) {
                Text("Get it", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
            }
        },
        dismissButton = {
            TextButton(onClick = viewModel::dismiss) {
                Text("Later", color = OmaykanTheme.colors.textSecondary)
            }
        },
    )
}
