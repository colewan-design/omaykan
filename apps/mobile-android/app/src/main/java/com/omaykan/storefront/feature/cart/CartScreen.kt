package com.omaykan.storefront.feature.cart

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.omaykan.storefront.core.designsystem.CtaButton
import com.omaykan.storefront.core.designsystem.ForestTopBar
import com.omaykan.storefront.core.designsystem.HeartToggle
import com.omaykan.storefront.core.designsystem.MessageState
import com.omaykan.storefront.core.designsystem.MountainScene
import com.omaykan.storefront.core.designsystem.OmaykanTheme
import com.omaykan.storefront.core.designsystem.QuantityBox
import com.omaykan.storefront.core.designsystem.RemoteImage
import com.omaykan.storefront.core.designsystem.softCard
import com.omaykan.storefront.core.model.CartLine
import com.omaykan.storefront.core.model.Money

/**
 * The basket for one shop, laid out to the reference: a card per line with its
 * photo, heart, price, stepper and bin; the sums; one terracotta button; and
 * the mountains along the bottom of the screen.
 *
 * Where the reference prints a shipping fee, this says the fee comes at
 * checkout — it is quoted server-side from the shop's pin and the drop-off's,
 * and a plausible number here would be a number that turns out wrong at the
 * door. The total is named an estimate for the same reason.
 */
@Composable
fun CartScreen(
    onCheckout: () -> Unit,
    onBack: () -> Unit,
    viewModel: CartViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val cart = state.cart

    Column(Modifier.fillMaxSize()) {
        ForestTopBar(title = "Your Cart", subtitle = state.shopName, onBack = onBack)

        if (cart.isEmpty && cart.unavailableIds.isEmpty()) {
            MessageState(
                title = "Your cart is empty",
                detail = "Add something from the shelf and it will wait for you here.",
                icon = Icons.Outlined.ShoppingCart,
                actionLabel = "Back to the shelf",
                onAction = onBack,
            )
            return@Column
        }

        /*
         * A plain scrolling column at least one screen tall, not a lazy list.
         *
         * A basket is a handful of lines, and this is what lets the mountains
         * sit on the bottom edge of the screen when it is short — the way the
         * reference has them — instead of stopping halfway down the page as a
         * strip, while still scrolling up under the button when it is long.
         */
        BoxWithConstraints(Modifier.weight(1f)) {
            val viewport = maxHeight

            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .heightIn(min = viewport),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(
                    Modifier.padding(top = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    // Told, not silently dropped: someone who put these in a
                    // basket deserves to know they went, rather than arriving
                    // at checkout with a total that quietly shrank.
                    if (cart.unavailableIds.isNotEmpty()) {
                        Column(
                            Modifier
                                .padding(horizontal = 16.dp)
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(OmaykanTheme.colors.warning.copy(alpha = 0.14f))
                                .padding(12.dp),
                        ) {
                            Text(
                                text = if (cart.unavailableIds.size == 1) {
                                    "One item is no longer on the shelf."
                                } else {
                                    "${cart.unavailableIds.size} items are no longer on the shelf."
                                },
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            TextButton(
                                onClick = viewModel::dropUnavailable,
                                contentPadding = PaddingValues(0.dp),
                            ) {
                                Text("Remove them")
                            }
                        }
                    }

                    cart.lines.forEach { line ->
                        key(line.product.id) {
                            CartRow(
                                line = line,
                                saved = viewModel.savedKey(line.product.id) in state.savedIds,
                                onToggleSaved = { viewModel.toggleSaved(line.product.id) },
                                onIncrement = { viewModel.increment(line.product.id) },
                                onDecrement = {
                                    viewModel.setQuantity(line.product.id, line.quantity - 1)
                                },
                                onRemove = { viewModel.remove(line.product.id) },
                                modifier = Modifier.padding(horizontal = 16.dp),
                            )
                        }
                    }

                    Totals(
                        estimateCents = cart.estimatedSubtotalCents,
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp),
                    )

                    Column(Modifier.padding(horizontal = 16.dp)) {
                        CtaButton(
                            text = "Proceed to Checkout",
                            onClick = onCheckout,
                            enabled = !cart.isEmpty,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        // Said plainly, and said before they commit: the shop
                        // prices the order, not the phone.
                        Text(
                            text = "The shop confirms prices at checkout. You pay when you get your order.",
                            style = MaterialTheme.typography.bodySmall,
                            color = OmaykanTheme.colors.textTertiary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                        )
                    }
                }

                // Runs under the navigation bar on purpose: it is scenery, and
                // stopping short of the edge would leave a cream band below it.
                MountainScene(
                    lines = listOf("Local shops", "Honest prices"),
                    modifier = Modifier.padding(top = 20.dp),
                )
            }
        }
    }
}

@Composable
private fun CartRow(
    line: CartLine,
    saved: Boolean,
    onToggleSaved: () -> Unit,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .softCard()
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(86.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(OmaykanTheme.colors.fill),
        ) {
            RemoteImage(
                url = line.product.imageUrl,
                contentDescription = line.product.name,
                modifier = Modifier.fillMaxSize(),
            )
            HeartToggle(
                saved = saved,
                onToggle = onToggleSaved,
                size = 13.dp,
                idleTint = Color(0xFF2A2420),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(3.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.85f)),
            )
        }

        Column(
            Modifier
                .weight(1f)
                .padding(start = 12.dp),
        ) {
            Text(
                text = line.product.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = OmaykanTheme.colors.ink,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = Money.peso(line.product.priceCents),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = OmaykanTheme.colors.ink,
                modifier = Modifier.padding(top = 2.dp),
            )
            if (line.quantity != 1.0) {
                Text(
                    text = "${Money.peso(line.lineTotalCents)} for this line",
                    style = MaterialTheme.typography.bodySmall,
                    color = OmaykanTheme.colors.textTertiary,
                )
            }
            QuantityBox(
                quantity = line.quantity,
                onIncrement = onIncrement,
                onDecrement = onDecrement,
                compact = true,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        IconButton(onClick = onRemove, modifier = Modifier.align(Alignment.Bottom)) {
            Icon(
                Icons.Outlined.DeleteOutline,
                contentDescription = "Remove ${line.product.name}",
                tint = OmaykanTheme.colors.textSecondary,
            )
        }
    }
}

@Composable
private fun Totals(estimateCents: Long, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth()) {
        TotalLine("Subtotal", Money.peso(estimateCents))
        TotalLine("Delivery", "Quoted at checkout", muted = true)
        HorizontalDivider(
            color = OmaykanTheme.colors.separator,
            modifier = Modifier.padding(vertical = 10.dp),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Estimated Total",
                style = MaterialTheme.typography.titleLarge,
                color = OmaykanTheme.colors.ink,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = Money.peso(estimateCents),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = OmaykanTheme.colors.ink,
            )
        }
    }
}

@Composable
private fun TotalLine(label: String, value: String, muted: Boolean = false) {
    Row(Modifier.padding(vertical = 3.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = OmaykanTheme.colors.ink,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (muted) FontWeight.Normal else FontWeight.SemiBold,
            color = if (muted) OmaykanTheme.colors.textSecondary else OmaykanTheme.colors.ink,
        )
    }
}
