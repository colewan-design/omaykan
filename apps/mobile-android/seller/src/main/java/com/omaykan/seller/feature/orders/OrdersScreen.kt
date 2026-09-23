package com.omaykan.seller.feature.orders

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.omaykan.seller.core.designsystem.ButtonShape
import com.omaykan.seller.core.designsystem.ChipTabs
import com.omaykan.seller.core.designsystem.ForestTopBar
import com.omaykan.seller.core.designsystem.ScreenMessage
import com.omaykan.seller.core.designsystem.SellerTheme
import com.omaykan.seller.core.designsystem.TabItem

/**
 * The shop's orders.
 *
 * A forest bar, a row of chips and a list of cards — and every decision a
 * merchant can make about an order is on its own card, so nothing here
 * navigates anywhere. The day's figures that used to sit above this list moved
 * to the home tab with the redesign; this tab is only the work.
 */
@Composable
fun OrdersScreen(viewModel: OrdersViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        ForestTopBar(
            title = "Orders",
            actions = {
                IconButton(onClick = viewModel::refresh) {
                    Icon(
                        Icons.Filled.Refresh,
                        contentDescription = "Refresh orders",
                        tint = SellerTheme.colors.onCanopy,
                    )
                }
            },
        )

        ChipTabs(
            tabs = OrderTab.entries.map { TabItem(it.label, state.count(it)) },
            selected = OrderTab.entries.indexOf(state.tab),
            onSelect = { index -> viewModel.onTab(OrderTab.entries[index]) },
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 6.dp),
        )

        state.feedError?.let { FeedErrorLine(it) }

        OrderList(state = state, viewModel = viewModel)
    }

    OrderSheets(state = state, viewModel = viewModel)
}

@Composable
private fun FeedErrorLine(message: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(ButtonShape)
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
     * above the fold and stays there. Guarded on being near the top already,
     * so it never yanks the list out from under somebody reading an older one.
     *
     * The frame wait is the fix: scrolling in the same frame as the insertion
     * lands on the old first order. It also covers an order that arrives in
     * the background — no frames are produced while stopped, so the scroll
     * happens on the frame the merchant comes back to.
     */
    val firstId = orders.firstOrNull()?.id
    LaunchedEffect(firstId) {
        if (firstId == null) return@LaunchedEffect
        if (listState.firstVisibleItemIndex > 1) return@LaunchedEffect
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

        orders.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            ScreenMessage(
                icon = Icons.AutoMirrored.Filled.ReceiptLong,
                title = when (state.tab) {
                    // Different silences. One is a good morning; "nothing
                    // needs you" after forty orders would read as a fault.
                    OrderTab.Live -> "Nothing needs you right now"
                    OrderTab.Preparing -> "Nothing being prepared"
                    OrderTab.Ready -> "Nothing waiting to go out"
                    OrderTab.All -> "No storefront orders yet"
                },
                body = when (state.tab) {
                    OrderTab.Live -> "New orders land at the top of this list, " +
                        "and the phone will sound if order alerts are on."
                    OrderTab.Preparing, OrderTab.Ready -> "Orders move here as you mark them along."
                    OrderTab.All -> "Orders placed on your storefront show up here " +
                        "the moment they are sent."
                },
            )
        }

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
