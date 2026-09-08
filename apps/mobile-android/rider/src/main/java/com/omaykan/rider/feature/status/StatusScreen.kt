package com.omaykan.rider.feature.status

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.omaykan.rider.core.designsystem.Canopy
import com.omaykan.rider.core.designsystem.CanopyIconButton
import com.omaykan.rider.core.designsystem.GhostButton
import com.omaykan.rider.core.designsystem.IconBadge
import com.omaykan.rider.core.designsystem.PillShape
import com.omaykan.rider.core.designsystem.PrimaryButton
import com.omaykan.rider.core.designsystem.RiderCard
import com.omaykan.rider.core.designsystem.RiderTheme
import com.omaykan.rider.core.designsystem.SectionLabel
import com.omaykan.rider.core.model.RiderProfile
import com.omaykan.rider.core.model.RiderStatus

/**
 * The screen for a rider who is signed in and cannot work yet.
 *
 * Three accounts land here — pending, rejected, suspended — and they are three
 * different letters, not one error with three severities. The server keeps
 * these accounts signed in for exactly this reason: `RiderAuthController`
 * mints a token for a pending rider so they have a way back to their own
 * status, and lets a rejected one sign in because being told why is the only
 * route they have to an operator.
 *
 * The review note is the whole point of the rejected and suspended cases. It is
 * a human's sentence about a specific licence photograph, and it is what turns
 * "no" into something a rider can act on.
 */
@Composable
fun StatusScreen(
    rider: RiderProfile,
    viewModel: StatusViewModel = hiltViewModel(),
) {
    val checking by viewModel.checking.collectAsStateWithLifecycle()

    // Ask once when the screen appears. Keyed on the rider so it fires again if
    // the account underneath changes — signing out and back in as somebody else
    // on a shared phone, which is a real thing riders do.
    LaunchedEffect(rider.id) { viewModel.check() }

    val (icon, tint) = when (rider.status) {
        RiderStatus.Pending -> Icons.Filled.HourglassTop to RiderTheme.colors.warning
        RiderStatus.Suspended -> Icons.Filled.PauseCircle to RiderTheme.colors.warning
        // Rejected, and Approved — which cannot reach this screen, since the
        // session sends an approved rider to the board. The `when` is
        // exhaustive rather than defaulting, so adding a fifth status is a
        // compile error here instead of a silent wrong icon.
        RiderStatus.Rejected, RiderStatus.Approved ->
            Icons.Filled.Block to RiderTheme.colors.danger
    }

    // Canopy fixed, the rest scrolling — see SignInScreen for why.
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Canopy {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                CanopyIconButton(
                    icon = Icons.AutoMirrored.Filled.Logout,
                    description = "Sign out",
                    onClick = viewModel::signOut,
                )
            }

            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                IconBadge(
                    icon = icon,
                    tint = tint,
                    container = RiderTheme.colors.canopyFill,
                    size = 72.dp,
                )

                Text(
                    text = when (rider.status) {
                        RiderStatus.Pending -> "We are checking your documents"
                        RiderStatus.Rejected -> "Your application was not approved"
                        RiderStatus.Suspended -> "Your account is on hold"
                        RiderStatus.Approved -> "You are approved"
                    },
                    style = MaterialTheme.typography.headlineMedium,
                    color = RiderTheme.colors.onCanopy,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 20.dp),
                )

                Text(
                    text = when (rider.status) {
                        RiderStatus.Pending ->
                            "Somebody is looking at your licence and plate. It usually takes " +
                                "a few hours. Nothing else is needed from you — open the app " +
                                "again later and jobs will be here."
                        RiderStatus.Rejected ->
                            "You can send a message to the team and ask them to look again."
                        RiderStatus.Suspended ->
                            "You cannot take jobs while an account is on hold. The team can " +
                                "tell you what is needed to lift it."
                        RiderStatus.Approved -> "Loading your jobs."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = RiderTheme.colors.onCanopyMuted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 10.dp),
                )
            }
        }

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (rider.status == RiderStatus.Pending) {
                SectionLabel("Where it is up to")
                RiderCard { ReviewSteps() }
            }

            rider.reviewNote?.let { note ->
                SectionLabel("What the team said", modifier = Modifier.padding(top = 8.dp))
                RiderCard {
                    Text(
                        text = note,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }

            SectionLabel("What you sent us", modifier = Modifier.padding(top = 8.dp))

            RiderCard {
                // So a rider can see the licence number was typed wrong without
                // ringing anybody to find out.
                Detail("Name", rider.name)
                Detail("Email", rider.email)
                Detail("Mobile", rider.phone)
                Detail("Licence", rider.licenseNumber)
                Detail("Plate", rider.plateNumber, last = true)
            }

            PrimaryButton(
                text = "Check again",
                onClick = viewModel::check,
                busy = checking,
                modifier = Modifier.padding(top = 14.dp),
            )

            GhostButton(
                text = "Sign out",
                icon = null,
                onClick = viewModel::signOut,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(24.dp))
        }
    }
}

/**
 * The three stages of a review, as a rail.
 *
 * Only shown while an application is pending, and that restriction is the
 * point: a rail implies a journey that ends well, and drawing one under
 * "not approved" would promise a fourth step that does not exist. A rejected
 * rider gets the review note instead, which is the only thing that can actually
 * move their application.
 *
 * The dates are deliberately absent. The server sends `reviewed_at` only once
 * somebody has decided, so the honest version of this rail is an order of
 * events rather than a schedule — a timestamp here would be a promise about
 * when, made by a screen that does not know.
 */
@Composable
private fun ReviewSteps() {
    Step("Application sent", "Your licence and plate reached us.", done = true)
    Rail()
    Step("Someone is checking it", "A person, not a script. Usually a few hours.", current = true)
    Rail()
    Step("Cleared to take jobs", "The board opens by itself when it happens.")
}

@Composable
private fun Step(
    title: String,
    detail: String,
    done: Boolean = false,
    current: Boolean = false,
) {
    val tint = when {
        done -> RiderTheme.colors.success
        current -> RiderTheme.colors.warning
        else -> RiderTheme.colors.textTertiary
    }

    Row(verticalAlignment = Alignment.Top) {
        Box(
            Modifier
                .padding(top = 5.dp)
                .size(if (done || current) 12.dp else 10.dp)
                .clip(PillShape)
                .background(tint),
        )
        Column(Modifier.padding(start = 14.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                color = RiderTheme.colors.textSecondary,
            )
        }
    }
}

/** The line joining two [Step]s, aligned to the dots. */
@Composable
private fun Rail() {
    Box(Modifier.width(12.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .padding(vertical = 4.dp)
                .width(2.dp)
                .height(16.dp)
                .background(RiderTheme.colors.separator),
        )
    }
}

@Composable
private fun Detail(label: String, value: String, last: Boolean = false) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(bottom = if (last) 0.dp else 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = RiderTheme.colors.textSecondary,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
