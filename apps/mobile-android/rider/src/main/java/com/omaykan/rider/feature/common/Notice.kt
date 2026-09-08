package com.omaykan.rider.feature.common

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.omaykan.rider.core.designsystem.RiderTheme
import com.omaykan.rider.core.designsystem.SoftBanner

/**
 * A sentence that is not a field error.
 *
 * Kept visually distinct from the red under a text field, because the two are
 * different claims: an error says "what you typed is wrong", and this says
 * "something happened that you should know about". "Another rider took that
 * one" is exactly the second, and colouring it as a mistake would tell somebody
 * off for tapping a job that was there when they looked.
 *
 * The two forms differ in ground rather than in wording weight — green for a
 * thing that happened, red for a thing that failed — which is the same pair the
 * cards use for cash and for a completed drop. One vocabulary of tinted bands
 * across the app, so a colour means the same thing wherever it appears.
 */
@Composable
fun Notice(
    text: String,
    modifier: Modifier = Modifier,
    warning: Boolean = false,
) {
    SoftBanner(
        icon = if (warning) Icons.Filled.WarningAmber else Icons.Filled.Info,
        text = text,
        tint = if (warning) RiderTheme.colors.danger else RiderTheme.colors.accentPressed,
        container = if (warning) RiderTheme.colors.dangerSoft else RiderTheme.colors.accentSoft,
        modifier = modifier.padding(top = 12.dp),
    )
}
