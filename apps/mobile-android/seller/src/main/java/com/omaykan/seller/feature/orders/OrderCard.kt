package com.omaykan.seller.feature.orders

import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.TwoWheeler
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.omaykan.seller.core.designsystem.CircleIconButton
import com.omaykan.seller.core.designsystem.InitialAvatar
import com.omaykan.seller.core.designsystem.Pill
import com.omaykan.seller.core.designsystem.PrimaryButton
import com.omaykan.seller.core.designsystem.SecondaryButton
import com.omaykan.seller.core.designsystem.SellerTheme
import com.omaykan.seller.core.designsystem.SoftCard
import com.omaykan.seller.core.designsystem.StageTrack
import com.omaykan.seller.core.designsystem.TileShape
import com.omaykan.seller.core.model.DeliveryStage
import com.omaykan.seller.core.model.Money
import com.omaykan.seller.core.model.OrderStatus
import com.omaykan.seller.core.model.SellerOrder
import com.omaykan.seller.core.model.formatQuantity
import com.omaykan.seller.core.model.placedAtTimeLabel

/** Lines shown before the rest fold into "+ 3 more". */
private const val MAX_LINES = 4

/**
 * One order, and everything a merchant can do about it.
 *
 * Laid out the way the reference draws it — ticket and time, the customer
 * with a way to ring them, the basket line by line, the total — and then,
 * unlike the reference, the real decisions rather than one "Accept" button.
 * There is nothing to accept: an order arrives already the shop's. What the
 * shop does is move it on, get it to the door and take the money, and those
 * stay on the card because a merchant marks an order ready with one hand while
 * the other is holding a cup.
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
            Customer(order)
            Lines(order)

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
private fun statusTint(status: OrderStatus): Color = when (status) {
    // Amber while the kitchen still owes it — the "New" orange of the
    // reference, for the state every new order is born in.
    OrderStatus.Preparing -> SellerTheme.colors.attention
    OrderStatus.Ready -> MaterialTheme.colorScheme.primary
    OrderStatus.Served -> SellerTheme.colors.success
}

@Composable
private fun Header(order: SellerOrder) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = order.ticketNumber?.let { "#$it" } ?: "Order",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
        )
        Spacer(Modifier.width(8.dp))
        Pill(text = order.status.label, tint = statusTint(order.status), dense = true)
        if (!order.paid) {
            Spacer(Modifier.width(6.dp))
            Pill(text = "Unpaid", tint = SellerTheme.colors.warning, dense = true)
        }
        Spacer(Modifier.weight(1f))
        // The clock time is what a shop reads down the list to find the
        // ticket that has waited longest.
        order.placedAtTimeLabel()?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = SellerTheme.colors.textTertiary,
                maxLines = 1,
            )
        }
    }

    // Where the kitchen has got to, as a rail — read without being read.
    StageTrack(
        steps = OrderStatus.entries.size,
        current = OrderStatus.entries.indexOf(order.status),
        tint = statusTint(order.status),
        modifier = Modifier.padding(top = 12.dp),
    )
}

/**
 * Who it is for, and a way to ring them.
 *
 * The phone button hands off to the dialler rather than placing the call, so
 * it needs no permission and a mis-tap costs nothing.
 */
@Composable
private fun Customer(order: SellerOrder) {
    val context = LocalContext.current

    Row(
        Modifier.padding(top = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        InitialAvatar(text = order.customerName, size = 36)
        Column(
            Modifier
                .weight(1f)
                .padding(horizontal = 10.dp),
        ) {
            Text(
                text = order.customerName,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = listOf(
                    if (order.isDelivery) "Delivery" else "Pickup",
                    "${order.itemCount} item${if (order.itemCount == 1) "" else "s"}",
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = SellerTheme.colors.textTertiary,
            )
        }
        order.customerPhone?.let { phone ->
            CircleIconButton(
                icon = Icons.Filled.Call,
                contentDescription = "Call ${order.customerName}",
                onClick = {
                    runCatching {
                        context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone")))
                    }
                },
                background = SellerTheme.colors.accentSoft,
                tint = SellerTheme.colors.onAccentSoft,
                size = 36,
            )
        }
    }
}

/** The basket, line by line, on a faint panel, with the total under it. */
@Composable
private fun Lines(order: SellerOrder) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
            .clip(TileShape)
            .background(SellerTheme.colors.fill.copy(alpha = 0.55f))
            .padding(12.dp),
    ) {
        val shown = order.items.take(MAX_LINES)
        shown.forEachIndexed { index, line ->
            Row(Modifier.padding(top = if (index == 0) 0.dp else 8.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = line.name,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "${formatQuantity(line.quantity)} × ${Money.peso(line.unitPriceCents)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = SellerTheme.colors.textTertiary,
                    )
                }
                Text(
                    text = Money.peso(line.lineTotalCents),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(start = 12.dp),
                )
            }
        }

        val hidden = order.items.size - shown.size
        if (hidden > 0) {
            Text(
                text = "+ $hidden more item${if (hidden == 1) "" else "s"}",
                style = MaterialTheme.typography.bodySmall,
                color = SellerTheme.colors.textTertiary,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        HorizontalDivider(
            Modifier.padding(vertical = 10.dp),
            color = SellerTheme.colors.separator,
        )

        // Whatever the total holds beyond the basket and the delivery fee —
        // VAT, on a shop that charges it. Without this line a ₱240 basket and
        // a ₱49 fee sat above a ₱317.80 total that visibly did not add up.
        val extraCents = order.totalCents - order.subtotalCents - order.deliveryFeeCents
        if (order.deliveryFeeCents > 0) SummaryLine("Delivery fee", order.deliveryFeeCents)
        if (extraCents > 0) SummaryLine("Tax", extraCents)

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Total",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = Money.peso(order.totalCents),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun SummaryLine(label: String, cents: Long) {
    Row(Modifier.padding(bottom = 4.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = SellerTheme.colors.textSecondary,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = Money.peso(cents),
            style = MaterialTheme.typography.bodySmall,
            color = SellerTheme.colors.textSecondary,
        )
    }
}

/**
 * Where the food is going, in its own peach panel.
 *
 * Set apart because it is the half of the order that belongs to somebody who
 * is not in the shop. The rider's number is spelled out rather than hidden
 * behind a tap: it is what a shop reaches for when an order goes quiet.
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
            .padding(top = 10.dp)
            .clip(TileShape)
            .background(SellerTheme.colors.accentSoft.copy(alpha = 0.7f))
            .padding(12.dp),
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

        // Needs a rider, has one, picked up, delivered. Amber while it is
        // still waiting for a person, the only one of the four that is
        // anybody's job.
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
    icon: ImageVector,
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

/**
 * Everything a merchant can press, in one band at the bottom, in the order of
 * the work: feed the customer, get it to them, take the money.
 */
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

    // A finished, paid, delivered order has nothing left to press.
    if (statusLabel == null && deliveryLabel == null && !order.needsRider && order.paid) return

    FlowRow(
        Modifier
            .fillMaxWidth()
            .padding(top = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // The write in flight, shown where the buttons are. The buttons are
        // already inert while any order is saving; this says why.
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

        // A way back into the rider sheet once somebody is on the order — the
        // map, and the button that puts the order back on the board.
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
