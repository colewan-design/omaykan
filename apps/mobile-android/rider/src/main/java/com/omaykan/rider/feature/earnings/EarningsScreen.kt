package com.omaykan.rider.feature.earnings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.omaykan.rider.core.designsystem.AccentDark
import com.omaykan.rider.core.designsystem.CanopyIconButton
import com.omaykan.rider.core.designsystem.ForestTopBar
import com.omaykan.rider.core.designsystem.IconBadge
import com.omaykan.rider.core.designsystem.PillShape
import com.omaykan.rider.core.designsystem.RiderCard
import com.omaykan.rider.core.designsystem.RiderTextStyles
import com.omaykan.rider.core.designsystem.RiderTheme
import com.omaykan.rider.core.designsystem.SectionLabel
import com.omaykan.rider.core.model.DeliveryAssignment
import com.omaykan.rider.core.model.Earnings
import com.omaykan.rider.core.model.EarningsDay
import com.omaykan.rider.core.model.EarningsTotal
import com.omaykan.rider.core.model.Money
import com.omaykan.rider.feature.common.Notice
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/** The four windows the server aggregates over, as the header's picker offers them. */
internal enum class EarningsPeriod(val label: String) {
    Today("Today"),
    Week("This week"),
    Month("This month"),
    AllTime("All time"),
}

/**
 * What the rider has made.
 *
 * ## The reference, and what stands in its slots
 *
 * A forest header with a period picker, the total, a week of bars, and a list
 * of deliveries grouped by day. Its COD / Paid Online split and the matching
 * filter chips are left out on purpose: `collectCents` on a finished job is
 * the order's payment status *now*, and a shop that marks a cash order paid
 * after the rider hands the money over would move it from one column to the
 * other. A split that changes after the fact is not a record. In its place are
 * the two figures that stay true — how many jobs, and what each paid on
 * average.
 *
 * ## Why this is not the finished list with a total on it
 *
 * It nearly was. `GET /api/rider/deliveries` returns the last thirty finished
 * jobs — a fortnight for a busy rider — so "this month" was unanswerable and
 * "all time" was a number that silently stopped growing. The totals here are
 * aggregated in the database over every row, and this screen only draws them.
 * The list underneath is those thirty, grouped, and each day's total is the
 * server's for that day rather than a sum of whatever happened to be fetched.
 *
 * ## Deliberately not a report
 *
 * No date picker, no per-shop breakdown, no export. A rider checking a phone
 * between jobs wants today, this week, and whether this week looks like the
 * last one.
 */
@Composable
fun EarningsScreen(viewModel: EarningsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val work by viewModel.work.collectAsStateWithLifecycle()

    EarningsContent(state = state, finished = work.completed, onRefresh = viewModel::refresh)
}

/** Stateless production layout, also rendered by the debug gallery. */
@Composable
internal fun EarningsContent(
    state: EarningsUiState,
    finished: List<DeliveryAssignment>,
    onRefresh: () -> Unit,
) {
    var period by rememberSaveable { mutableStateOf(EarningsPeriod.Week) }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        ForestTopBar(
            title = "Earnings",
            leading = {
                CanopyIconButton(
                    icon = Icons.Filled.Refresh,
                    description = "Refresh earnings",
                    onClick = onRefresh,
                    enabled = !state.loading,
                )
            },
            actions = {
                PeriodPicker(period = period, onSelect = { period = it }, modifier = Modifier.padding(end = 8.dp))
            },
        )

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
            Summary(earnings = state.earnings, period = period, loaded = state.loaded)

            Column(Modifier.padding(horizontal = 16.dp)) {
                state.error?.let { Notice(it, warning = true) }

                // The spinner only before the first answer. A refresh over
                // figures that are already on screen should not blank them.
                if (state.loading && !state.loaded) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 32.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                }

                RecentDeliveries(finished = finished, days = state.earnings.days)

                Text(
                    text = "Counted from the day you took each job, in Philippine time. " +
                        "Jobs you are still carrying are not in here — that is money you " +
                        "are about to make, not money you have made.",
                    style = MaterialTheme.typography.bodySmall,
                    color = RiderTheme.colors.textTertiary,
                    modifier = Modifier.padding(top = 16.dp),
                )

                Spacer(Modifier.height(28.dp))
            }
        }
    }
}

private fun Earnings.total(period: EarningsPeriod): EarningsTotal = when (period) {
    EarningsPeriod.Today -> today
    EarningsPeriod.Week -> week
    EarningsPeriod.Month -> month
    EarningsPeriod.AllTime -> allTime
}

/** "This week ▾" on the bar. */
@Composable
private fun PeriodPicker(
    period: EarningsPeriod,
    onSelect: (EarningsPeriod) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = RiderTheme.colors
    var open by remember { mutableStateOf(false) }

    Box(modifier) {
        Row(
            Modifier
                .clip(PillShape)
                .background(colors.canopyFill)
                .clickable(onClickLabel = "Choose a period") { open = true }
                .padding(start = 12.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = period.label,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color = colors.onCanopy,
            )
            Icon(
                Icons.Filled.ArrowDropDown,
                contentDescription = null,
                tint = colors.onCanopy,
                modifier = Modifier.size(20.dp),
            )
        }

        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            EarningsPeriod.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.label) },
                    onClick = {
                        onSelect(option)
                        open = false
                    },
                )
            }
        }
    }
}

/** The forest block under the bar: the total, two figures, and the week. */
@Composable
private fun Summary(earnings: Earnings, period: EarningsPeriod, loaded: Boolean) {
    val colors = RiderTheme.colors
    val total = earnings.total(period)

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = 22.dp, bottomEnd = 22.dp))
            .background(colors.canopy)
            .padding(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 20.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = when (period) {
                        EarningsPeriod.Today -> "Earned today"
                        EarningsPeriod.Week -> "Earned this week"
                        EarningsPeriod.Month -> "Earned this month"
                        EarningsPeriod.AllTime -> "Earned, all time"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onCanopyMuted,
                )
                Text(
                    // The full peso figure, not the rounded one the board
                    // uses: this is the number a rider checks against the notes
                    // in their pocket.
                    text = if (loaded) Money.peso(total.feeCents) else "—",
                    style = RiderTextStyles.money.copy(fontSize = 32.sp),
                    color = colors.onCanopy,
                    maxLines = 1,
                    modifier = Modifier.padding(top = 2.dp),
                )
                Text(
                    // Said once, plainly. It is the platform's actual position
                    // rather than a promotion — see documentation/positioning.md.
                    text = "Every peso of the fee is yours.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onCanopyMuted,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }

            Column(
                Modifier.padding(start = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                MiniStat("Deliveries", if (loaded) total.jobs.toString() else "—")
                MiniStat(
                    "Per delivery",
                    if (loaded && total.jobs > 0) Money.pesoRounded(total.feeCents / total.jobs) else "—",
                )
            }
        }

        WeekChart(days = earnings.days, modifier = Modifier.padding(top = 20.dp))
    }
}

@Composable
private fun MiniStat(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.bodySmall, color = RiderTheme.colors.onCanopyMuted)
        Text(value, style = MaterialTheme.typography.titleMedium, color = RiderTheme.colors.onCanopy)
    }
}

/**
 * Seven bars, one per day, **including the days with nothing on them**, with
 * today lit.
 *
 * A chart that dropped the quiet days would compress a slow week into a
 * busy-looking one. The empty ones keep a stub for the same reason: a gap
 * where a bar should be reads as missing data rather than as a day off.
 *
 * The comparison is the last seven days against the seven before them — both
 * out of the same fourteen the server sends — and says so. It is deliberately
 * not set against the "This week" total, which is a calendar week and would
 * make the percentage a comparison of two different windows.
 */
@Composable
private fun WeekChart(days: List<EarningsDay>, modifier: Modifier = Modifier) {
    val colors = RiderTheme.colors
    val week = days.takeLast(7)
    val before = days.dropLast(7).takeLast(7)

    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Last 7 days",
                style = MaterialTheme.typography.bodySmall,
                color = colors.onCanopyMuted,
                modifier = Modifier.weight(1f),
            )
            trendLabel(week, before)?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = colors.onCanopy,
                    modifier = Modifier
                        .clip(PillShape)
                        .background(colors.canopyFill)
                        .padding(horizontal = 10.dp, vertical = 3.dp),
                )
            }
        }

        if (week.isEmpty()) {
            Text(
                text = "Nothing to chart yet.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.onCanopyMuted,
                modifier = Modifier.padding(top = 12.dp),
            )
            return@Column
        }

        // The tallest bar sets the scale, so a quiet week still has a shape.
        val peak = week.maxOf { it.feeCents }.coerceAtLeast(1)

        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 12.dp)
                .height(112.dp)
                .clearAndSetSemantics {
                    contentDescription = "Last 7 days: " + week.joinToString {
                        "${it.weekdayShort()} ${Money.pesoRounded(it.feeCents)}"
                    }
                },
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            week.forEachIndexed { index, day ->
                val today = index == week.lastIndex

                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.BottomCenter,
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .fillMaxHeight((day.feeCents.toFloat() / peak).coerceAtLeast(0.03f))
                                .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp, bottomStart = 2.dp, bottomEnd = 2.dp))
                                .background(
                                    // Sage on forest in both themes: the chart
                                    // always sits on the forest block.
                                    if (today) AccentDark else colors.onCanopy.copy(alpha = 0.26f),
                                ),
                        )
                    }
                    Text(
                        text = day.weekdayShort(),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (today) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (today) colors.onCanopy else colors.onCanopyMuted,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
        }
    }
}

private fun trendLabel(week: List<EarningsDay>, before: List<EarningsDay>): String? {
    if (week.size < 7 || before.size < 7) return null
    val now = week.sumOf { it.feeCents }
    val then = before.sumOf { it.feeCents }
    if (then <= 0) return null

    val change = ((now - then) * 100.0 / then).roundToInt()
    return when {
        change > 0 -> "↑ $change% vs the 7 days before"
        change < 0 -> "↓ ${-change}% vs the 7 days before"
        else -> "Level with the 7 days before"
    }
}

/**
 * The finished jobs, grouped by the Manila day each was taken.
 *
 * Grouped on `acceptedAt` because that is the day the server counts a job
 * under, so a group's heading total — the server's own figure for that date —
 * and the rows beneath it describe the same set of jobs.
 */
@Composable
private fun RecentDeliveries(finished: List<DeliveryAssignment>, days: List<EarningsDay>) {
    SectionLabel("Recent deliveries", Modifier.padding(top = 20.dp))

    if (finished.isEmpty()) {
        RiderCard(Modifier.padding(top = 12.dp)) {
            Text(
                text = "Nothing finished yet.",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "Take a job from the board and it will be listed here once it is at the door.",
                style = MaterialTheme.typography.bodyMedium,
                color = RiderTheme.colors.textSecondary,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        return
    }

    val today = LocalDate.now(Manila)

    finished.groupBy { it.acceptedAt.manilaDate() }.forEach { (date, jobs) ->
        val dayTotal = date?.let { d -> days.firstOrNull { it.date == d.toString() }?.feeCents }

        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 16.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = dayLabel(date, today),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            dayTotal?.let {
                Text(
                    text = Money.pesoRounded(it),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }

        RiderCard(padding = 0.dp) {
            jobs.forEachIndexed { index, job ->
                if (index > 0) {
                    HorizontalDivider(Modifier.padding(start = 62.dp), color = RiderTheme.colors.hairline)
                }
                DeliveryRow(job)
            }
        }
    }
}

@Composable
private fun DeliveryRow(job: DeliveryAssignment) {
    val colors = RiderTheme.colors

    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBadge(Icons.Filled.CheckCircle, tint = colors.success, container = colors.successSoft, size = 36.dp)

        Column(
            Modifier
                .weight(1f)
                .padding(horizontal = 12.dp),
        ) {
            Text(
                text = job.offer.ticketNumber?.let { "#$it" } ?: "Delivery",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textTertiary,
            )
            Text(
                text = job.customerName ?: job.offer.dropoffArea ?: "Delivered",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "From ${job.offer.pickup.storeName}",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = Money.pesoRounded(job.offer.deliveryFeeCents),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            job.acceptedAt.manilaTime()?.let {
                Text("Taken $it", style = MaterialTheme.typography.bodySmall, color = colors.textTertiary)
            }
        }
    }
}

// -- Dates ------------------------------------------------------------------
//
// Everything on this screen is a Manila day, because that is the day the
// server counts a job under. A phone set to another timezone still groups and
// labels by the rider's working day, not by its own clock.

private val Manila: ZoneId = ZoneId.of("Asia/Manila")
private val DayFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH)
private val TimeFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)

/** Tolerant of the three shapes a Laravel timestamp arrives in. */
private fun String?.instant(): Instant? {
    if (isNullOrBlank()) return null
    return runCatching { OffsetDateTime.parse(this).toInstant() }.getOrNull()
        ?: runCatching { Instant.parse(this) }.getOrNull()
        ?: runCatching { LocalDateTime.parse(replace(' ', 'T')).atZone(Manila).toInstant() }.getOrNull()
}

private fun String?.manilaDate(): LocalDate? = instant()?.atZone(Manila)?.toLocalDate()

private fun String?.manilaTime(): String? = instant()?.atZone(Manila)?.let { TimeFormat.format(it) }

private fun dayLabel(date: LocalDate?, today: LocalDate): String = when (date) {
    null -> "Earlier"
    today -> "Today · ${DayFormat.format(date)}"
    today.minusDays(1) -> "Yesterday · ${DayFormat.format(date)}"
    else -> "${WeekdayNames[weekdayIndex(date.year, date.monthValue, date.dayOfMonth)]} · " +
        DayFormat.format(date)
}

/** Zeller's order: 0 = Saturday, 1 = Sunday, 2 = Monday … */
private val WeekdayNames = listOf("Sat", "Sun", "Mon", "Tue", "Wed", "Thu", "Fri")

/**
 * "Mon", worked out from the date string itself.
 *
 * Deliberately not `LocalDate.parse(...).dayOfWeek` formatted in the phone's
 * locale: the date is already a Manila day, and nothing here should get the
 * chance to re-interpret it. Zeller's congruence on the three numbers is exact
 * and needs no timezone at all.
 */
private fun EarningsDay.weekdayShort(): String {
    val parts = date.split('-')
    if (parts.size != 3) return ""

    val year = parts[0].toIntOrNull() ?: return ""
    val month = parts[1].toIntOrNull() ?: return ""
    val dayOfMonth = parts[2].toIntOrNull() ?: return ""

    return WeekdayNames[weekdayIndex(year, month, dayOfMonth)]
}

private fun weekdayIndex(year: Int, month: Int, dayOfMonth: Int): Int {
    val shiftedMonth = if (month <= 2) month + 12 else month
    val shiftedYear = if (month <= 2) year - 1 else year
    val century = shiftedYear / 100
    val yearInCentury = shiftedYear % 100

    return (
        dayOfMonth +
            (13 * (shiftedMonth + 1)) / 5 +
            yearInCentury +
            yearInCentury / 4 +
            century / 4 +
            5 * century
        ) % 7
}
