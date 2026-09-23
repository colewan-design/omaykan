package com.omaykan.seller.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import com.omaykan.seller.core.designsystem.Pill
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.omaykan.seller.core.model.OrderingState
import com.omaykan.seller.core.model.PauseLength
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.omaykan.seller.core.designsystem.CountBadge
import com.omaykan.seller.core.designsystem.MountainBackdrop
import com.omaykan.seller.core.designsystem.QuickTile
import com.omaykan.seller.core.designsystem.RemoteThumb
import com.omaykan.seller.core.designsystem.SectionHeader
import com.omaykan.seller.core.designsystem.SellerLockup
import com.omaykan.seller.core.designsystem.SellerSwitch
import com.omaykan.seller.core.designsystem.SellerTheme
import com.omaykan.seller.core.designsystem.SerifFamily
import com.omaykan.seller.core.designsystem.SoftCard
import com.omaykan.seller.core.model.Money
import com.omaykan.seller.core.model.PairedStore
import com.omaykan.seller.core.model.TopProduct
import com.omaykan.seller.core.model.formatQuantity
import com.omaykan.seller.feature.products.ProductFilter
import com.omaykan.seller.feature.products.categoryIcon
import com.omaykan.seller.feature.shell.openInBrowser
import com.omaykan.seller.feature.shell.rememberAlertToggle
import com.omaykan.seller.feature.shell.storeImageUrl
import com.omaykan.seller.feature.shell.storefrontUrl
import java.time.LocalTime

/**
 * The shop's day at a glance.
 *
 * Top to bottom in the order a merchant opens the app wanting to know:
 * which shop is this, how is today going, where do I go next, will the phone
 * tell me when an order comes, and what is selling.
 */
@Composable
fun HomeScreen(
    store: PairedStore,
    onOpenOrders: () -> Unit,
    onOpenProducts: (ProductFilter?) -> Unit,
    onOpenSales: () -> Unit,
    onOpenMessages: () -> Unit,
    onOpenAccount: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val toggleAlerts = rememberAlertToggle(viewModel::setWatching)

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState()),
    ) {
        MountainBackdrop(Modifier.fillMaxWidth()) {
            Column(
                Modifier
                    .statusBarsPadding()
                    .padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 44.dp),
            ) {
                TopRow(unread = state.unreadMessages, onOpenMessages = onOpenMessages)
                StoreCard(store = store, photoUrl = state.storePhotoUrl, onOpen = onOpenAccount)
                Greeting(state.userName)
            }
        }

        Column(
            Modifier
                .offset(y = (-28).dp)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            TodayCard(
                state = state,
                onOpenOrders = onOpenOrders,
                onOpenLowStock = { onOpenProducts(ProductFilter.LowStock) },
            )

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                QuickTile(Icons.Filled.Inventory2, "Products", { onOpenProducts(null) }, Modifier.weight(1f))
                QuickTile(
                    Icons.AutoMirrored.Filled.ReceiptLong,
                    "Orders",
                    onOpenOrders,
                    Modifier.weight(1f),
                    count = state.liveCount,
                )
                QuickTile(Icons.Filled.Insights, "Sales", onOpenSales, Modifier.weight(1f))
                QuickTile(
                    Icons.Filled.ChatBubbleOutline,
                    "Messages",
                    onOpenMessages,
                    Modifier.weight(1f),
                    count = state.unreadMessages,
                )
            }

            AlertsCard(
                watching = state.watching,
                onToggle = toggleAlerts,
                insistent = state.insistent,
                onInsistent = viewModel::setInsistent,
            )

            state.ordering?.let { ordering ->
                OrderingCard(
                    ordering = ordering,
                    busy = state.orderingBusy,
                    error = state.orderingError,
                    onPause = viewModel::pauseOrdering,
                    onReopen = viewModel::reopenOrdering,
                )
            }

            state.feedError?.let {
                Text(
                    text = "$it Figures show the last orders that loaded.",
                    style = MaterialTheme.typography.bodySmall,
                    color = SellerTheme.colors.warning,
                )
            }

            TopProducts(state = state, onSeeAll = { onOpenProducts(null) })
        }
    }
}

@Composable
private fun TopRow(unread: Int, onOpenMessages: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(52.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SellerLockup(fontSize = 20.sp, modifier = Modifier.weight(1f))
        Box {
            IconButton(onClick = onOpenMessages) {
                Icon(
                    Icons.Filled.ChatBubbleOutline,
                    contentDescription = if (unread > 0) "Messages, $unread unread" else "Messages",
                    tint = SellerTheme.colors.onCanopy,
                )
            }
            if (unread > 0) {
                CountBadge(
                    unread,
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 4.dp, end = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun StoreCard(store: PairedStore, photoUrl: String?, onOpen: () -> Unit) {
    val context = LocalContext.current

    SoftCard(
        Modifier
            .fillMaxWidth()
            .padding(top = 6.dp),
        onClick = onOpen,
    ) {
        Row(
            Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RemoteThumb(
                url = photoUrl ?: storeImageUrl(store.id),
                fallback = Icons.Filled.Storefront,
                shape = CircleShape,
                modifier = Modifier.size(54.dp),
            )
            Column(
                Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp),
            ) {
                Text(
                    text = store.name.ifBlank { "Your shop" },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    Modifier
                        .padding(top = 2.dp)
                        .clip(CircleShape)
                        .clickable { context.openInBrowser(storefrontUrl(store)) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "View Store",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .padding(start = 4.dp)
                            .size(14.dp),
                    )
                }
                // The code, so a merchant running two branches can tell which
                // one this phone is showing before they mark an order ready.
                Text(
                    text = "Branch ${store.code}",
                    style = MaterialTheme.typography.bodySmall,
                    color = SellerTheme.colors.textTertiary,
                )
            }
        }
    }
}

@Composable
private fun Greeting(userName: String?) {
    val hour = LocalTime.now().hour
    val part = when {
        hour < 12 -> "Good morning"
        hour < 18 -> "Good afternoon"
        else -> "Good evening"
    }
    val first = userName?.trim()?.split(" ")?.firstOrNull()?.takeIf { it.isNotBlank() }

    Text(
        text = if (first != null) "$part, $first!" else "$part!",
        style = TextStyle(
            fontFamily = SerifFamily,
            fontWeight = FontWeight.SemiBold,
            fontStyle = FontStyle.Italic,
            fontSize = 24.sp,
            lineHeight = 30.sp,
        ),
        color = Color.White,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 22.dp),
    )
    Text(
        text = "“Local products create brighter tomorrows.”",
        style = TextStyle(fontFamily = SerifFamily, fontStyle = FontStyle.Italic, fontSize = 15.sp),
        color = SellerTheme.colors.onCanopy.copy(alpha = 0.85f),
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp),
    )
}

/**
 * Today in three figures, laid out the way the reference draws them: a round
 * forest badge, the number, a two-line label, and under the sales figure a
 * small pill saying how today compares with yesterday by this hour.
 *
 * The storefront-only caveat sits under the card rather than in it, so the
 * card matches the reference and the caveat is still on the screen that shows
 * "Total Sales" — counter sales never reach this app, and a merchant reading
 * this as the whole day's takings would be short by what they sold in person.
 */
@Composable
private fun TodayCard(state: HomeUiState, onOpenOrders: () -> Unit, onOpenLowStock: () -> Unit) {
    val takings = state.takings
    val waiting = state.ordersLoading && takings.orderCount == 0

    Column {
        SoftCard(Modifier.fillMaxWidth()) {
            Row(
                Modifier
                    .height(IntrinsicSize.Min)
                    .padding(vertical = 16.dp),
            ) {
                Figure(
                    badge = { ForestBadge(Icons.Outlined.AccountBalanceWallet) },
                    value = if (waiting) "—" else Money.pesoRounded(takings.grossCents),
                    label = "Total Sales\nToday",
                    onClick = onOpenOrders,
                    modifier = Modifier.weight(1.15f),
                ) {
                    state.salesChangePercent?.let { ChangePill(it) }
                }
                FigureDivider()
                Figure(
                    badge = { ForestBadge(Icons.AutoMirrored.Outlined.ReceiptLong) },
                    value = if (waiting) "—" else takings.orderCount.toString(),
                    label = "Orders\nToday",
                    onClick = onOpenOrders,
                    modifier = Modifier.weight(1f),
                )
                FigureDivider()
                Figure(
                    badge = {
                        Icon(
                            Icons.Filled.Warning,
                            contentDescription = null,
                            tint = SellerTheme.colors.danger,
                            modifier = Modifier.size(30.dp),
                        )
                    },
                    value = state.lowStockCount?.toString() ?: "—",
                    label = "Low Stock\nItems",
                    onClick = onOpenLowStock,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Text(
            text = "Storefront orders only — counter sales stay on the till.",
            style = MaterialTheme.typography.bodySmall,
            color = SellerTheme.colors.textTertiary,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
        )
    }
}

@Composable
private fun FigureDivider() {
    VerticalDivider(
        color = SellerTheme.colors.separator,
        modifier = Modifier
            .fillMaxHeight()
            .padding(vertical = 4.dp),
    )
}

/** The reference's round badge: forest ground, a light line icon. */
@Composable
private fun ForestBadge(icon: ImageVector) {
    Box(
        Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(SellerTheme.colors.canopy),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = SellerTheme.colors.onCanopy, modifier = Modifier.size(19.dp))
    }
}

/**
 * "↗ +12%": green up, red down. Read aloud with what it compares against,
 * because the pill alone does not say.
 */
@Composable
private fun ChangePill(percent: Int) {
    val up = percent >= 0
    Pill(
        text = (if (up) "+" else "") + "$percent%",
        tint = if (up) SellerTheme.colors.success else SellerTheme.colors.danger,
        dense = true,
        leading = if (up) Icons.AutoMirrored.Filled.TrendingUp else Icons.AutoMirrored.Filled.TrendingDown,
        modifier = Modifier
            .padding(top = 6.dp)
            .clearAndSetSemantics {
                contentDescription = "${if (up) "Up" else "Down"} ${kotlin.math.abs(percent)} percent " +
                    "on this time yesterday"
            },
    )
}

@Composable
private fun Figure(
    badge: @Composable () -> Unit,
    value: String,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    extra: @Composable () -> Unit = {},
) {
    Column(
        modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(34.dp), contentAlignment = Alignment.Center) { badge() }
        Text(
            text = value,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = SellerTheme.colors.ink,
            maxLines = 1,
            // A six-figure day is wider than a third of a phone. Ellipsised
            // rather than clipped, so a cut number is visibly cut.
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            lineHeight = 18.sp,
            color = SellerTheme.colors.textSecondary,
            textAlign = TextAlign.Center,
            maxLines = 2,
        )
        extra()
    }
}

/**
 * The reference's "Store Status: Online" card, answering the question this
 * app can actually answer.
 *
 * There is no switch on the server that takes a shop off the storefront — an
 * "Online / Offline" toggle here would change nothing a customer sees. What a
 * merchant can decide from this phone is whether it tells them about orders,
 * so that is what the card holds. The label says the state, not the action:
 * somebody glancing at it wants to know whether they will hear the next order.
 */
/**
 * The shop's own "not taking online orders right now".
 *
 * Switching it off asks for how long, because "closed until tomorrow morning"
 * should be one tap and a shop that forgets to reopen should not stay closed
 * for a week. See documentation/merchant-features.md §3.
 */
@Composable
private fun OrderingCard(
    ordering: OrderingState,
    busy: Boolean,
    error: String?,
    onPause: (PauseLength) -> Unit,
    onReopen: () -> Unit,
) {
    var choosing by remember { mutableStateOf(false) }

    SoftCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(if (ordering.paused) SellerTheme.colors.warning else SellerTheme.colors.canopy),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (ordering.paused) Icons.Filled.PauseCircle else Icons.Filled.PlayCircle,
                        contentDescription = null,
                        tint = SellerTheme.colors.onCanopy,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Column(
                    Modifier
                        .weight(1f)
                        .padding(horizontal = 12.dp),
                ) {
                    Text(
                        text = if (ordering.paused) "Online orders paused" else "Taking online orders",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = if (ordering.paused) {
                            ordering.resumesAt?.let { "Reopens by itself ${resumeLabel(it)}." }
                                ?: "Closed until you reopen."
                        } else {
                            "Pause when the kitchen is full or stock has run out."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = SellerTheme.colors.textTertiary,
                    )
                }
                SellerSwitch(
                    checked = !ordering.paused,
                    onCheckedChange = { open ->
                        if (busy) return@SellerSwitch
                        if (open) onReopen() else choosing = true
                    },
                )
            }

            if (choosing && !ordering.paused) {
                Text(
                    text = "Pause for how long?",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 10.dp),
                )
                PauseLength.entries.forEach { length ->
                    TextButton(
                        onClick = {
                            choosing = false
                            onPause(length)
                        },
                        enabled = !busy,
                    ) { Text(length.label) }
                }
                TextButton(onClick = { choosing = false }) { Text("Cancel") }
            }

            error?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = SellerTheme.colors.warning,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
    }
}

private fun resumeLabel(at: java.time.OffsetDateTime): String {
    val local = at.atZoneSameInstant(java.time.ZoneId.systemDefault())
    val time = local.format(DateTimeFormatter.ofPattern("h:mm a"))
    return if (local.toLocalDate() == LocalDate.now()) "at $time" else "${local.format(DateTimeFormatter.ofPattern("EEE"))} at $time"
}

@Composable
private fun AlertsCard(
    watching: Boolean,
    onToggle: (Boolean) -> Unit,
    insistent: Boolean,
    onInsistent: (Boolean) -> Unit,
) {
    SoftCard(Modifier.fillMaxWidth()) {
        Column {
            Row(
                Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(SellerTheme.colors.canopy),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (watching) Icons.Filled.NotificationsActive else Icons.Filled.NotificationsOff,
                        contentDescription = null,
                        tint = SellerTheme.colors.onCanopy,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Column(
                    Modifier
                        .weight(1f)
                        .padding(horizontal = 12.dp),
                ) {
                    Text(
                        text = if (watching) "Order alerts are on" else "Order alerts are off",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = if (watching) {
                            "This phone sounds when an order arrives, even in the background."
                        } else {
                            "Orders still arrive. The phone just won't sound for them."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = SellerTheme.colors.textTertiary,
                    )
                }
                SellerSwitch(checked = watching, onCheckedChange = onToggle)
            }

            // For the kitchen with its back to the phone.
            if (watching) {
                Row(
                    Modifier.padding(start = 66.dp, end = 14.dp, bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Keep ringing until I open it",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    SellerSwitch(checked = insistent, onCheckedChange = onInsistent)
                }
            }
        }
    }
}

@Composable
private fun TopProducts(state: HomeUiState, onSeeAll: () -> Unit) {
    Column {
        SectionHeader(title = "Top Products", actionLabel = "See All", onAction = onSeeAll)
        Text(
            text = "Storefront sales, last 7 days",
            style = MaterialTheme.typography.bodySmall,
            color = SellerTheme.colors.textTertiary,
        )

        if (state.topProducts.isEmpty()) {
            Text(
                text = if (state.ordersLoading) "Loading…" else "Nothing sold on the storefront this week yet.",
                style = MaterialTheme.typography.bodyMedium,
                color = SellerTheme.colors.textTertiary,
                modifier = Modifier.padding(top = 12.dp),
            )
        } else {
            SoftCard(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
            ) {
                state.topProducts.forEachIndexed { index, top ->
                    TopProductRow(rank = index + 1, top = top, imageUrl = state.products[top.productId]?.imageUrl)
                }
            }
        }

        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun TopProductRow(rank: Int, top: TopProduct, imageUrl: String?) {
    Row(
        Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = rank.toString(),
                color = MaterialTheme.colorScheme.onPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
        RemoteThumb(
            url = imageUrl,
            fallback = categoryIcon(top.name),
            modifier = Modifier
                .padding(start = 10.dp)
                .size(46.dp),
            iconSize = 20.dp,
        )
        Column(
            Modifier
                .weight(1f)
                .padding(horizontal = 12.dp),
        ) {
            Text(
                text = top.name,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${formatQuantity(top.quantity)} sold",
                style = MaterialTheme.typography.bodySmall,
                color = SellerTheme.colors.textTertiary,
            )
        }
        Text(
            text = Money.pesoRounded(top.revenueCents),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = SellerTheme.colors.ink,
        )
        Spacer(Modifier.width(2.dp))
    }
}
