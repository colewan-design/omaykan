package com.omaykan.rider.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.TwoWheeler
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.omaykan.rider.core.designsystem.Avatar
import com.omaykan.rider.core.designsystem.Canopy
import com.omaykan.rider.core.designsystem.CanopyIconButton
import com.omaykan.rider.core.designsystem.MoneyCard
import com.omaykan.rider.core.designsystem.OverviewTile
import com.omaykan.rider.core.designsystem.PillShape
import com.omaykan.rider.core.designsystem.RiderCard
import com.omaykan.rider.core.designsystem.RiderTheme
import com.omaykan.rider.core.designsystem.SectionLabel
import com.omaykan.rider.core.model.Money
import com.omaykan.rider.core.model.RiderProfile
import com.omaykan.rider.feature.common.Notice
import com.omaykan.rider.feature.work.CompletedRow

/**
 * Where a rider lands: today's money, what is in their hands, and what they
 * have just finished.
 *
 * ## Why this screen exists at all
 *
 * The board used to be the app. It carried a rider's name, three running totals
 * and the jobs, all in one column, which meant the two questions a rider
 * actually opens the app to ask — *what have I made today* and *what am I still
 * carrying* — were answered in a strip above a list that scrolled them away.
 * Splitting them puts the standing answers on a screen that never moves, and
 * leaves the board free to be nothing but jobs.
 *
 * ## The two cards, and why they are different colours
 *
 * The left one is money the rider has earned and keeps in full. The right one
 * is money they are carrying that belongs to a shop. They are the two halves of
 * a shift's cash and they are the two halves a rider most easily confuses at
 * eleven at night, so they are opposite hues rather than two greens, and the
 * right one offers no way to "settle" anything — see [cashInHand].
 *
 * ## What is deliberately not on it
 *
 * A withdraw button, a company balance, a cancellation count. There is no
 * platform cut on this product and no wallet to withdraw from — a fee is paid
 * in cash at a door — so a Withdraw button would be a control with nothing
 * behind it. Cancellations are not counted anywhere on the backend; a rider
 * hands a job back and the row goes to another rider, which is not a failure
 * and is not recorded as one.
 */
@Composable
fun HomeScreen(
    rider: RiderProfile,
    onSeeJobs: () -> Unit,
    onSeeEarnings: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val work by viewModel.work.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val sharing by viewModel.sharingState.collectAsStateWithLifecycle()

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Header(
            rider = rider,
            carrying = work.active.size,
            todayCents = state.earnings.today.feeCents,
            todayJobs = state.earnings.today.jobs,
            cashCents = work.cashInHand,
            onRefresh = viewModel::refresh,
            onSeeEarnings = onSeeEarnings,
            onSeeJobs = onSeeJobs,
        )

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            state.error?.let { Notice(it, warning = true) }
            work.error?.let { Notice(it, warning = true) }

            // The one switch in the app that is about the rider rather than
            // about a job, and it sits here rather than on the board because
            // this is the screen they land on. It is still off by default and
            // still says what it costs them before they press it.
            SharingSwitch(
                state = sharing,
                locationGranted = viewModel.locationGranted(),
                onChange = viewModel::setSharing,
                modifier = Modifier.padding(top = 16.dp),
            )

            SectionLabel("Your record", Modifier.padding(top = 24.dp, bottom = 12.dp))

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OverviewTile(
                    // All of them, from the aggregate endpoint — not the
                    // thirty the job list happens to have fetched.
                    value = state.earnings.allTime.jobs.toString(),
                    label = "Delivered",
                    icon = Icons.Filled.CheckCircle,
                    tint = RiderTheme.colors.success,
                    container = RiderTheme.colors.successSoft,
                    modifier = Modifier.weight(1f),
                )
                OverviewTile(
                    value = work.active.size.toString(),
                    label = "In your hands",
                    icon = Icons.Filled.TwoWheeler,
                    tint = MaterialTheme.colorScheme.primary,
                    container = RiderTheme.colors.accentSoft,
                    modifier = Modifier.weight(1f),
                )
            }

            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SectionLabel("Recent deliveries", Modifier.weight(1f))

                if (work.completed.isNotEmpty()) {
                    TextButton(onClick = onSeeJobs) {
                        Text("See all", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            if (work.completed.isEmpty()) {
                RiderCard {
                    Text(
                        text = "Nothing finished yet.",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "Take a job from the board and it will be listed here once " +
                            "it is at the door.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = RiderTheme.colors.textSecondary,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    // Five, not thirty. This is a glance at what just happened,
                    // and the full run is one tap away on the Jobs tab.
                    work.completed.take(5).forEach { CompletedRow(it) }
                }
            }

            Spacer(Modifier.height(28.dp))
        }
    }
}

/**
 * The canopy, carrying the greeting and both money cards.
 *
 * The cards sit *inside* the green block rather than under it, which is what
 * makes the top of this screen one object instead of a header with two tiles
 * loose beneath it. It also puts the two figures on the half of the screen a
 * thumb never covers.
 */
@Composable
private fun Header(
    rider: RiderProfile,
    carrying: Int,
    todayCents: Long,
    todayJobs: Int,
    cashCents: Long,
    onRefresh: () -> Unit,
    onSeeEarnings: () -> Unit,
    onSeeJobs: () -> Unit,
) {
    val firstName = rider.name.substringBefore(' ').ifBlank { "Rider" }

    Canopy {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Avatar(rider.name)

            Column(
                Modifier
                    .weight(1f)
                    .padding(start = 12.dp),
            ) {
                Text(
                    text = "Hello, $firstName",
                    style = MaterialTheme.typography.titleLarge,
                    color = RiderTheme.colors.onCanopy,
                    maxLines = 1,
                )
                Text(
                    text = if (carrying == 0) {
                        "Ready to start earning?"
                    } else {
                        "You are carrying $carrying right now."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = RiderTheme.colors.onCanopyMuted,
                    maxLines = 1,
                )
            }

            CanopyIconButton(Icons.Filled.Refresh, "Refresh", onRefresh)
        }

        // A status, not a control. The dot is lit when there is something in
        // the rider's hands, which is the one fact about a shift that changes
        // what every other number on this screen means.
        CarryingPill(carrying, Modifier.padding(top = 14.dp))

        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 14.dp, bottom = 22.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            MoneyCard(
                label = "Earned today",
                value = Money.peso(todayCents),
                caption = if (todayJobs == 1) {
                    "1 delivery, and the fee is yours in full"
                } else {
                    "$todayJobs deliveries, fees yours in full"
                },
                actionLabel = "Breakdown",
                onAction = onSeeEarnings,
                container = RiderTheme.colors.payout,
                modifier = Modifier.weight(1f),
            )

            MoneyCard(
                label = "Cash to collect",
                value = Money.peso(cashCents),
                caption = if (cashCents == 0L) {
                    "Nothing to hand back to a shop"
                } else {
                    "Collected at the door, owed to the shop"
                },
                actionLabel = "See the jobs",
                onAction = onSeeJobs,
                container = RiderTheme.colors.owed,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** "Carrying 2" or "Nothing in hand", as a dot and a word on the canopy. */
@Composable
private fun CarryingPill(carrying: Int, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(PillShape)
            .background(RiderTheme.colors.canopyFill)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            Modifier
                .size(8.dp)
                .clip(PillShape)
                .background(
                    if (carrying > 0) {
                        RiderTheme.colors.success
                    } else {
                        RiderTheme.colors.onCanopyMuted
                    },
                ),
        )
        Text(
            text = if (carrying > 0) "Carrying $carrying" else "Nothing in hand",
            style = MaterialTheme.typography.bodySmall,
            color = RiderTheme.colors.onCanopy,
        )
    }
}
