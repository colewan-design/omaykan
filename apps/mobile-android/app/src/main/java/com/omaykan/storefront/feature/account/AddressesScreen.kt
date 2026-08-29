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
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
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
import com.omaykan.storefront.core.model.SavedAddress

/**
 * Where the shopper's orders go.
 *
 * The list carries one thing the web version does not have to explain as
 * carefully: whether an address has a pin. A pinned address gets a delivery fee
 * quoted from the real distance; an unpinned one falls back to the shop's flat
 * rate, and the shopper is entitled to know which of those they are looking at.
 */
@Composable
fun AddressesScreen(
    onBack: () -> Unit,
    onOpenForm: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AddressesViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    AccountSectionScaffold(
        title = "Delivery addresses",
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
            if (state.addresses.isEmpty()) {
                Text(
                    "No saved addresses yet.",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = OmaykanTheme.colors.ink,
                )
                Text(
                    "Saving one means checkout fills itself in — and if you save it " +
                        "from where you are standing, the shop can quote the real " +
                        "delivery fee instead of its flat rate.",
                    style = MaterialTheme.typography.bodySmall,
                    color = OmaykanTheme.colors.textSecondary,
                )
            }

            state.addresses.forEach { address ->
                AddressRow(
                    address = address,
                    busy = address.id in state.working,
                    onEdit = {
                        viewModel.editAddress(address)
                        onOpenForm()
                    },
                    onMakeDefault = { viewModel.makeDefault(address) },
                    onDelete = { viewModel.confirmDelete(address) },
                )
            }

            SectionError(state.error)

            OutlinedButton(
                onClick = {
                    viewModel.addAddress()
                    onOpenForm()
                },
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Text("Add an address", modifier = Modifier.padding(start = 8.dp, top = 6.dp, bottom = 6.dp))
            }

            Spacer(Modifier.height(24.dp))
        }
    }

    state.confirmingDelete?.let { address ->
        AlertDialog(
            onDismissRequest = viewModel::cancelDelete,
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text("Remove ${address.label}?") },
            text = { Text(address.oneLine) },
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
private fun AddressRow(
    address: SavedAddress,
    busy: Boolean,
    onEdit: () -> Unit,
    onMakeDefault: () -> Unit,
    onDelete: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .border(1.dp, OmaykanTheme.colors.separator, RoundedCornerShape(14.dp))
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                address.label,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = OmaykanTheme.colors.ink,
                modifier = Modifier.weight(1f),
            )
            if (address.isDefault) {
                Text(
                    "Default",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            if (busy) {
                Box(Modifier.padding(start = 8.dp)) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                }
            }
        }

        Text(
            address.oneLine,
            style = MaterialTheme.typography.bodyMedium,
            color = OmaykanTheme.colors.textSecondary,
            modifier = Modifier.padding(top = 2.dp),
        )

        if (address.notes.isNotBlank()) {
            Text(
                address.notes,
                style = MaterialTheme.typography.bodySmall,
                color = OmaykanTheme.colors.textTertiary,
                modifier = Modifier.padding(top = 2.dp),
            )
        }

        // Said on the row rather than only in the form, because it is the one
        // fact about a saved address that changes what an order costs.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = 6.dp),
        ) {
            Icon(
                Icons.Filled.LocationOn,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = if (address.pinned) {
                    MaterialTheme.colorScheme.primary
                } else {
                    OmaykanTheme.colors.textTertiary
                },
            )
            Text(
                if (address.pinned) {
                    "Pinned — delivery quoted by distance"
                } else {
                    "No pin — the shop's flat delivery rate"
                },
                style = MaterialTheme.typography.labelSmall,
                color = OmaykanTheme.colors.textTertiary,
                modifier = Modifier.padding(start = 4.dp),
            )
        }

        HorizontalDivider(
            color = OmaykanTheme.colors.separator,
            modifier = Modifier.padding(vertical = 8.dp),
        )

        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(onClick = onEdit, enabled = !busy) { Text("Edit") }
            if (!address.isDefault) {
                TextButton(onClick = onMakeDefault, enabled = !busy) { Text("Make default") }
            }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onDelete, enabled = !busy) {
                Text("Remove", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}
