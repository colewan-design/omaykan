package com.omaykan.seller.feature.products

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.omaykan.seller.core.designsystem.ButtonShape
import com.omaykan.seller.core.designsystem.ForestTopBar
import com.omaykan.seller.core.designsystem.FormField
import com.omaykan.seller.core.designsystem.PrimaryButton
import com.omaykan.seller.core.designsystem.RemoteThumb
import com.omaykan.seller.core.designsystem.ScreenMessage
import com.omaykan.seller.core.designsystem.SecondaryButton
import com.omaykan.seller.core.designsystem.SellerSwitch
import com.omaykan.seller.core.designsystem.SellerTheme
import com.omaykan.seller.core.designsystem.SoftCard
import com.omaykan.seller.core.model.Category
import com.omaykan.seller.core.model.Money

/**
 * "Add Product" / "Edit Product".
 *
 * The fields are the ones this platform actually stores and the register
 * actually writes: name, category, price, stock, the low-stock line, and
 * whether it is on sale. The reference also draws a photo picker, a unit
 * dropdown and a description box. None of those has anywhere to go — there is
 * no upload endpoint for product photos, the sync event that writes a product
 * carries no unit, and a product has no description column — so the form does
 * not offer them. A photo the product already has is shown, and so is its
 * unit, as facts rather than as fields.
 */
@Composable
fun ProductEditorScreen(
    onDone: () -> Unit,
    viewModel: ProductEditorViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.saved) {
        if (state.saved) onDone()
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .navigationBarsPadding()
            .imePadding(),
    ) {
        ForestTopBar(
            title = when {
                state.isNew -> "Add Product"
                state.canEdit -> "Edit Product"
                else -> "Product"
            },
            onBack = onDone,
        )

        when {
            state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(strokeWidth = 2.dp)
            }

            state.loadError != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                ScreenMessage(
                    icon = Icons.Filled.Inventory2,
                    title = "Can't open this product",
                    body = state.loadError.orEmpty(),
                    actionLabel = "Go back",
                    onAction = onDone,
                )
            }

            else -> Form(state = state, viewModel = viewModel, onCancel = onDone)
        }
    }
}

@Composable
private fun Form(state: ProductEditorUiState, viewModel: ProductEditorViewModel, onCancel: () -> Unit) {
    val form = state.form
    val product = state.product
    val enabled = state.canEdit && !state.saving

    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (!product?.imageUrl.isNullOrBlank()) {
                RemoteThumb(
                    url = product?.imageUrl,
                    fallback = Icons.Filled.Inventory2,
                    contentDescription = product?.name,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                )
            }

            if (!state.canEdit) {
                Note(
                    icon = Icons.Filled.Lock,
                    text = "Only an admin or manager can change products. You can see them here.",
                    tint = SellerTheme.colors.textSecondary,
                )
            }

            FormField(
                label = "Product Name",
                value = form.name,
                onValueChange = viewModel::onName,
                required = true,
                enabled = enabled,
                error = state.nameError,
                placeholder = "Baguio strawberries",
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Next,
                ),
            )

            CategoryPicker(
                categories = state.categories,
                selectedId = form.categoryId,
                enabled = enabled,
                onSelect = viewModel::onCategory,
            )

            FormField(
                label = "Price (₱)",
                value = form.price,
                onValueChange = viewModel::onPrice,
                required = true,
                enabled = enabled,
                error = state.priceError,
                prefix = "₱",
                placeholder = "180",
                // The unit is shown, not edited: the register owns it, and
                // the sync event that saves this form cannot carry one.
                supporting = product?.unitLabel?.let { "Per $it" },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
            )

            product?.branchPriceCents?.let { branch ->
                Note(
                    icon = Icons.Filled.Info,
                    text = "Customers at this branch pay ${Money.peso(branch)}. A branch price set on " +
                        "the register takes the place of the price above here.",
                    tint = SellerTheme.colors.warning,
                )
            }

            if (state.tracksStock) {
                FormField(
                    label = "Stock Quantity",
                    value = form.stock,
                    onValueChange = viewModel::onStock,
                    required = true,
                    enabled = enabled,
                    error = state.stockError,
                    placeholder = "50",
                    supporting = if (state.isNew) {
                        null
                    } else {
                        "Type what is on the shelf. The difference is recorded as a stock count."
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                )

                FormField(
                    label = "Low-stock alert at",
                    value = form.lowStock,
                    onValueChange = viewModel::onLowStock,
                    enabled = enabled,
                    error = state.lowStockError,
                    placeholder = "5",
                    supporting = "Counts as low stock at or below this. Empty uses the register's default of 5.",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                )
            } else {
                Note(
                    icon = Icons.Filled.Info,
                    text = "Stock isn't counted for this product, so it never runs out on the storefront.",
                    tint = SellerTheme.colors.textSecondary,
                )
            }

            ActiveCard(active = form.active, enabled = enabled, onChange = viewModel::onActive)

            if (product?.branchAvailable == false) {
                Note(
                    icon = Icons.Filled.Info,
                    text = "This product is switched off for this branch on the register, " +
                        "so customers here won't see it even while it is active.",
                    tint = SellerTheme.colors.warning,
                )
            }

            state.error?.let {
                Note(icon = Icons.Filled.Info, text = it, tint = SellerTheme.colors.danger)
            }
        }

        if (state.canEdit) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SecondaryButton(
                    label = "Cancel",
                    onClick = onCancel,
                    enabled = !state.saving,
                    height = 50,
                    modifier = Modifier.weight(1f),
                )
                PrimaryButton(
                    label = "Save Product",
                    onClick = viewModel::save,
                    busy = state.saving,
                    modifier = Modifier.weight(1.6f),
                )
            }
        }
    }
}

@Composable
private fun CategoryPicker(
    categories: List<Category>,
    selectedId: String?,
    enabled: Boolean,
    onSelect: (String?) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    val label = categories.firstOrNull { it.id == selectedId }?.name ?: "No category"

    Column {
        Text(
            text = "Category",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Box(Modifier.padding(top = 6.dp)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .clip(ButtonShape)
                    .background(if (enabled) MaterialTheme.colorScheme.surface else SellerTheme.colors.fill)
                    .border(1.dp, SellerTheme.colors.separator, ButtonShape)
                    .clickable(enabled = enabled) { open = true }
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null, tint = SellerTheme.colors.textSecondary)
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                DropdownMenuItem(
                    text = { Text("No category") },
                    onClick = {
                        onSelect(null)
                        open = false
                    },
                )
                categories.forEach { category ->
                    DropdownMenuItem(
                        text = { Text(category.name) },
                        onClick = {
                            onSelect(category.id)
                            open = false
                        },
                    )
                }
            }
        }
        if (categories.isEmpty()) {
            Text(
                text = "This shop has no categories yet. They are made on the register.",
                style = MaterialTheme.typography.bodySmall,
                color = SellerTheme.colors.textTertiary,
                modifier = Modifier.padding(top = 4.dp, start = 4.dp),
            )
        }
    }
}

@Composable
private fun ActiveCard(active: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    SoftCard(Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = "Active Product",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "Make this product visible to customers",
                    style = MaterialTheme.typography.bodySmall,
                    color = SellerTheme.colors.textTertiary,
                )
            }
            SellerSwitch(checked = active, onCheckedChange = onChange, enabled = enabled)
        }
    }
}

@Composable
private fun Note(icon: ImageVector, text: String, tint: Color) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(ButtonShape)
            .background(tint.copy(alpha = 0.10f))
            .padding(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(start = 10.dp),
        )
    }
}
