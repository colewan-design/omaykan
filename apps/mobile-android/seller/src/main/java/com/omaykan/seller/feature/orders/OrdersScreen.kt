package com.omaykan.seller.feature.orders

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.omaykan.seller.core.designsystem.CircleIconButton
import com.omaykan.seller.core.designsystem.HeroShape
import com.omaykan.seller.core.designsystem.InitialAvatar
import com.omaykan.seller.core.designsystem.PillShape
import com.omaykan.seller.core.designsystem.SegmentedTabs
import com.omaykan.seller.core.designsystem.SellerTheme
import com.omaykan.seller.core.designsystem.TabItem
import com.omaykan.seller.core.model.PairedStore
import com.omaykan.seller.core.model.Takings

/**
 * The shop's day.
 *
 * A header, two tabs and a list of cards — and every decision a merchant can
 * make about an order is on its own card, so nothing here navigates anywhere.
 * The screen a shop reads at arm's length across a counter should not also be a
 * screen they have to find their way back out of.
 *
 * The header is a dark green panel rather than an app bar, and the day's
 * figures sit on it as white tiles. That is one band of colour doing three
 * jobs: it says which shop this phone is, it answers "how is today going"
 * without a tap, and it gives the list of white cards below something to be a
 * list *against* — which the old flat-grey layout never did.
 */
@Composable
fun OrdersScreen(
    store: PairedStore,
    viewModel: OrdersViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    /*
     * The notification permission, asked for once, when the switch is turned on
     * rather than on launch.
     *
     * Asking at launch is asking before the merchant has seen what the app is
     * for, and a prompt dismissed then is dismissed for good. Asking here means
     * the question arrives at the moment they have just said they want to be
     * told about orders, which is the one moment the answer is obviously yes.
     *
     * Denied is a supported state: the watcher still runs and the list still
     * updates. What is lost is the sound, not the work.
     */
    val permission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { viewModel.setWatching(true) }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .navigationBarsPadding(),
    ) {
        Hero(
            store = store,
            takings = state.takings,
            watching = state.watching,
            onToggleWatch = { wanted ->
                when {
                    !wanted -> viewModel.setWatching(false)
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ->
                        permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    else -> viewModel.setWatching(true)
                }
            },
            onSignOut = viewModel::signOut,
        )

        SegmentedTabs(
            tabs = OrderTab.entries.map { entry ->
                TabItem(
                    label = entry.label,
                    // A count on Live only. A number beside All restates the
                    // length of the list under it, which is furniture.
                    badge = if (entry == OrderTab.Live) state.liveCount else null,
                )
            },
            selected = OrderTab.entries.indexOf(state.tab),
            onSelect = { index -> viewModel.onTab(OrderTab.entries[index]) },
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )

        state.feedError?.let { FeedErrorLine(it) }

        OrderList(state = state, viewModel = viewModel)
    }

    OrderSheets(state = state, viewModel = viewModel)
}

/**
 * Which shop, how today is going, and the two switches that are not about any
 * one order.
 *
 * Sign-out lives up here rather than behind a menu because there is no menu —
 * this app is two screens — and a phone shared between a morning and an evening
 * shift needs the way out to be findable by someone who has never used it.
 */
@Composable
private fun Hero(
    store: PairedStore,
    takings: Takings,
    watching: Boolean,
    onToggleWatch: (Boolean) -> Unit,
    onSignOut: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(HeroShape)
            .background(SellerTheme.colors.canopy)
            .statusBarsPadding()
            .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            InitialAvatar(
                text = store.name,
                background = SellerTheme.colors.onCanopy.copy(alpha = 0.14f),
                foreground = SellerTheme.colors.onCanopy,
            )

            Column(
                Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp),
            ) {
                Text(
                    text = "Today at",
                    style = MaterialTheme.typography.bodySmall,
                    color = SellerTheme.colors.canopyMuted,
                )
                Text(
                    text = store.name.ifBlank { "Your shop" },
                    style = MaterialTheme.typography.titleLarge,
                    color = SellerTheme.colors.onCanopy,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    // The code, so a merchant running two branches can tell at
                    // a glance which one this phone is showing before they mark
                    // somebody's order ready.
                    text = store.code,
                    style = MaterialTheme.typography.bodySmall,
                    color = SellerTheme.colors.canopyMuted,
                )
            }

            WatchToggle(watching = watching, onToggle = onToggleWatch)

            Spacer(Modifier.width(8.dp))

            CircleIconButton(
                icon = Icons.AutoMirrored.Filled.Logout,
                contentDescription = "Sign out",
                onClick = onSignOut,
                background = SellerTheme.colors.onCanopy.copy(alpha = 0.12f),
                tint = SellerTheme.colors.canopyMuted,
            )
        }

        TakingsStrip(takings, Modifier.padding(top = 18.dp))
    }
}

/**
 * Whether the phone is listening.
 *
 * A filled circle when it is, hollow when it is not, and the label says the
 * state rather than the action: a merchant glancing at the header wants to know
 * whether they will be told about the next order, and a button reading "Stop
 * watching" would have them reading the opposite of the truth.
 */
@Composable
private fun WatchToggle(watching: Boolean, onToggle: (Boolean) -> Unit) {
    CircleIconButton(
        icon = if (watching) Icons.Filled.NotificationsActive else Icons.Filled.NotificationsOff,
        contentDescription = if (watching) "Watching for orders" else "Not watching for orders",
        onClick = { onToggle(!watching) },
        background = if (watching) {
            MaterialTheme.colorScheme.primary
        } else {
            SellerTheme.colors.onCanopy.copy(alpha = 0.12f)
        },
        tint = if (watching) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            SellerTheme.colors.canopyMuted
        },
    )
}

@Composable
private fun FeedErrorLine(message: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(PillShape)
            .background(SellerTheme.colors.warning.copy(alpha = 0.14f))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Filled.WifiOff,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = SellerTheme.colors.warning,
        )
        Text(
            // Says what is wrong *and* what is still true. The list below is
            // real, it is simply not moving.
            text = "$message  Showing the last orders that loaded.",
            style = MaterialTheme.typography.bodySmall,
            color = SellerTheme.colors.textSecondary,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

@Composable
private fun OrderList(state: OrdersUiState, viewModel: OrdersViewModel) {
    val orders = state.visible
    val listState = rememberLazyListState()

    /*
     * Bring a newly arrived order into view.
     *
     * LazyColumn anchors on the first *visible* item when the list has keys, so
     * an order prepended while the merchant is looking at the top slides in
     * above the fold and stays there — the whole point of this screen, arriving
     * off-screen. Caught on an emulator, not in review.
     *
     * Guarded on being near the top already, so it never yanks the list out
     * from under somebody who has scrolled down to read an older order. If they
     * are down the list, the takings count and the Live badge are what tell
     * them something came in.
     */
    val firstId = orders.firstOrNull()?.id
    LaunchedEffect(firstId) {
        if (firstId == null) return@LaunchedEffect
        // Read before waiting: once the new order is in the list, everything
        // has shifted down by one and "was the merchant at the top" is no
        // longer answerable.
        if (listState.firstVisibleItemIndex > 1) return@LaunchedEffect

        /*
         * Wait for a frame before scrolling, and the wait is the fix.
         *
         * Scrolling in the same frame as the insertion takes the list to
         * whatever is at index 0 *now*, which is still the old first order —
         * so the new one ends up one card above the fold, exactly the bug this
         * is here to prevent.
         *
         * It also handles the case that made this hard to see: an order that
         * arrives while the app is in the background. No frames are produced
         * while the activity is stopped, so this simply waits, and the scroll
         * happens on the frame the merchant actually comes back to.
         */
        withFrameNanos { }
        listState.animateScrollToItem(0)
    }

    when {
        state.loading && orders.isEmpty() -> Box(
            Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(strokeWidth = 2.dp)
        }

        orders.isEmpty() -> EmptyState(state.tab)

        else -> LazyColumn(
            state = listState,
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(orders, key = { it.id }) { order ->
                OrderCard(
                    order = order,
                    busy = state.busyOrderId == order.id,
                    // Nothing is tappable while any write is in flight. One
                    // order at a time is the rule the view model enforces; this
                    // is what stops a merchant discovering it by tapping.
                    enabled = state.busyOrderId == null,
                    onAdvanceStatus = { viewModel.advanceStatus(order) },
                    onAdvanceDelivery = { viewModel.advanceDelivery(order) },
                    onAssignRider = { viewModel.openRiderSheet(order.id) },
                    onSettle = { viewModel.openSettleSheet(order.id) },
                )
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

/**
 * An empty list, said as a picture rather than a paragraph.
 *
 * The circle and the icon are doing real work: a screen with one grey sentence
 * in the middle of it reads as something that failed to load, and this one has
 * not failed — it is the state a good morning starts in.
 */
@Composable
private fun EmptyState(tab: OrderTab) {
    Box(
        Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier
                    .size(84.dp)
                    .clip(CircleShape)
                    .background(SellerTheme.colors.accentSoft),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ReceiptLong,
                    contentDescription = null,
                    modifier = Modifier.size(36.dp),
                    tint = SellerTheme.colors.onAccentSoft,
                )
            }

            Text(
                text = when (tab) {
                    // Two different silences. One is a good morning; the other
                    // is a quiet day, and telling a merchant "nothing needs
                    // you" when they have taken forty orders would read as a
                    // fault.
                    OrderTab.Live -> "Nothing needs you right now"
                    OrderTab.All -> "No storefront orders yet today"
                },
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 20.dp),
            )

            Text(
                text = when (tab) {
                    OrderTab.Live -> "New orders land at the top of this list, " +
                        "and the phone will sound if it is watching."
                    OrderTab.All -> "Orders placed on your storefront show up here " +
                        "the moment they are paid for or sent."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = SellerTheme.colors.textTertiary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}
