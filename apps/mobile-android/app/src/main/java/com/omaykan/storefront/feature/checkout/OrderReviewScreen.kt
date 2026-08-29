package com.omaykan.storefront.feature.checkout

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.omaykan.storefront.core.designsystem.OmaykanTheme
import com.omaykan.storefront.core.model.CartLine
import com.omaykan.storefront.core.model.Money
import com.omaykan.storefront.feature.cart.CartTopBar

/**
 * The last look before the shop is holding a ticket.
 *
 * A screen rather than a dialog, because what wants checking does not fit in
 * one: the items and their quantities, where it is going, who it is for, how it
 * will be paid, and what it comes to. The thing being confirmed is the one
 * irreversible action in the app — the order takes stock off a real shelf and
 * starts a real person packing, and there is no cancel afterwards, only ringing
 * the shop.
 *
 * It shares the checkout's view model, resolved against the checkout's own
 * back-stack entry, so Back returns to a form still holding every word the
 * shopper typed.
 */
@Composable
fun OrderReviewScreen(
    viewModel: CheckoutViewModel,
    onPlaced: (String) -> Unit,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.placedOrderId) {
        val id = state.placedOrderId ?: return@LaunchedEffect
        viewModel.onNavigated()
        onPlaced(id)
    }

    /*
     * A rejected field belongs next to the box the shopper fixes it in.
     *
     * The server's 422s are keyed to the form's fields — an address outside the
     * shop's delivery range is the common one — and the form is the screen
     * behind this one. Showing "Delivery address: too far" here, above a Place
     * order button and below nothing that can be edited, would be a dead end.
     */
    LaunchedEffect(state.fieldErrors) {
        if (state.fieldErrors.isNotEmpty()) onBack()
    }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding(),
    ) {
        CartTopBar(title = "Review your order", subtitle = "", onBack = onBack)

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ItemsCard(state)
            FulfillmentCard(state)
            TotalsCard(state)

            state.error?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }

        Column(
            Modifier
                .fillMaxWidth()
                .padding(20.dp)
                .navigationBarsPadding(),
        ) {
            Button(
                onClick = viewModel::confirm,
                enabled = state.canSubmit,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = OmaykanTheme.colors.ink,
                    contentColor = OmaykanTheme.colors.onInk,
                ),
            ) {
                if (state.submitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = OmaykanTheme.colors.onInk,
                    )
                } else {
                    Text("Place order", modifier = Modifier.padding(vertical = 4.dp))
                }
            }
            Text(
                text = "This sends the order to the shop. Nothing is charged now — you pay " +
                    "when you get it.",
                style = MaterialTheme.typography.bodySmall,
                color = OmaykanTheme.colors.textTertiary,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun ItemsCard(state: CheckoutUiState) {
    Card {
        CardTitle("What you are ordering")
        state.cart.lines.forEach { line -> ItemRow(line) }
    }
}

@Composable
private fun ItemRow(line: CartLine) {
    Row(Modifier.padding(top = 10.dp)) {
        Column(Modifier.weight(1f)) {
            Text(
                text = formatQuantity(line.quantity) + "× " + line.product.name,
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = Money.peso(line.product.priceCents) + " each",
                style = MaterialTheme.typography.bodySmall,
                color = OmaykanTheme.colors.textTertiary,
            )
        }
        Text(
            text = Money.peso(line.lineTotalCents),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun FulfillmentCard(state: CheckoutUiState) {
    Card {
        CardTitle(if (state.isDelivery) "Where it is going" else "Where you are collecting it")

        Text(
            text = if (state.isDelivery) state.addressForOrder else "Pick up at the shop",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 8.dp),
        )

        DetailRow(
            label = "For",
            value = listOf(state.name, state.phone, state.email)
                .filter { it.isNotBlank() }
                .joinToString(" · "),
        )
        DetailRow(label = "Paying with", value = state.payment.label)
    }
}

/**
 * The money, and the one number nobody on this phone can know.
 *
 * Everything here is priced from the shop's cached shelf, so it is an estimate
 * and says so. The delivery fee is worse than an estimate: it is quoted
 * server-side from the shop's pin and the drop-off's, and there is no endpoint
 * that will quote one without also placing the order. So it is named as pending
 * rather than shown as a plausible number that would turn out wrong at the
 * door — the confirmation screen carries the shop's real total.
 */
@Composable
private fun TotalsCard(state: CheckoutUiState) {
    Card {
        Row {
            Text(
                text = "Subtotal",
                style = MaterialTheme.typography.bodyMedium,
                color = OmaykanTheme.colors.textSecondary,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = Money.peso(state.cart.estimatedSubtotalCents),
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        if (state.isDelivery) {
            Row(Modifier.padding(top = 4.dp)) {
                Text(
                    text = "Delivery",
                    style = MaterialTheme.typography.bodyMedium,
                    color = OmaykanTheme.colors.textSecondary,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = if (state.dropLat != null) {
                        "Quoted from your pin"
                    } else {
                        "Quoted by the shop"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = OmaykanTheme.colors.textSecondary,
                )
            }
        }

        Row(Modifier.padding(top = 10.dp)) {
            Text(
                text = "Estimated total",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = Money.peso(state.cart.estimatedSubtotalCents),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        }

        Text(
            text = if (state.isDelivery) {
                "The shop prices the order and adds the delivery fee. Your confirmation shows " +
                    "the real total."
            } else {
                "The shop prices the order. Your confirmation shows the real total."
            },
            style = MaterialTheme.typography.bodySmall,
            color = OmaykanTheme.colors.textTertiary,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

@Composable
private fun CardTitle(text: String) {
    Text(text = text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(Modifier.padding(top = 8.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = OmaykanTheme.colors.textSecondary,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1.6f),
        )
    }
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, OmaykanTheme.colors.separator, RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
    ) {
        content()
    }
}

/** "2", not "2.0" — whole counts are the overwhelming case, and 2.0 kg is not. */
private fun formatQuantity(quantity: Double): String =
    if (quantity % 1.0 == 0.0) quantity.toInt().toString() else quantity.toString()
