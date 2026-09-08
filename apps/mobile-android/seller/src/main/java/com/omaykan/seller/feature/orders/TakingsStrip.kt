package com.omaykan.seller.feature.orders

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.omaykan.seller.core.designsystem.CardShape
import com.omaykan.seller.core.designsystem.SellerTheme
import com.omaykan.seller.core.designsystem.StatTile
import com.omaykan.seller.core.model.Money
import com.omaykan.seller.core.model.Takings

/**
 * How today is going, in tiles on the header's green.
 *
 * Two figures side by side — what came through the shop, and how many orders
 * that was — because either alone is half an answer: ₱12,480 means nothing
 * until you know whether it was four orders or forty.
 *
 * What is still owed gets its own full-width row underneath, and only when
 * there is something owed. It is the one figure here that is a job rather than
 * a fact, so it is shaped like the rest of the app's jobs: a wide row with a
 * coloured icon, not a number in a grid.
 *
 * The footnote is not boilerplate. This counts **storefront orders only**;
 * in-person sales ride the till's own offline outbox and never reach this API.
 * A merchant who read this as the day's takings would be short by however much
 * they sold over the counter, so the strip says what it is counting.
 */
@Composable
fun TakingsStrip(takings: Takings, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth()) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatTile(
                label = "Taken today",
                // Rounded, because a day's total is read at a glance and never
                // typed into anything. Order totals, which a merchant might
                // reconcile against, keep their centavos.
                value = Money.pesoRounded(takings.grossCents),
                modifier = Modifier.weight(1f),
            )
            StatTile(
                label = "Orders",
                value = takings.orderCount.toString(),
                unit = if (takings.orderCount == 1) "order" else "orders",
                modifier = Modifier.weight(1f),
            )
        }

        if (takings.unpaidCount > 0) {
            UnpaidRow(takings)
        }

        Text(
            text = "Storefront orders only — counter sales stay on the till.",
            style = MaterialTheme.typography.bodySmall,
            color = SellerTheme.colors.canopyMuted,
            modifier = Modifier.padding(top = 10.dp),
        )
    }
}

/**
 * "₱1,240 still owed."
 *
 * Shown only when there is something to chase. A permanent "₱0 unpaid" is a
 * figure that is always there and therefore never read; one that appears on the
 * header partway through a shift is one a merchant notices.
 */
@Composable
private fun UnpaidRow(takings: Takings) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
            .clip(CardShape)
            // A translucent white rather than a tinted orange. Orange at low
            // alpha over the header's dark green mixes to olive, which reads
            // as neither a warning nor a colour anybody chose.
            .background(SellerTheme.colors.onCanopy.copy(alpha = 0.10f))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Filled.HourglassEmpty,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = SellerTheme.colors.warning,
        )
        Column(Modifier.padding(start = 10.dp)) {
            Text(
                text = "${Money.pesoRounded(takings.unpaidCents)} still owed",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = SellerTheme.colors.warning,
            )
            Text(
                text = "${takings.unpaidCount} order${if (takings.unpaidCount == 1) "" else "s"} " +
                    "not paid for yet",
                style = MaterialTheme.typography.bodySmall,
                color = SellerTheme.colors.canopyMuted,
            )
        }
    }
}
