package com.omaykan.rider.feature.earnings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.omaykan.rider.core.designsystem.Canopy
import com.omaykan.rider.core.designsystem.CanopyIconButton
import com.omaykan.rider.core.designsystem.RiderCard
import com.omaykan.rider.core.designsystem.RiderTheme
import com.omaykan.rider.core.designsystem.SectionLabel
import com.omaykan.rider.core.model.Earnings
import com.omaykan.rider.core.model.EarningsDay
import com.omaykan.rider.core.model.Money
import com.omaykan.rider.feature.common.Notice

/**
 * What the rider has made.
 *
 * ## Why this is not the finished list with a total on it
 *
 * It nearly was. `GET /api/rider/deliveries` returns the last thirty finished
 * jobs and the work screen already sums them into the canopy — but thirty is a
 * fortnight for a busy rider, so "this month" was unanswerable and "all time"
 * was a number that silently stopped growing. The totals here are aggregated in
 * the database over every row, and this screen only draws them.
 *
 * ## Deliberately not a report
 *
 * No date picker, no per-shop breakdown, no export. A rider checking a phone
 * between jobs wants today, this week, and whether this week looks like last
 * one. The moment somebody needs more than that, it is an accounting screen and
 * should be built as one rather than grown here.
 */
@Composable
fun EarningsScreen(viewModel: EarningsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val earnings = state.earnings

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Canopy {
            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "Earnings",
                        style = MaterialTheme.typography.headlineMedium,
                        color = RiderTheme.colors.onCanopy,
                    )

                    Text(
                        // Said once, plainly. It is the platform's actual
                        // position rather than a promotion — see
                        // documentation/positioning.md — and it is the sentence
                        // an aggregator cannot say.
                        text = "Every peso of the fee is yours.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = RiderTheme.colors.onCanopyMuted,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }

                CanopyIconButton(
                    icon = Icons.Filled.Refresh,
                    description = "Refresh earnings",
                    onClick = viewModel::load,
                    enabled = !state.loading,
                )
            }

            Spacer(Modifier.height(24.dp))
        }

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            state.error?.let { Notice(it, warning = true) }

            // The spinner only before the first answer. A refresh over figures
            // that are already on screen should not blank them.
            if (state.loading && !state.loaded) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 60.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            } else {
                Today(earnings, Modifier.padding(top = 16.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Tile("This week", earnings.week.feeCents, earnings.week.jobs, Modifier.weight(1f))
                    Tile("This month", earnings.month.feeCents, earnings.month.jobs, Modifier.weight(1f))
                }

                SectionLabel("Last 14 days", Modifier.padding(top = 28.dp, bottom = 12.dp))

                RiderCard { Trend(earnings.days) }

                SectionLabel("All time", Modifier.padding(top = 28.dp, bottom = 12.dp))

                RiderCard {
                    TotalRow("Earned", Money.peso(earnings.allTime.feeCents))
                    HorizontalDivider(
                        Modifier.padding(vertical = 12.dp),
                        color = RiderTheme.colors.hairline,
                    )
                    TotalRow("Deliveries finished", earnings.allTime.jobs.toString())

                    if (earnings.allTime.jobs > 0) {
                        HorizontalDivider(
                            Modifier.padding(vertical = 12.dp),
                            color = RiderTheme.colors.hairline,
                        )
                        TotalRow("Average per delivery", Money.peso(earnings.averageFeeCents))
                    }
                }

                Text(
                    text = "Counted from the day you took each job, in Philippine time. " +
                        "Jobs you are still carrying are not in here — that is money you " +
                        "are about to make, not money you have made.",
                    style = MaterialTheme.typography.bodySmall,
                    color = RiderTheme.colors.textTertiary,
                    modifier = Modifier.padding(top = 16.dp),
                )
            }

            Spacer(Modifier.height(28.dp))
        }
    }
}

/** The one number a rider opens this screen for. */
@Composable
private fun Today(earnings: Earnings, modifier: Modifier = Modifier) {
    RiderCard(
        modifier = modifier,
        color = MaterialTheme.colorScheme.primary,
        padding = 24.dp,
    ) {
        Column(
            Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "TODAY",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.75f),
            )

            Text(
                // The full peso figure here, not the rounded one the board
                // uses: a fee is scanned while scrolling, and this is the
                // number a rider checks against the notes in their pocket.
                text = Money.peso(earnings.today.feeCents),
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.padding(top = 6.dp),
            )

            Text(
                text = "${earnings.today.jobs} " +
                    if (earnings.today.jobs == 1) "delivery finished" else "deliveries finished",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f),
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun Tile(label: String, cents: Long, jobs: Int, modifier: Modifier = Modifier) {
    RiderCard(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = RiderTheme.colors.textSecondary,
        )
        Text(
            text = Money.peso(cents),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(top = 4.dp),
        )
        Text(
            text = "$jobs ${if (jobs == 1) "job" else "jobs"}",
            style = MaterialTheme.typography.bodySmall,
            color = RiderTheme.colors.textTertiary,
        )
    }
}

@Composable
private fun TotalRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = RiderTheme.colors.textSecondary,
            modifier = Modifier.weight(1f),
        )
        Text(text = value, style = MaterialTheme.typography.titleMedium)
    }
}

/**
 * Fourteen bars, one per day, **including the days with nothing on them**.
 *
 * A chart that dropped the quiet days would compress a slow fortnight into a
 * busy-looking week. The empty ones keep a 2% stub for the same reason: a gap
 * where a bar should be reads as missing data rather than as a day off.
 */
@Composable
private fun Trend(days: List<EarningsDay>) {
    if (days.isEmpty()) {
        Text(
            text = "Nothing finished in the last two weeks.",
            style = MaterialTheme.typography.bodyMedium,
            color = RiderTheme.colors.textSecondary,
        )
        return
    }

    // The tallest bar sets the scale, so a quiet fortnight still has a shape.
    val peak = days.maxOf { it.feeCents }.coerceAtLeast(1)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(132.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        days.forEach { day ->
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom,
            ) {
                val fraction = (day.feeCents.toFloat() / peak).coerceAtLeast(0.02f)

                Box(
                    Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(fraction)
                        .clip(MaterialTheme.shapes.extraSmall)
                        .background(
                            if (day.feeCents > 0) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                RiderTheme.colors.hairline
                            },
                        ),
                )

                Text(
                    text = day.weekdayInitial(),
                    style = MaterialTheme.typography.labelSmall,
                    color = RiderTheme.colors.textTertiary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .padding(top = 6.dp)
                        .width(14.dp),
                )
            }
        }
    }
}

/**
 * One letter under each bar, worked out from the date string itself.
 *
 * Deliberately not `LocalDate.parse(...).dayOfWeek` formatted in the phone's
 * locale: the date is already a Manila day, and re-interpreting it through a
 * phone set to another timezone is how every label ends up shifted by one.
 * Zeller's congruence on the three numbers is exact and needs no timezone at
 * all.
 */
private fun EarningsDay.weekdayInitial(): String {
    val parts = date.split('-')
    if (parts.size != 3) return ""

    val year = parts[0].toIntOrNull() ?: return ""
    val month = parts[1].toIntOrNull() ?: return ""
    val dayOfMonth = parts[2].toIntOrNull() ?: return ""

    val shiftedMonth = if (month <= 2) month + 12 else month
    val shiftedYear = if (month <= 2) year - 1 else year
    val century = shiftedYear / 100
    val yearInCentury = shiftedYear % 100

    // 0 = Saturday, 1 = Sunday, 2 = Monday …
    val index = (
        dayOfMonth +
            (13 * (shiftedMonth + 1)) / 5 +
            yearInCentury +
            yearInCentury / 4 +
            century / 4 +
            5 * century
        ) % 7

    return listOf("S", "S", "M", "T", "W", "T", "F")[index]
}
