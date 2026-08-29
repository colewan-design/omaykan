package com.omaykan.storefront.feature.checkout

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.omaykan.storefront.core.designsystem.OmaykanTheme
import com.omaykan.storefront.core.model.FulfillmentMethod
import com.omaykan.storefront.core.model.Money
import com.omaykan.storefront.core.model.PaymentPreference
import com.omaykan.storefront.feature.cart.CartTopBar

@Composable
fun CheckoutScreen(
    onReview: () -> Unit,
    onBack: () -> Unit,
    onSignIn: () -> Unit,
    viewModel: CheckoutViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // This screen no longer places anything — it hands a good form to the
    // review screen, which is where the order is actually spoken for.
    LaunchedEffect(state.openReview) {
        if (!state.openReview) return@LaunchedEffect
        viewModel.onReviewOpened()
        onReview()
    }

    if (state.signInPrompt) {
        SignInPrompt(
            onSignIn = {
                viewModel.dismissSignInPrompt()
                onSignIn()
            },
            onGuest = viewModel::continueAsGuest,
            // Waving it away is an answer too, and it means the same thing as
            // pressing Continue as guest. Treating it as unanswered would put
            // the question back at the Place order button, which is nagging.
            onDismiss = viewModel::continueAsGuest,
        )
    }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding(),
    ) {
        CartTopBar(title = "Checkout", subtitle = "", onBack = onBack)

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Section("How would you like it?") {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ChoiceChip(
                        label = "Pick up",
                        selected = state.method == FulfillmentMethod.Pickup,
                        onClick = { viewModel.onMethod(FulfillmentMethod.Pickup) },
                        modifier = Modifier.weight(1f),
                    )
                    ChoiceChip(
                        label = "Delivery",
                        selected = state.method == FulfillmentMethod.Delivery,
                        onClick = { viewModel.onMethod(FulfillmentMethod.Delivery) },
                        modifier = Modifier.weight(1f),
                    )
                }

                if (state.isDelivery) {
                    Field(
                        value = state.address,
                        onValueChange = viewModel::onAddress,
                        label = "Delivery address",
                        error = state.fieldErrors["fulfillment.address"],
                        modifier = Modifier.padding(top = 12.dp),
                    )
                    // Two different sentences, because they are two different
                    // situations. A pin means the fee is quoted on the real
                    // distance; without one the shop charges its flat rate, and
                    // saying so is what stops the total looking arbitrary.
                    Text(
                        text = if (state.dropLat != null) {
                            "From your saved address. The shop quotes the delivery fee from its " +
                                "pin, so edit this only if the order is going somewhere else."
                        } else {
                            "The rider reads this, so write it the way you would say it " +
                                "out loud. The shop quotes the delivery fee."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = OmaykanTheme.colors.textTertiary,
                        modifier = Modifier.padding(top = 4.dp),
                    )

                    Field(
                        value = state.landmark,
                        onValueChange = viewModel::onLandmark,
                        label = "Landmark (optional)",
                        error = null,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                    Text(
                        text = "The green gate past the basketball court, the sari-sari store on " +
                            "the corner. Saved for next time.",
                        style = MaterialTheme.typography.bodySmall,
                        color = OmaykanTheme.colors.textTertiary,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }

            Section("Who is it for?") {
                Field(state.name, viewModel::onName, "Name", state.fieldErrors["guest.name"])
                Field(
                    value = state.phone,
                    onValueChange = viewModel::onPhone,
                    label = "Phone",
                    error = state.fieldErrors["guest.phone"],
                    keyboardType = KeyboardType.Phone,
                    modifier = Modifier.padding(top = 10.dp),
                )
                Field(
                    value = state.email,
                    onValueChange = viewModel::onEmail,
                    label = "Email (for a receipt)",
                    error = state.fieldErrors["guest.email"],
                    keyboardType = KeyboardType.Email,
                    modifier = Modifier.padding(top = 10.dp),
                )
                Text(
                    text = "A phone number or an email — one is enough.",
                    style = MaterialTheme.typography.bodySmall,
                    color = OmaykanTheme.colors.textTertiary,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            Section("How will you pay?") {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PaymentPreference.entries.forEach { option ->
                        ChoiceChip(
                            label = option.label,
                            selected = state.payment == option,
                            onClick = { viewModel.onPayment(option) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                // The one sentence that keeps the promise honest: nothing is
                // collected here, and nothing passes through the platform.
                Text(
                    text = "You pay the shop or the rider when you get your order. " +
                        "Nothing is charged now.",
                    style = MaterialTheme.typography.bodySmall,
                    color = OmaykanTheme.colors.textTertiary,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }

            state.fieldErrors["items.0.productId"]?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }

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
            Row {
                Text(
                    text = "Estimated total",
                    style = MaterialTheme.typography.bodyMedium,
                    color = OmaykanTheme.colors.textSecondary,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = Money.peso(state.cart.estimatedSubtotalCents),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
            Text(
                text = "Nothing is placed yet — the next screen shows the whole order first.",
                style = MaterialTheme.typography.bodySmall,
                color = OmaykanTheme.colors.textTertiary,
                modifier = Modifier.padding(top = 2.dp),
            )
            Button(
                onClick = viewModel::place,
                enabled = state.canSubmit,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = OmaykanTheme.colors.ink,
                    contentColor = OmaykanTheme.colors.onInk,
                ),
            ) {
                Text("Review order", modifier = Modifier.padding(vertical = 4.dp))
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 10.dp),
        )
        content()
    }
}

@Composable
private fun ChoiceChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) OmaykanTheme.colors.ink else MaterialTheme.colorScheme.background)
            .border(
                1.dp,
                if (selected) OmaykanTheme.colors.ink else OmaykanTheme.colors.separator,
                RoundedCornerShape(12.dp),
            )
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (selected) OmaykanTheme.colors.onInk else MaterialTheme.colorScheme.onBackground,
        )
    }
}

@Composable
private fun Field(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    error: String?,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        label = { Text(label) },
        singleLine = true,
        isError = error != null,
        supportingText = error?.let { { Text(it) } },
        shape = RoundedCornerShape(12.dp),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
    )
}

/**
 * The one interruption on this screen, and it takes no for an answer.
 *
 * Raised as checkout opens, once, and never again once answered. It sits here
 * rather than on the Place order button because signed out means an empty form,
 * an empty form means a disabled button, and a question behind a button nobody
 * can press is a question nobody is asked.
 *
 * "Continue as guest" is a real button and the order behind it is the same
 * order: guest checkout is the first-class case in this app, and a prompt that
 * could not be dismissed would quietly turn it into a login wall.
 */
@Composable
private fun SignInPrompt(
    onSignIn: () -> Unit,
    onGuest: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Sign in first?") },
        text = {
            Text(
                "Signing in keeps this order in your history and fills this form in for you " +
                    "next time, on whichever phone you are holding. You can order without " +
                    "one — the order is exactly the same either way.",
            )
        },
        confirmButton = {
            Button(onClick = onSignIn, shape = RoundedCornerShape(12.dp)) {
                Text("Sign in", fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onGuest) { Text("Continue as guest") }
        },
    )
}
