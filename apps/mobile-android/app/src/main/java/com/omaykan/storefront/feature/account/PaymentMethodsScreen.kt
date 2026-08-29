package com.omaykan.storefront.feature.account

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.omaykan.storefront.core.designsystem.LoadingState
import com.omaykan.storefront.core.designsystem.OmaykanTheme
import com.omaykan.storefront.core.model.SavedPaymentMethod

/**
 * How the shopper means to pay.
 *
 * Said plainly on the screen, because the word "payment method" sets an
 * expectation this platform deliberately does not meet: nothing here is
 * charged, nothing is stored with a processor, and every order is settled with
 * the person who hands it over.
 */
@Composable
fun PaymentMethodsScreen(
    onBack: () -> Unit,
    onOpenForm: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PaymentMethodsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    AccountSectionScaffold(
        title = "Payment",
        onBack = onBack,
        modifier = modifier,
        notice = state.notice,
        onNoticeShown = viewModel::onNoticeShown,
    ) { inner ->
        if (!state.loaded) {
            LoadingState(inner)
            return@AccountSectionScaffold
        }

        Column(inner, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                "You pay the person who brings your order. Nothing here is charged, " +
                    "and no card details are stored — this just saves checkout a question.",
                style = MaterialTheme.typography.bodySmall,
                color = OmaykanTheme.colors.textSecondary,
            )

            state.methods.forEach { method ->
                PaymentMethodRow(
                    method = method,
                    busy = method.id in state.working,
                    onMakeDefault = { viewModel.makeDefault(method) },
                    onDelete = { viewModel.confirmDelete(method) },
                )
            }

            SectionError(state.error)

            OutlinedButton(
                onClick = {
                    viewModel.addMethod()
                    onOpenForm()
                },
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Text("Add a payment method", modifier = Modifier.padding(start = 8.dp, top = 6.dp, bottom = 6.dp))
            }

            Spacer(Modifier.height(24.dp))
        }
    }

    state.confirmingDelete?.let { method ->
        AlertDialog(
            onDismissRequest = viewModel::cancelDelete,
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text("Remove ${method.kind.label}?") },
            text = { Text("You can still choose it at checkout.") },
            confirmButton = {
                TextButton(onClick = viewModel::deleteConfirmed) {
                    Text("Remove", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::cancelDelete) {
                    Text("Keep it", color = OmaykanTheme.colors.textSecondary)
                }
            },
        )
    }
}

@Composable
private fun PaymentMethodRow(
    method: SavedPaymentMethod,
    busy: Boolean,
    onMakeDefault: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, OmaykanTheme.colors.separator, RoundedCornerShape(14.dp))
            .padding(start = 14.dp, top = 6.dp, end = 6.dp, bottom = 6.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    method.kind.label,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = OmaykanTheme.colors.ink,
                )
                if (method.isDefault) {
                    Text(
                        "Default",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
            if (method.detail.isNotBlank()) {
                Text(
                    method.detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = OmaykanTheme.colors.textSecondary,
                )
            }
        }

        if (busy) {
            Box(Modifier.padding(horizontal = 12.dp)) {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
            }
        } else {
            if (!method.isDefault) {
                TextButton(onClick = onMakeDefault) { Text("Default") }
            }
            TextButton(onClick = onDelete) {
                Text("Remove", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}
