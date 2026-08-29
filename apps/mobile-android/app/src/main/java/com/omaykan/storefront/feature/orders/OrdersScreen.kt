package com.omaykan.storefront.feature.orders

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.LocalShipping
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.omaykan.storefront.core.designsystem.Refreshable
import com.omaykan.storefront.core.designsystem.RefreshableFill
import com.omaykan.storefront.core.designsystem.LoadingState
import com.omaykan.storefront.core.designsystem.MessageState
import com.omaykan.storefront.core.designsystem.OmaykanTheme
import com.omaykan.storefront.core.designsystem.RemoteImage
import com.omaykan.storefront.core.model.Money
import com.omaykan.storefront.core.model.TrackedOrder
import com.omaykan.storefront.core.model.TrackedOrderItem
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Orders, laid out to the reference: a search field beside the title, tabs
 * under it, and one card per order — what was in it with its picture, where it
 * has got to, what it came to, and a way in.
 *
 * Three things the reference carries that are not here, and their absence is
 * the point. Its cards are headed by the seller; an order from this API does
 * not carry one, so the ticket number heads ours instead. Its second button
 * cancels or refunds, and there is no such endpoint — a button that cannot do
 * what it says is worse than one button that works. And there is no "Review
 * Product": this app has no reviews and no data behind them.
 */
@Composable
fun OrdersScreen(
    onOpenOrder: (String) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    /**
     * A filter asked for from somewhere else — the account page's stage tiles.
     *
     * Applied once and then handed back through [onFilterApplied], so returning
     * to this tab later shows what the shopper last chose here rather than
     * silently re-applying a tap from three screens ago.
     */
    requestedFilter: OrderFilter? = null,
    onFilterApplied: () -> Unit = {},
    viewModel: OrdersViewModel = hiltViewModel(),
) {
    LaunchedEffect(requestedFilter) {
        val filter = requestedFilter ?: return@LaunchedEffect
        viewModel.onFilter(filter)
        onFilterApplied()
    }

    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(
        modifier
            .fillMaxSize()
            .statusBarsPadding(),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Orders",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            // Only worth its space once there is a list to search. One or two
            // orders are read, not queried.
            if (state.orders.size > 2) {
                SearchField(
                    query = state.query,
                    onQuery = viewModel::onQuery,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 14.dp),
                )
            }
        }

        if (state.orders.isNotEmpty()) {
            OrderTabs(
                selected = state.filter,
                countOf = state::countOf,
                onSelect = viewModel::onFilter,
            )
        }

        Refreshable(refreshing = state.refreshing, onRefresh = viewModel::refresh) {
            when {
                state.loading -> LoadingState()

                state.error != null -> RefreshableFill {
                    MessageState(
                        title = "We could not load your orders",
                        detail = state.error,
                        icon = Icons.Filled.CloudOff,
                        actionLabel = "Try again",
                        onAction = viewModel::refresh,
                    )
                }

                state.orders.isEmpty() -> RefreshableFill {
                    MessageState(
                        title = "No orders yet",
                        detail = if (state.signedIn) {
                            "Anything you order will show up here."
                        } else {
                            // Not a sign-in wall. Ordering never needed an account and
                            // still does not; what an account adds is that the history
                            // follows the person to their next phone.
                            "Anything you order will show up here. Sign in and it will " +
                                "follow you to your next phone."
                        },
                        icon = Icons.Outlined.ReceiptLong,
                    )
                }

                state.visible.isEmpty() -> RefreshableFill {
                    // Whichever of the two narrowed it down is the one to offer
                    // to undo — clearing the other would not bring anything back.
                    val searching = state.query.isNotBlank()
                    MessageState(
                        title = "Nothing here",
                        detail = if (searching) {
                            "No orders match \"${state.query.trim()}\"."
                        } else {
                            "No orders match that filter."
                        },
                        icon = Icons.Outlined.ReceiptLong,
                        actionLabel = if (searching) "Clear search" else "Show all",
                        onAction = {
                            if (searching) {
                                viewModel.onQuery("")
                            } else {
                                viewModel.onFilter(OrderFilter.All)
                            }
                        },
                    )
                }

                else -> LazyColumn(
                    contentPadding = PaddingValues(
                        start = 20.dp,
                        end = 20.dp,
                        top = 14.dp,
                        bottom = contentPadding.calculateBottomPadding() + 24.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    if (state.guestOnly) {
                        item {
                            Text(
                                text = "These are the orders from this phone. Sign in to keep " +
                                    "them when you change device.",
                                style = MaterialTheme.typography.bodySmall,
                                color = OmaykanTheme.colors.textSecondary,
                            )
                        }
                    }

                    items(state.visible, key = { it.orderId }) { order ->
                        OrderCard(
                            order = order,
                            photos = state.photos,
                            onClick = { onOpenOrder(order.orderId) },
                        )
                    }
                }
            }
        }
    }
}

/** The reference's search box: a filled pill, and a way back out of it. */
@Composable
private fun SearchField(query: String, onQuery: (String) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .clip(RoundedCornerShape(10.dp))
            .background(OmaykanTheme.colors.fill)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Filled.Search,
            contentDescription = null,
            tint = OmaykanTheme.colors.textTertiary,
            modifier = Modifier.size(16.dp),
        )
        Box(
            Modifier
                .weight(1f)
                .padding(start = 8.dp),
        ) {
            if (query.isEmpty()) {
                Text(
                    text = "Search your orders",
                    style = MaterialTheme.typography.bodySmall,
                    color = OmaykanTheme.colors.textTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            BasicTextField(
                value = query,
                onValueChange = onQuery,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodySmall.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (query.isNotEmpty()) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = "Clear search",
                tint = OmaykanTheme.colors.textTertiary,
                modifier = Modifier
                    .size(16.dp)
                    .clickable { onQuery("") },
            )
        }
    }
}

/**
 * The filters as tabs rather than chips, per the reference: a word, a count
 * where one is owed, and an underline under the one in force.
 *
 * Hand-rolled rather than Material's TabRow because these scroll, carry
 * badges, and want the app's own accent underline — three things that cost
 * more to talk a TabRow out of than to draw.
 */
@Composable
private fun OrderTabs(
    selected: OrderFilter,
    countOf: (OrderFilter) -> Int,
    onSelect: (OrderFilter) -> Unit,
) {
    Column {
        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            items(OrderFilter.entries.toList(), key = { it.name }) { filter ->
                OrderTab(
                    filter = filter,
                    selected = filter == selected,
                    count = if (filter.counted) countOf(filter) else 0,
                    onClick = { onSelect(filter) },
                )
            }
        }
        HorizontalDivider(thickness = 1.dp, color = OmaykanTheme.colors.separator)
    }
}

@Composable
private fun OrderTab(
    filter: OrderFilter,
    selected: Boolean,
    count: Int,
    onClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = filter.label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                color = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    OmaykanTheme.colors.textSecondary
                },
                maxLines = 1,
            )
            if (count > 0) {
                Badge(Modifier.padding(start = 5.dp)) { Text("$count") }
            }
        }
        Box(
            Modifier
                .padding(top = 8.dp)
                .width(22.dp)
                .height(3.dp)
                .clip(RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp))
                .background(
                    if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                ),
        )
    }
}

@Composable
private fun OrderCard(
    order: TrackedOrder,
    photos: Map<String, String>,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)

    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .border(1.dp, OmaykanTheme.colors.separator, shape)
            .clickable(onClick = onClick),
    ) {
        // The header the reference gives the seller, given to the ticket: it is
        // what the shopper is asked for at the counter, and the only name this
        // order has.
        Column(Modifier.padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.ReceiptLong,
                    contentDescription = null,
                    tint = OmaykanTheme.colors.textSecondary,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = order.ticketNumber?.let { "#$it" } ?: "Order",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 8.dp),
                )
                Icon(
                    imageVector = Icons.Filled.ChevronRight,
                    contentDescription = null,
                    tint = OmaykanTheme.colors.textTertiary,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = order.statusLabel,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = statusTint(order),
                )
            }
            placedOn(order.placedAt)?.let { when_ ->
                Text(
                    text = "Ordered on $when_",
                    style = MaterialTheme.typography.bodySmall,
                    color = OmaykanTheme.colors.textTertiary,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }

        HorizontalDivider(thickness = 1.dp, color = OmaykanTheme.colors.separator)

        Column(Modifier.padding(14.dp)) {
            // Two, then a count. A card is a summary — someone who wants the
            // whole list of a twelve-item order taps into it.
            order.items.take(SHOWN_ITEMS).forEachIndexed { index, item ->
                OrderItemRow(
                    item = item,
                    photoUrl = photos[item.productId],
                    modifier = Modifier.padding(top = if (index == 0) 0.dp else 12.dp),
                )
            }
            val rest = order.items.size - SHOWN_ITEMS
            if (rest > 0) {
                Text(
                    text = if (rest == 1) "+1 more item" else "+$rest more items",
                    style = MaterialTheme.typography.bodySmall,
                    color = OmaykanTheme.colors.textTertiary,
                    modifier = Modifier.padding(top = 10.dp),
                )
            }

            FulfillmentStrip(order, Modifier.padding(top = 14.dp))

            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 14.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Total (${countOfItems(order)}): ",
                    style = MaterialTheme.typography.bodySmall,
                    color = OmaykanTheme.colors.textSecondary,
                )
                Text(
                    text = Money.peso(order.totalCents),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }

            Button(
                onClick = onClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = OmaykanTheme.colors.ink,
                    contentColor = OmaykanTheme.colors.onInk,
                ),
            ) {
                Text(
                    text = if (order.status in OrdersUiState.TERMINAL) {
                        "Order details"
                    } else {
                        "Track order"
                    },
                    modifier = Modifier.padding(vertical = 2.dp),
                )
            }
        }
    }
}

/**
 * One bought thing: its picture, its name, and the two numbers the reference
 * puts under them — what one costs, and how many.
 *
 * The photo comes off the local catalog cache and is often absent — an order
 * placed at a shop whose shelf this phone has not opened has none, and most
 * sari-sari stock has never been photographed anyway. The row is built so that
 * the missing case is the ordinary one: the tile holds its place, and nothing
 * reflows when a picture is there.
 */
@Composable
private fun OrderItemRow(item: TrackedOrderItem, photoUrl: String?, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth()) {
        RemoteImage(
            url = photoUrl,
            contentDescription = null,
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(OmaykanTheme.colors.fill),
        )
        Column(
            Modifier
                .weight(1f)
                .padding(start = 12.dp),
        ) {
            Text(
                text = item.name,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = Money.peso(item.unitPriceCents),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "Qty: ${quantity(item.quantity)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = OmaykanTheme.colors.textSecondary,
                )
            }
        }
    }
}

/**
 * The reference's grey delivery strip, saying the other half of the story.
 *
 * The badge at the top of the card is how far the shop has got; this is where
 * the order itself is. The server answers those separately — `order_status` and
 * `delivery_stage` — and on a card that has room for both, both are worth
 * saying: "Ready" and "a rider has it" are different news.
 */
@Composable
private fun FulfillmentStrip(order: TrackedOrder, modifier: Modifier = Modifier) {
    val (icon, lead, detail) = fulfillmentOf(order)

    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(OmaykanTheme.colors.fill)
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = OmaykanTheme.colors.textSecondary,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = lead,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 8.dp),
        )
        if (detail != null) {
            Text(
                text = "|",
                style = MaterialTheme.typography.bodySmall,
                color = OmaykanTheme.colors.separator,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                color = OmaykanTheme.colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        } else {
            Spacer(Modifier.weight(1f))
        }
        Icon(
            imageVector = Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = OmaykanTheme.colors.textTertiary,
            modifier = Modifier.size(16.dp),
        )
    }
}

private data class Fulfillment(
    val icon: ImageVector,
    val lead: String,
    val detail: String?,
)

/**
 * Where the order is, in the app's own words.
 *
 * Cancelled first, before anything else gets a chance to say an order is on
 * its way somewhere. Then pick-up, which has no rider and no stages. Then the
 * delivery ladder, read from the top down so that an order the shop has closed
 * out cannot still be "waiting for a rider".
 */
private fun fulfillmentOf(order: TrackedOrder): Fulfillment {
    val rider = order.riderName?.takeIf { it.isNotBlank() }
    val address = order.deliveryAddress?.takeIf { it.isNotBlank() }

    if (order.cancelled) {
        return Fulfillment(Icons.Filled.Cancel, "Cancelled", null)
    }

    if (!order.isDelivery) {
        return Fulfillment(
            icon = Icons.Outlined.Storefront,
            lead = "Pick-up",
            detail = when (order.status) {
                "completed" -> "Picked up"
                "ready", "served" -> "Ready at the counter"
                else -> "The shop is putting it together"
            },
        )
    }

    val lead = when {
        order.status == "completed" || order.deliveryStage == "delivered" -> "Delivered"
        order.deliveryStage == "picked_up" -> "On the way"
        order.deliveryStage == "assigned" -> "Rider assigned"
        else -> "Waiting for a rider"
    }
    return Fulfillment(
        icon = Icons.Outlined.LocalShipping,
        lead = lead,
        detail = rider ?: address,
    )
}

/** Called off, finished, or still moving — the three answers worth colouring. */
@Composable
private fun statusTint(order: TrackedOrder): Color = when {
    order.cancelled -> MaterialTheme.colorScheme.error
    order.status == "completed" -> OmaykanTheme.colors.success
    else -> OmaykanTheme.colors.warning
}

/** "3 items", counting the things bought rather than the lines they sit on. */
private fun countOfItems(order: TrackedOrder): String {
    val units = order.items.sumOf { it.quantity }
    val shown = quantity(units)
    return if (units == 1.0) "$shown item" else "$shown items"
}

/** "2", not "2.0" — whole counts are the overwhelming case, and 2.0 kg is not. */
private fun quantity(value: Double): String =
    if (value % 1.0 == 0.0) value.toInt().toString() else value.toString()

private const val SHOWN_ITEMS = 2

/**
 * "26 October 2025", from the ISO-8601 the API sends.
 *
 * Parsed leniently: a timestamp this screen cannot read is a line it leaves
 * out, not a card it refuses to draw.
 */
private fun placedOn(iso: String?): String? {
    if (iso.isNullOrBlank()) return null
    return runCatching {
        OffsetDateTime.parse(iso)
            .format(DateTimeFormatter.ofPattern("d MMMM yyyy", Locale("en", "PH")))
    }.getOrNull()
}
