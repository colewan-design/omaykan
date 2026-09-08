package com.omaykan.storefront.feature.account

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.DeliveryDining
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.omaykan.storefront.core.designsystem.OmaykanTheme
import com.omaykan.storefront.core.designsystem.Refreshable
import com.omaykan.storefront.core.designsystem.RemoteImage
import com.omaykan.storefront.core.model.CustomerAccount
import com.omaykan.storefront.core.model.Money
import com.omaykan.storefront.core.model.OrderStage
import com.omaykan.storefront.core.model.TrackedOrder

/**
 * The signed-in account page.
 *
 * Laid out the way a marketplace app lays this page out — a tinted header
 * carrying the identity, a row of counts under it, the order lifecycle as a
 * row of tappable stages, then a grid of everything else — because that shape
 * is familiar to anyone who has shopped on a phone in this market, and because
 * it puts the two things people actually open this tab for (where is my order,
 * where does it get delivered) above the fold instead of behind a list.
 *
 * What it deliberately does **not** borrow is the half of that shape that is
 * loyalty theatre: no coin balance, no tier badge, no voucher wallet, no games
 * rail. This platform has none of those, and a card showing zero coins is worse
 * than no card at all. Every number on this page counts something real — orders
 * placed, addresses saved, products hearted.
 */
@Composable
fun AccountHome(
    account: CustomerAccount,
    summary: AccountSummary,
    busy: Boolean,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenProfile: (() -> Unit)? = null,
    onOpenAddresses: (() -> Unit)? = null,
    onOpenPayment: (() -> Unit)? = null,
    onOpenSecurity: (() -> Unit)? = null,
    onOpenSaved: (() -> Unit)? = null,
    onOpenOrders: ((OrderStage?) -> Unit)? = null,
    onOpenAbout: (() -> Unit)? = null,
    onTrackOrder: ((String) -> Unit)? = null,
    onRefresh: () -> Unit = {},
) {
    Refreshable(
        refreshing = summary.refreshing,
        onRefresh = onRefresh,
        modifier = modifier,
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            AccountHeader(account = account, onOpenAddresses = onOpenAddresses)

            StatRow(
                account = account,
                summary = summary,
                onOpenOrders = onOpenOrders,
                onOpenSaved = onOpenSaved,
                onOpenAddresses = onOpenAddresses,
                modifier = Modifier
                    // Lifted into the header, so the two read as one block rather
                    // than as a banner with a list under it.
                    .offset(y = (-28).dp)
                    .padding(horizontal = 16.dp),
            )

            OrdersSection(
                summary = summary,
                onOpenOrders = onOpenOrders,
                onTrackOrder = onTrackOrder,
                modifier = Modifier.padding(horizontal = 16.dp),
            )

            ShortcutGrid(
                account = account,
                summary = summary,
                onOpenProfile = onOpenProfile,
                onOpenAddresses = onOpenAddresses,
                onOpenPayment = onOpenPayment,
                onOpenSecurity = onOpenSecurity,
                onOpenSaved = onOpenSaved,
                onOpenOrders = onOpenOrders,
                onOpenAbout = onOpenAbout,
                modifier = Modifier.padding(horizontal = 16.dp),
            )

            Spacer(Modifier.height(24.dp))

            OutlinedButton(
                onClick = onSignOut,
                enabled = !busy,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth()
                    .height(50.dp),
            ) {
                Text(if (busy) "Signing out…" else "Sign out")
            }

            // Says what sign-out does, because it does not do the obvious thing:
            // other devices stay signed in, which is the behaviour the API has and
            // the one a shared household wants.
            Text(
                text = "Signs out this phone only. Anything else you are signed in on stays that way.",
                style = MaterialTheme.typography.bodySmall,
                color = OmaykanTheme.colors.textTertiary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
            )

            Spacer(Modifier.height(24.dp))
        }
    }
}

/**
 * The identity band.
 *
 * Tinted with the brand accent rather than the reference's gold: the shape is
 * borrowed, the palette is this product's own, and a second accent colour
 * appearing on exactly one screen is how a design system starts to come apart.
 * The gradient lands on the page background so the stat cards below can sit
 * across the seam without a visible edge behind them.
 */
@Composable
private fun AccountHeader(account: CustomerAccount, onOpenAddresses: (() -> Unit)?) {
    val accent = MaterialTheme.colorScheme.primary

    Box(
        Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    listOf(
                        accent.copy(alpha = 0.22f),
                        accent.copy(alpha = 0.08f),
                        MaterialTheme.colorScheme.background,
                    ),
                ),
            )
            .padding(start = 20.dp, end = 16.dp, top = 24.dp, bottom = 40.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (account.avatarUrl.isNotBlank()) {
                RemoteImage(
                    url = account.avatarUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape),
                )
            } else {
                Box(
                    Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surface),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.Person,
                        contentDescription = null,
                        modifier = Modifier.size(30.dp),
                        tint = OmaykanTheme.colors.textTertiary,
                    )
                }
            }

            Column(
                Modifier
                    .weight(1f)
                    .padding(start = 14.dp),
            ) {
                Text(
                    account.name,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = OmaykanTheme.colors.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    account.email,
                    style = MaterialTheme.typography.bodySmall,
                    color = OmaykanTheme.colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            /*
             * Where the reference puts a loyalty tier and a voucher balance.
             *
             * This is the honest thing to put in that slot: the address an
             * order would go to right now. It is the one fact on this page that
             * silently decides something — an order placed against the wrong
             * default goes to the wrong door — so it earns the prominence the
             * reference gives to points.
             */
            if (onOpenAddresses != null) {
                val address = account.preferredAddress

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .clickable(onClick = onOpenAddresses)
                        .padding(start = 10.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                ) {
                    Icon(
                        Icons.Filled.LocationOn,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = accent,
                    )
                    Text(
                        address?.label ?: "Add address",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = OmaykanTheme.colors.ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .widthIn(max = 88.dp)
                            .padding(start = 4.dp),
                    )
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = OmaykanTheme.colors.textTertiary,
                    )
                }
            }
        }
    }
}

/**
 * Three counts, in the slot the reference gives to coins, vouchers and credit.
 *
 * All three are things the shopper made rather than things the platform issued,
 * which is the whole difference between this row and the one it is modelled on.
 */
@Composable
private fun StatRow(
    account: CustomerAccount,
    summary: AccountSummary,
    onOpenOrders: ((OrderStage?) -> Unit)?,
    onOpenSaved: (() -> Unit)?,
    onOpenAddresses: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        StatCard(
            label = "Orders",
            value = if (summary.loaded) "${summary.orders}" else "—",
            detail = when {
                !summary.loaded -> "Counting…"
                summary.active > 0 -> "${summary.active} still moving"
                summary.orders > 0 -> "All received"
                else -> "None yet"
            },
            action = "View all",
            onClick = onOpenOrders?.let { open -> { open(null) } },
            modifier = Modifier.weight(1f),
        )
        StatCard(
            label = "Saved",
            value = "${summary.saved}",
            detail = if (summary.saved == 0) "Nothing hearted" else "Ready to reorder",
            action = "Browse",
            onClick = onOpenSaved,
            modifier = Modifier.weight(1f),
        )
        StatCard(
            label = "Addresses",
            value = "${account.addresses.size}",
            detail = account.preferredAddress?.let { if (it.pinned) "Pinned" else "No map pin" }
                ?: "None saved",
            action = "Manage",
            onClick = onOpenAddresses,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun StatCard(
    label: String,
    value: String,
    detail: String,
    action: String,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 12.dp, horizontal = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = OmaykanTheme.colors.textSecondary,
        )
        Text(
            value,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = OmaykanTheme.colors.ink,
        )
        Text(
            detail,
            style = MaterialTheme.typography.labelSmall,
            color = OmaykanTheme.colors.textTertiary,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (onClick != null) {
            Text(
                action,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

/**
 * The order lifecycle as a row, and the one order still moving.
 *
 * Four stages rather than the reference's five, and different ones, because
 * these are the four this platform actually has: `order_status` runs preparing
 * → ready → served, `delivery_stage` runs pending → assigned → picked_up →
 * delivered, and that is the whole vocabulary. There is no "to pay" stage —
 * payment is settled at the door — and no "to review" stage, because nothing on
 * this platform collects reviews.
 */
@Composable
private fun OrdersSection(
    summary: AccountSummary,
    onOpenOrders: ((OrderStage?) -> Unit)?,
    onTrackOrder: ((String) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Your orders",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = OmaykanTheme.colors.ink,
                modifier = Modifier.weight(1f),
            )
            if (onOpenOrders != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onOpenOrders(null) }
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                ) {
                    Text(
                        "View all",
                        style = MaterialTheme.typography.labelLarge,
                        color = OmaykanTheme.colors.textSecondary,
                    )
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = OmaykanTheme.colors.textTertiary,
                    )
                }
            }
        }

        Row(
            Modifier
                .padding(top = 8.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(vertical = 14.dp),
        ) {
            OrderStage.entries.forEach { stage ->
                StageTile(
                    stage = stage,
                    count = summary.count(stage),
                    // Lands on exactly the orders this tile just counted.
                    onClick = onOpenOrders?.let { open -> { open(stage) } },
                    modifier = Modifier.weight(1f),
                )
            }
        }

        // Only when there is one. A "no active orders" card is a card that
        // spends the best space on the page saying nothing.
        val live = summary.live
        if (live != null) {
            LiveOrderCard(
                order = live,
                onClick = onTrackOrder?.let { track -> { track(live.orderId) } },
                modifier = Modifier.padding(top = 10.dp),
            )
        }
    }
}

@Composable
private fun StageTile(
    stage: OrderStage,
    count: Int,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val icon = when (stage) {
        OrderStage.Preparing -> Icons.Filled.Restaurant
        OrderStage.Ready -> Icons.Filled.ShoppingBag
        OrderStage.OnTheWay -> Icons.Filled.DeliveryDining
        OrderStage.Completed -> Icons.Filled.CheckCircle
    }

    Column(
        modifier
            .clip(RoundedCornerShape(10.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Badged only when there is something in it: a "0" on every stage turns
        // the row into noise and hides the one number that matters.
        if (count > 0) {
            BadgedBox(badge = { Badge { Text("$count") } }) {
                Icon(
                    icon,
                    contentDescription = null,
                    modifier = Modifier.size(26.dp),
                    tint = OmaykanTheme.colors.ink,
                )
            }
        } else {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(26.dp),
                tint = OmaykanTheme.colors.textTertiary,
            )
        }

        Text(
            stage.label,
            style = MaterialTheme.typography.labelSmall,
            color = if (count > 0) OmaykanTheme.colors.ink else OmaykanTheme.colors.textTertiary,
            textAlign = TextAlign.Center,
            maxLines = 2,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

/** The newest order still moving — the reference's prompt-card slot, earned. */
@Composable
private fun LiveOrderCard(
    order: TrackedOrder,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val accent = MaterialTheme.colorScheme.primary
    val items = order.items.sumOf { it.quantity }.toInt()

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(accent.copy(alpha = 0.10f))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Icon(
            Icons.AutoMirrored.Filled.ReceiptLong,
            contentDescription = null,
            modifier = Modifier.size(22.dp),
            tint = accent,
        )

        Column(
            Modifier
                .weight(1f)
                .padding(start = 12.dp),
        ) {
            Text(
                order.ticketNumber?.let { "Order #$it" } ?: "Your order",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = OmaykanTheme.colors.ink,
            )
            Text(
                listOf(
                    order.stage?.label,
                    if (items > 0) "$items item${if (items == 1) "" else "s"}" else null,
                    Money.peso(order.totalCents),
                ).filterNotNull().joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = OmaykanTheme.colors.textSecondary,
            )
        }

        if (onClick != null) {
            Text(
                "Track",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = accent,
            )
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = accent,
            )
        }
    }
}

/**
 * Everything else, four across.
 *
 * A grid rather than a list because the labels are short and the destinations
 * are unrelated — a list implies an order of importance that these do not have,
 * and eight rows of one word each is a lot of screen to say very little.
 */
@Composable
private fun ShortcutGrid(
    account: CustomerAccount,
    summary: AccountSummary,
    onOpenProfile: (() -> Unit)?,
    onOpenAddresses: (() -> Unit)?,
    onOpenPayment: (() -> Unit)?,
    onOpenSecurity: (() -> Unit)?,
    onOpenSaved: (() -> Unit)?,
    onOpenOrders: ((OrderStage?) -> Unit)?,
    onOpenAbout: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val tiles = listOfNotNull(
        onOpenProfile?.let { Shortcut("Your details", Icons.Filled.Person, null, it) },
        onOpenAddresses?.let {
            Shortcut(
                "Addresses",
                Icons.Filled.LocationOn,
                account.addresses.size.takeIf { count -> count > 0 },
                it,
            )
        },
        onOpenPayment?.let {
            Shortcut("Payment", Icons.Filled.CreditCard, null, it)
        },
        onOpenSecurity?.let { Shortcut("Sign-in", Icons.Filled.Lock, null, it) },
        onOpenSaved?.let {
            Shortcut("Saved", Icons.Filled.FavoriteBorder, summary.saved.takeIf { n -> n > 0 }, it)
        },
        onOpenOrders?.let { open ->
            Shortcut(
                "Orders",
                Icons.AutoMirrored.Filled.ReceiptLong,
                summary.active.takeIf { n -> n > 0 },
            ) { open(null) }
        },
        onOpenAbout?.let { Shortcut("About", Icons.Filled.Info, null, it) },
    )

    if (tiles.isEmpty()) return

    Column(modifier.padding(top = 20.dp)) {
        Text(
            "Your account",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = OmaykanTheme.colors.ink,
        )

        Column(
            Modifier
                .padding(top = 8.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            tiles.chunked(COLUMNS).forEach { row ->
                Row(Modifier.fillMaxWidth()) {
                    row.forEach { tile -> ShortcutTile(tile, Modifier.weight(1f)) }
                    // Keeps a short last row aligned under the one above rather
                    // than spreading its tiles across the full width.
                    repeat(COLUMNS - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

/**
 * The half of this tab that works with no account at all.
 *
 * Shown under the sign-in card, because a signed-out Account tab that is only a
 * login wall would quietly contradict the sentence printed above it: ordering
 * from this app does not need an account, and neither do saved products, this
 * device's own order history, or pairing to a shop by code. A shopper who has
 * never signed in still has things on this page.
 *
 * Saved items and orders are the same destinations the signed-in grid offers —
 * both read from local state or from this device's own order ids, so neither
 * needs a session to have something in it.
 */
@Composable
fun AccountGuestShortcuts(
    savedCount: Int,
    modifier: Modifier = Modifier,
    onOpenSaved: (() -> Unit)? = null,
    onOpenOrders: (() -> Unit)? = null,
    onOpenAbout: (() -> Unit)? = null,
) {
    val tiles = listOfNotNull(
        onOpenSaved?.let {
            Shortcut("Saved", Icons.Filled.FavoriteBorder, savedCount.takeIf { n -> n > 0 }, it)
        },
        onOpenOrders?.let { Shortcut("Orders", Icons.AutoMirrored.Filled.ReceiptLong, null, it) },
        onOpenAbout?.let { Shortcut("About", Icons.Filled.Info, null, it) },
    )

    if (tiles.isEmpty()) return

    Column(modifier.fillMaxWidth()) {
        Text(
            "Without an account",
            style = MaterialTheme.typography.labelMedium,
            color = OmaykanTheme.colors.textTertiary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )

        Row(
            Modifier
                .padding(top = 10.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(vertical = 14.dp),
        ) {
            tiles.forEach { tile -> ShortcutTile(tile, Modifier.weight(1f)) }
            repeat(COLUMNS - tiles.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}

private const val COLUMNS = 4

private data class Shortcut(
    val label: String,
    val icon: ImageVector,
    /** Shown as a badge when there is something to say. Null for most. */
    val count: Int?,
    val onClick: () -> Unit,
)

@Composable
private fun ShortcutTile(shortcut: Shortcut, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = shortcut.onClick)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val icon = @Composable {
            Icon(
                shortcut.icon,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = OmaykanTheme.colors.ink,
            )
        }

        if (shortcut.count != null) {
            BadgedBox(badge = { Badge { Text("${shortcut.count}") } }) { icon() }
        } else {
            icon()
        }

        Text(
            shortcut.label,
            style = MaterialTheme.typography.labelSmall,
            color = OmaykanTheme.colors.textSecondary,
            textAlign = TextAlign.Center,
            maxLines = 2,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}
