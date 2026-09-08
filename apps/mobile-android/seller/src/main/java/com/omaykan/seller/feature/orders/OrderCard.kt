package com.omaykan.seller.feature.orders

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeliveryDining
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.TwoWheeler
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.omaykan.seller.core.designsystem.Pill
import com.omaykan.seller.core.designsystem.PrimaryButton
import com.omaykan.seller.core.designsystem.SecondaryButton
import com.omaykan.seller.core.designsystem.SellerTheme
import com.omaykan.seller.core.designsystem.SoftCard
import com.omaykan.seller.core.designsystem.StageTrack
import com.omaykan.seller.core.model.DeliveryStage
import com.omaykan.seller.core.model.Money
import com.omaykan.seller.core.model.OrderStatus
import com.omaykan.seller.core.model.SellerOrder
import com.omaykan.seller.core.model.placedAtTimeLabel

/**
 * One order, and everything a merchant can do about it.
 *
 * The card carries its own actions rather than opening a detail screen. A shop
 * marks an order ready with one hand while the other is holding a cup, and a
 * tap that costs a screen transition and a back press is a tap that gets put
 * off. The two actions that genuinely need more information — naming a rider,
 * and saying how the money arrived — open a sheet, because they need a keyboard
 * or a choice.
 *
 * The ordering of the buttons is the order of the work: feed the customer
 * first, then get it to them, then take the money.
 *
 * Read top to bottom the card answers four questions in the order a shop asks
 * them: whose is this and what is it worth, how far along is it, what is in the
 * bag, and where is it going. Everything a merchant can *press* is at the
 * bottom, in one band, so a thumb learns one place to go.
 */
@Composable
fun OrderCard(
    order: SellerOrder,
    busy: Boolean,
    enabled: Boolean,
    onAdvanceStatus: () -> Unit,
    onAdvanceDelivery: () -> Unit,
    onAssignRider: () -> Unit,
    onSettle: () -> Unit,
) {
    SoftCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Header(order)

            // Where the kitchen has got to, as a rail. Three segments, because
            // there are exactly three kitchen states, and no labels because the
            // pill below already names the one we are in — this is here to be
            // read without being read.
            StageTrack(
                steps = OrderStatus.entries.size,
                current = OrderStatus.entries.indexOf(order.status),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 14.dp),
            )

            Badges(order)

            Text(
                text = order.itemSummary,
                style = MaterialTheme.typography.bodyMedium,
                color = SellerTheme.colors.textSecondary,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 10.dp),
            )

            if (order.isDelivery) {
                DeliveryPanel(order)
            }

            Actions(
                order = order,
                busy = busy,
                enabled = enabled,
                onAdvanceStatus = onAdvanceStatus,
                onAdvanceDelivery = onAdvanceDelivery,
                onAssignRider = onAssignRider,
                onSettle = onSettle,
            )
        }
    }
}

@Composable
private fun Header(order: SellerOrder) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        /*
         * Pickup or delivery, said with a shape before it is said with a word.
         * It is the first thing that changes what the shop does next, and it
         * should survive being read upside-down across a counter.
         *
         * The tile turns orange when nobody is carrying the order yet, which
         * makes "these three need a rider" answerable by scrolling rather than
         * by reading.
         */
        val wanting = order.needsRider
        Box(
            Modifier
                .size(46.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(
                    if (wanting) {
                        SellerTheme.colors.attention.copy(alpha = 0.16f)
                    } else {
                        SellerTheme.colors.accentSoft
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (order.isDelivery) {
                    Icons.Filled.DeliveryDining
                } else {
                    Icons.Filled.ShoppingBag
                },
                contentDescription = if (order.isDelivery) "Delivery" else "Pickup",
                modifier = Modifier.size(24.dp),
                tint = if (wanting) {
                    SellerTheme.colors.attention
                } else {
                    SellerTheme.colors.onAccentSoft
                },
            )
        }

        Column(
            Modifier
                .weight(1f)
                .padding(start = 12.dp),
        ) {
            Text(
                text = order.ticketNumber?.let { "#$it" } ?: "Order",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
            Text(
                // The name, and when it came in. The clock time is what a shop
                // reads down the list to find the ticket that has been waiting
                // longest — the list is newest-first, so "oldest" is furthest
                // from the thumb and needs saying.
                text = listOfNotNull(
                    order.customerName.takeIf { it.isNotBlank() },
                    order.placedAtTimeLabel(),
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = SellerTheme.colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Text(
            text = Money.peso(order.totalCents),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/**
 * Where the food is going, in its own tinted panel.
 *
 * Set apart from the rest of the card because it is the half of the order that
 * belongs to somebody who is not in the shop. The rider's number is spelled out
 * rather than hidden behind a tap: it is what a shop reaches for when an order
 * goes quiet, and the phone's own dialler is one long-press away from any text
 * on screen.
 */
@Composable
private fun DeliveryPanel(order: SellerOrder) {
    val stage = order.deliveryStage ?: DeliveryStage.Pending
    val tint = if (stage == DeliveryStage.Pending) {
        SellerTheme.colors.attention
    } else {
        MaterialTheme.colorScheme.primary
    }

    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(SellerTheme.colors.accentSoft.copy(alpha = 0.6f))
            .padding(horizontal = 12.dp, vertical = 12.dp),
    ) {
        order.deliveryAddress?.let { address ->
            PanelLine(
                icon = Icons.Filled.LocationOn,
                text = address,
                tint = SellerTheme.colors.textSecondary,
            )
        }

        order.riderName?.let { rider ->
            PanelLine(
                icon = Icons.Filled.TwoWheeler,
                text = listOfNotNull(rider, order.riderPhone).joinToString(" · "),
                tint = SellerTheme.colors.textSecondary,
                topPadding = 6,
            )
        }

        // Four dots' worth of road: needs a rider, has one, picked up,
        // delivered. Coloured orange while it is still waiting for a person,
        // which is the only one of the four that is anybody's job.
        StageTrack(
            steps = DeliveryStage.entries.size,
            current = DeliveryStage.entries.indexOf(stage),
            tint = tint,
            modifier = Modifier.padding(top = 10.dp),
        )

        Text(
            text = stage.label,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = tint,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

@Composable
private fun PanelLine(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    tint: Color,
    topPadding: Int = 0,
) {
    Row(
        Modifier.padding(top = topPadding.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier
                .size(15.dp)
                .padding(top = 1.dp),
            tint = tint,
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = tint,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Badges(order: SellerOrder) {
    FlowRow(
        Modifier.padding(top = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Pill(
            text = order.status.label,
            tint = if (order.status == OrderStatus.Served) {
                SellerTheme.colors.success
            } else {
                MaterialTheme.colorScheme.primary
            },
        )

        // No pill for the delivery stage. The panel below names it, under its
        // own track, and a card that said "Needs a rider" twice in four lines
        // was the first thing anybody noticed about it.

        if (!order.paid) {
            Pill(text = "Unpaid", tint = SellerTheme.colors.warning)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Actions(
    order: SellerOrder,
    busy: Boolean,
    enabled: Boolean,
    onAdvanceStatus: () -> Unit,
    onAdvanceDelivery: () -> Unit,
    onAssignRider: () -> Unit,
    onSettle: () -> Unit,
) {
    val statusLabel = order.status.advanceLabel
    val deliveryLabel = order.deliveryStage?.advanceLabel

    // A finished, paid, delivered order has nothing left to press, and an empty
    // row of padding under it would read as something missing.
    if (statusLabel == null && deliveryLabel == null && !order.needsRider && order.paid) return

    FlowRow(
        Modifier
            .fillMaxWidth()
            .padding(top = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // The write in flight, shown where the buttons are rather than over
        // the card. The buttons themselves are already inert — the screen
        // disables every one of them while any order is saving — so this is
        // here to say why, not to stop anything.
        if (busy) {
            Box(
                Modifier.height(44.dp),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                )
            }
            Spacer(Modifier.size(4.dp))
        }

        statusLabel?.let { label ->
            PrimaryButton(
                label = label,
                onClick = onAdvanceStatus,
                enabled = enabled,
                height = 44,
            )
        }

        // Naming a rider and advancing the road are the same slot: an order
        // with nobody on it needs a name, and once it has one it needs moving.
        when {
            order.needsRider -> SecondaryButton(
                label = "Assign rider",
                onClick = onAssignRider,
                enabled = enabled,
                leading = Icons.Filled.PersonAdd,
            )

            deliveryLabel != null -> SecondaryButton(
                label = deliveryLabel,
                onClick = onAdvanceDelivery,
                enabled = enabled,
                leading = Icons.Filled.TwoWheeler,
            )
        }

        // And a way back into the rider sheet once somebody is on the order.
        //
        // Without this the sheet is reachable exactly once — before anybody is
        // assigned — which strands the two things a merchant actually wants
        // mid-delivery: the map showing where the rider is, and the button that
        // puts the order back on the board. Found by running the app.
        if (order.isDelivery && !order.needsRider) {
            val tracking = order.riderPosition != null
            SecondaryButton(
                label = if (tracking) "Track" else "Change rider",
                onClick = onAssignRider,
                enabled = enabled,
                leading = if (tracking) Icons.Filled.MyLocation else Icons.Filled.PersonAdd,
            )
        }

        if (!order.paid) {
            SecondaryButton(
                label = "Mark paid",
                onClick = onSettle,
                enabled = enabled,
                leading = Icons.Filled.Payments,
            )
        }
    }
}
