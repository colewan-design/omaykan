package com.omaykan.seller.feature.products

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.omaykan.seller.core.designsystem.ForestTopBar
import com.omaykan.seller.core.designsystem.FormField
import com.omaykan.seller.core.designsystem.PrimaryButton
import com.omaykan.seller.core.designsystem.ScreenMessage
import com.omaykan.seller.core.designsystem.SecondaryButton
import com.omaykan.seller.core.designsystem.SellerTheme
import com.omaykan.seller.core.designsystem.SoftCard
import com.omaykan.seller.core.model.Money
import com.omaykan.seller.core.network.dto.PromoCodeDto

private val CHANNELS = listOf("online" to "Online", "counter" to "Counter", "both" to "Both")

/**
 * The shop's promo and voucher codes: what each takes off, where it works, how
 * often it has been used, and switches to pause or retire it.
 *
 * Opened from Products, for the roles that may change prices; the server
 * refuses anyone else. See documentation/merchant-features.md §8.
 */
@Composable
fun PromotionsScreen(
    onBack: () -> Unit,
    viewModel: PromotionsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .navigationBarsPadding()
            .imePadding(),
    ) {
        ForestTopBar(
            title = "Promo codes",
            onBack = onBack,
            actions = {
                if (!state.creating && state.loadError == null) {
                    IconButton(onClick = viewModel::startCreating) {
                        Icon(Icons.Filled.Add, "New promo code", tint = SellerTheme.colors.onCanopy)
                    }
                }
            },
        )

        when {
            state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(strokeWidth = 2.dp)
            }

            state.loadError != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                ScreenMessage(
                    icon = Icons.Filled.LocalOffer,
                    title = "Can't load promo codes",
                    body = state.loadError.orEmpty(),
                    actionLabel = "Try again",
                    onAction = viewModel::load,
                )
            }

            else -> Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (state.creating) CreateForm(state, viewModel)

                if (state.codes.isEmpty() && !state.creating) {
                    ScreenMessage(
                        icon = Icons.Filled.LocalOffer,
                        title = "No promo codes yet",
                        body = "Make one for a slow afternoon, a first order, or a regular.",
                        actionLabel = "New code",
                        onAction = viewModel::startCreating,
                    )
                }

                state.codes.forEach { code ->
                    CodeCard(
                        code = code,
                        error = state.rowErrors[code.id],
                        onToggle = { viewModel.setActive(code, !code.isActive) },
                        onRetire = { viewModel.retire(code) },
                    )
                }
            }
        }
    }
}

@Composable
private fun CreateForm(state: PromotionsUiState, viewModel: PromotionsViewModel) {
    val form = state.form

    SoftCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            FormField(
                label = "Code",
                value = form.code,
                onValueChange = { value -> viewModel.edit { copy(code = value.uppercase()) } },
                required = true,
                placeholder = "WELCOME10",
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                FilterChip(selected = form.percent, onClick = { viewModel.edit { copy(percent = true) } }, label = { Text("% off") })
                FilterChip(selected = !form.percent, onClick = { viewModel.edit { copy(percent = false) } }, label = { Text("₱ off") })
            }

            FormField(
                label = if (form.percent) "Percentage off" else "Amount off (₱)",
                value = form.value,
                onValueChange = { value -> viewModel.edit { copy(value = value) } },
                required = true,
                placeholder = if (form.percent) "10" else "50",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )

            FormField(
                label = "Minimum order (₱)",
                value = form.minOrder,
                onValueChange = { value -> viewModel.edit { copy(minOrder = value) } },
                placeholder = "0",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )

            Text("Works", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CHANNELS.forEach { (value, label) ->
                    FilterChip(
                        selected = form.channel == value,
                        onClick = { viewModel.edit { copy(channel = value) } },
                        label = { Text(label) },
                    )
                }
            }

            FormField(
                label = "Total uses",
                value = form.maxUses,
                onValueChange = { value -> viewModel.edit { copy(maxUses = value) } },
                placeholder = "Unlimited",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )

            FormField(
                label = "Uses per customer",
                value = form.perCustomer,
                onValueChange = { value -> viewModel.edit { copy(perCustomer = value) } },
                placeholder = "Unlimited",
                supporting = "Customers are known by their account, or by phone number for guests.",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )

            state.formError?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = SellerTheme.colors.danger)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SecondaryButton(
                    label = "Cancel",
                    onClick = viewModel::cancelCreating,
                    enabled = !state.saving,
                    height = 50,
                    modifier = Modifier.weight(1f),
                )
                PrimaryButton(
                    label = "Create code",
                    onClick = viewModel::create,
                    busy = state.saving,
                    modifier = Modifier.weight(1.6f),
                )
            }
        }
    }
}

@Composable
private fun CodeCard(code: PromoCodeDto, error: String?, onToggle: () -> Unit, onRetire: () -> Unit) {
    val rules = buildList {
        add(CHANNELS.firstOrNull { it.first == code.channel }?.second ?: code.channel)
        if (code.minSubtotalCents > 0) add("orders over ${Money.peso(code.minSubtotalCents)}")
        code.perCustomerLimit?.let { add("${it}× per customer") }
    }.joinToString(" · ")
    val uses = code.maxRedemptions?.let { "${code.redemptions} of $it used" } ?: "${code.redemptions} used"

    SoftCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        code.code,
                        style = MaterialTheme.typography.titleMedium,
                        color = if (code.isActive) MaterialTheme.colorScheme.onSurface else SellerTheme.colors.textTertiary,
                    )
                    Text(code.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                    Text(rules, style = MaterialTheme.typography.bodySmall, color = SellerTheme.colors.textTertiary)
                }
                Text(uses, style = MaterialTheme.typography.labelMedium, color = SellerTheme.colors.textSecondary)
            }
            Row {
                TextButton(onClick = onToggle) { Text(if (code.isActive) "Pause" else "Resume") }
                TextButton(onClick = onRetire) { Text("Retire", color = SellerTheme.colors.danger) }
            }
            error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = SellerTheme.colors.danger) }
        }
    }
}
