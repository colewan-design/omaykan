package com.omaykan.storefront.feature.cart

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.omaykan.storefront.core.designsystem.MessageState
import com.omaykan.storefront.core.designsystem.OmaykanTheme
import com.omaykan.storefront.core.designsystem.RemoteImage
import com.omaykan.storefront.core.model.CartLine
import com.omaykan.storefront.core.model.Money

@Composable
fun CartScreen(
    onCheckout: () -> Unit,
    onBack: () -> Unit,
    viewModel: CartViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val cart = state.cart

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding(),
    ) {
        CartTopBar(title = "Cart", subtitle = state.shopName, onBack = onBack)

        if (cart.isEmpty && cart.unavailableIds.isEmpty()) {
            MessageState(
                title = "Your cart is empty",
                detail = "Add something from the shelf and it will wait for you here.",
                icon = Icons.Outlined.ShoppingCart,
            )
            return@Column
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Told, not silently dropped: someone who put these in a basket
            // deserves to know they went, rather than arriving at checkout
            // with a total that quietly shrank.
            if (cart.unavailableIds.isNotEmpty()) {
                item {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
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
            }

            items(cart.lines, key = { it.product.id }) { line ->
                CartRow(
                    line = line,
                    onIncrement = { viewModel.increment(line.product.id) },
                    onDecrement = { viewModel.setQuantity(line.product.id, line.quantity - 1) },
                    onRemove = { viewModel.remove(line.product.id) },
                )
            }
        }

        CartFooter(
            estimateCents = cart.estimatedSubtotalCents,
            enabled = !cart.isEmpty,
            onCheckout = onCheckout,
        )
    }
}

@Composable
private fun CartRow(
    line: CartLine,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit,
    onRemove: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, OmaykanTheme.colors.separator, RoundedCornerShape(16.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RemoteImage(
            url = line.product.imageUrl,
            contentDescription = line.product.name,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(12.dp)),
        )
        Column(
            Modifier
                .weight(1f)
                .padding(horizontal = 12.dp),
        ) {
            Text(
                text = line.product.name,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = Money.peso(line.lineTotalCents),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 2.dp),
            )
            Text(
                text = "${Money.peso(line.product.priceCents)} each",
                style = MaterialTheme.typography.bodySmall,
                color = OmaykanTheme.colors.textTertiary,
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Stepper(
                quantity = line.quantity,
                onIncrement = onIncrement,
                onDecrement = onDecrement,
            )
            TextButton(onClick = onRemove, contentPadding = PaddingValues(4.dp)) {
                Text("Remove", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
fun Stepper(
    quantity: Double,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        StepperButton(Icons.Filled.Remove, "One fewer", onDecrement)
        Text(
            text = if (quantity % 1.0 == 0.0) quantity.toInt().toString() else quantity.toString(),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 12.dp),
        )
        StepperButton(Icons.Filled.Add, "One more", onIncrement)
    }
}

@Composable
private fun StepperButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .size(30.dp)
            .clip(CircleShape)
            .background(OmaykanTheme.colors.fill)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = description, modifier = Modifier.size(16.dp))
    }
}

@Composable
private fun CartFooter(estimateCents: Long, enabled: Boolean, onCheckout: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
            .navigationBarsPadding(),
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = "Estimated total",
                    style = MaterialTheme.typography.bodySmall,
                    color = OmaykanTheme.colors.textSecondary,
                )
                Text(
                    text = Money.peso(estimateCents),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        // Said plainly, and said before they commit: the shop prices the order,
        // not the phone. Any delivery fee lands at the next step.
        Text(
            text = "The shop confirms prices at checkout. Delivery is quoted there too.",
            style = MaterialTheme.typography.bodySmall,
            color = OmaykanTheme.colors.textTertiary,
            modifier = Modifier.padding(top = 4.dp),
        )
        Button(
            onClick = onCheckout,
            enabled = enabled,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = OmaykanTheme.colors.ink,
                contentColor = OmaykanTheme.colors.onInk,
            ),
        ) {
            Text("Checkout", modifier = Modifier.padding(vertical = 4.dp))
        }
    }
}

@Composable
internal fun CartTopBar(title: String, subtitle: String, onBack: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(OmaykanTheme.colors.fill)
                .clickable(onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                modifier = Modifier.size(20.dp),
            )
        }
        Column(Modifier.padding(start = 12.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = OmaykanTheme.colors.textSecondary,
                )
            }
        }
    }
}
