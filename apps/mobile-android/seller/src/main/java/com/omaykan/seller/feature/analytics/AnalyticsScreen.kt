package com.omaykan.seller.feature.analytics

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.omaykan.seller.core.data.OrderFeed
import com.omaykan.seller.core.designsystem.ForestTopBar
import com.omaykan.seller.core.designsystem.Pill
import com.omaykan.seller.core.designsystem.PillShape
import com.omaykan.seller.core.designsystem.SectionHeader
import com.omaykan.seller.core.designsystem.SellerTheme
import com.omaykan.seller.core.designsystem.SoftCard
import com.omaykan.seller.core.model.DaySales
import com.omaykan.seller.core.model.Money
import com.omaykan.seller.core.model.SellerOrder
import com.omaykan.seller.core.model.WeekSales
import com.omaykan.seller.core.model.relativeStamp
import com.omaykan.seller.core.model.weekSales
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.util.Locale
import javax.inject.Inject
import java.time.format.TextStyle as DayNameStyle

data class AnalyticsUiState(
    val week: WeekSales? = null,
    val recent: List<SellerOrder> = emptyList(),
    val loading: Boolean = true,
    val error: String? = null,
)

/**
 * The week, from the order list the app already polls.
 *
 * No analytics endpoint exists for a shop's phone, and inventing one here
 * would be a second opinion on the same orders. So this is arithmetic over
 * [OrderFeed] — the same list the orders tab shows — and it says plainly
 * where that list runs out. See WeekSales.complete.
 */
@HiltViewModel
class AnalyticsViewModel @Inject constructor(feed: OrderFeed) : ViewModel() {
    val state: StateFlow<AnalyticsUiState> = feed.state
        .map { loaded ->
            AnalyticsUiState(
                week = if (loaded.loading && loaded.orders.isEmpty()) null else loaded.orders.weekSales(),
                recent = loaded.orders.take(RECENT),
                loading = loaded.loading,
                error = loaded.error,
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AnalyticsUiState())

    private companion object {
        const val RECENT = 8
    }
}

/**
 * "Sales": the reference's Earnings screen, minus the payouts.
 *
 * There are no payouts on this platform to list. A customer pays the shop —
 * cash at the door, GCash to the shop's own number — and the platform never
 * holds the money, so a "Recent Payouts" list here would be inventing
 * transfers that do not happen. The space goes to the recent orders and
 * whether each one has been paid, which is the question a payout list is
 * really answering.
 */
@Composable
fun AnalyticsScreen(viewModel: AnalyticsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        ForestTopBar(title = "Sales")

        val week = state.week
        if (week == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(strokeWidth = 2.dp)
            }
            return@Column
        }

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            Row(
                Modifier
                    .clip(PillShape)
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.CalendarMonth, null, tint = SellerTheme.colors.textSecondary, modifier = Modifier.size(16.dp))
                Text(
                    text = "Last 7 days",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(start = 6.dp),
                )
            }

            state.error?.let {
                Text(
                    text = "$it Showing the last orders that loaded.",
                    style = MaterialTheme.typography.bodySmall,
                    color = SellerTheme.colors.warning,
                    modifier = Modifier.padding(top = 10.dp),
                )
            }

            SummaryCard(week, Modifier.padding(top = 14.dp))

            if (!week.complete) {
                Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.Top) {
                    Icon(Icons.Filled.Info, null, tint = SellerTheme.colors.warning, modifier = Modifier.size(16.dp))
                    Text(
                        text = "This week holds more than the last $ORDER_CAP_TEXT orders the app can " +
                            "read, so these figures are a floor, not the full total.",
                        style = MaterialTheme.typography.bodySmall,
                        color = SellerTheme.colors.textSecondary,
                        modifier = Modifier.padding(start = 6.dp),
                    )
                }
            }

            Text(
                text = "Storefront orders only — counter sales stay on the till.",
                style = MaterialTheme.typography.bodySmall,
                color = SellerTheme.colors.textTertiary,
                modifier = Modifier.padding(top = 10.dp),
            )

            SectionHeader(title = "Recent Orders", modifier = Modifier.padding(top = 22.dp, bottom = 10.dp))

            if (state.recent.isEmpty()) {
                Text(
                    text = "No storefront orders yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = SellerTheme.colors.textTertiary,
                )
            } else {
                SoftCard(Modifier.fillMaxWidth()) {
                    state.recent.forEachIndexed { index, order ->
                        if (index > 0) HorizontalDivider(color = SellerTheme.colors.separator)
                        RecentRow(order)
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

private const val ORDER_CAP_TEXT = "100"

@Composable
private fun SummaryCard(week: WeekSales, modifier: Modifier = Modifier) {
    SoftCard(modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1.4f)) {
                    Label("Total sales")
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = Money.pesoRounded(week.totalCents),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = SellerTheme.colors.ink,
                            maxLines = 1,
                        )
                        week.changePercent?.let { change ->
                            Spacer(Modifier.width(6.dp))
                            Pill(
                                text = (if (change >= 0) "+" else "") + "$change%",
                                tint = if (change >= 0) SellerTheme.colors.success else SellerTheme.colors.danger,
                                dense = true,
                                leading = if (change >= 0) {
                                    Icons.AutoMirrored.Filled.TrendingUp
                                } else {
                                    Icons.AutoMirrored.Filled.TrendingDown
                                },
                            )
                        }
                    }
                }
                Figure("Orders", week.orderCount.toString(), Modifier.weight(0.8f))
                Figure("Average order", week.averageCents?.let { Money.pesoRounded(it) } ?: "—", Modifier.weight(1f))
            }

            WeekBars(week.days, today = LocalDate.now())
        }
    }
}

@Composable
private fun Label(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = SellerTheme.colors.textTertiary,
    )
}

@Composable
private fun Figure(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier.padding(start = 8.dp)) {
        Label(label)
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = SellerTheme.colors.ink,
            maxLines = 1,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

/** How tall the tallest bar is; everything else is a share of it. */
private val BAR_AREA = 110.dp

/**
 * Seven bars, one a day, today's in terracotta so "where am I" needs no
 * reading. Plain boxes rather than a chart library: seven rectangles are not
 * worth a dependency, and boxes inherit the theme for free.
 */
@Composable
private fun WeekBars(days: List<DaySales>, today: LocalDate) {
    val max = days.maxOfOrNull { it.cents }?.coerceAtLeast(1L) ?: 1L

    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 18.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        days.forEach { day ->
            val isToday = day.date == today
            Column(
                Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = if (day.cents > 0) compactPeso(day.cents) else "",
                    fontSize = 10.sp,
                    color = SellerTheme.colors.textTertiary,
                    maxLines = 1,
                    textAlign = TextAlign.Center,
                )
                val share = day.cents.toFloat() / max
                Box(
                    Modifier
                        .padding(top = 4.dp)
                        .fillMaxWidth(0.72f)
                        .height(if (day.cents > 0) (BAR_AREA * share).coerceAtLeast(4.dp) else 3.dp)
                        .clip(RoundedCornerShape(topStart = 5.dp, topEnd = 5.dp))
                        .background(
                            when {
                                day.cents == 0L -> SellerTheme.colors.fill
                                isToday -> MaterialTheme.colorScheme.primary
                                else -> SellerTheme.colors.canopy
                            },
                        ),
                )
                Text(
                    text = day.date.dayOfWeek.getDisplayName(DayNameStyle.SHORT, Locale.getDefault()),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = if (isToday) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (isToday) MaterialTheme.colorScheme.primary else SellerTheme.colors.textTertiary,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
    }
}

/** ₱850, 3.2K, 1.4M — a label that fits over a bar a thumb wide. */
private fun compactPeso(cents: Long): String {
    val pesos = cents / 100.0
    fun short(value: Double, suffix: String) =
        String.format(Locale.US, "%.1f", value).removeSuffix(".0") + suffix
    return when {
        pesos >= 1_000_000 -> short(pesos / 1_000_000, "M")
        pesos >= 1_000 -> short(pesos / 1_000, "K")
        // Whole pesos by dropping centavos, the way Money.pesoRounded does —
        // rounding here had a ₱317.80 day read ₱317 in the total and ₱318
        // on its own bar.
        else -> "₱" + (cents / 100)
    }
}

@Composable
private fun RecentRow(order: SellerOrder) {
    Row(
        Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = order.ticketNumber?.let { "#$it" } ?: "Order",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = listOfNotNull(relativeStamp(order.placedAt), order.customerName).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = SellerTheme.colors.textTertiary,
                maxLines = 1,
            )
        }
        Text(
            text = Money.peso(order.totalCents),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = SellerTheme.colors.ink,
            modifier = Modifier.padding(horizontal = 10.dp),
        )
        Pill(
            text = if (order.paid) "Paid" else "Unpaid",
            tint = if (order.paid) SellerTheme.colors.success else SellerTheme.colors.warning,
            dense = true,
        )
    }
}
