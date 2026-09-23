package com.omaykan.storefront.feature.account

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.omaykan.storefront.core.designsystem.OmaykanTheme
import com.omaykan.storefront.core.model.PaymentPreference

/**
 * Saving how the shopper means to pay.
 *
 * A screen rather than a dialog, for the same reason the address form is one:
 * a radio list that grows a phone-number field when GCash is picked is a form,
 * and a form that resizes itself inside an AlertDialog moves the option the
 * shopper just tapped.
 *
 * It shares [PaymentMethodsViewModel] with the list underneath, so "Payment
 * method saved." is said by the list this pops back to.
 */
@Composable
fun PaymentMethodFormScreen(
    viewModel: PaymentMethodsViewModel,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val form = state.form

    // Clearing the form is the only way out — see AddressFormScreen.
    LaunchedEffect(form == null) { if (form == null) onDone() }

    val held = remember { mutableStateOf(form) }
    form?.let { held.value = it }
    val shown = held.value ?: return

    // As on the address form: Back clears the form, and does nothing mid-save.
    BackHandler { if (!shown.busy) viewModel.closeForm() }

    AccountSectionScaffold(
        title = "Add a payment method",
        onBack = { if (!shown.busy) viewModel.closeForm() },
        modifier = modifier,
    ) { inner ->
        Column(inner, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PaymentPreference.entries.forEach { kind ->
                // Cash is greyed rather than hidden once saved, so the list
                // of what this platform accepts stays the same every time.
                val available = kind != PaymentPreference.Cash || !state.cashAlreadySaved

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(
                            selected = shown.kind == kind,
                            enabled = available && !shown.busy,
                            onClick = { viewModel.onKind(kind) },
                        )
                        .padding(vertical = 6.dp),
                ) {
                    RadioButton(
                        selected = shown.kind == kind,
                        onClick = null,
                        enabled = available && !shown.busy,
                    )
                    Column(Modifier.padding(start = 8.dp)) {
                        Text(
                            kind.label,
                            color = if (available) {
                                OmaykanTheme.colors.ink
                            } else {
                                OmaykanTheme.colors.textTertiary
                            },
                        )
                        if (!available) {
                            Text(
                                "Already saved.",
                                style = MaterialTheme.typography.labelSmall,
                                color = OmaykanTheme.colors.textTertiary,
                            )
                        }
                    }
                }
            }

            if (shown.needsDetail) {
                OutlinedTextField(
                    value = shown.detail,
                    onValueChange = viewModel::onDetail,
                    label = { Text("GCash number") },
                    singleLine = true,
                    enabled = !shown.busy,
                    isError = shown.fieldErrors.containsKey("detail"),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    supportingText = {
                        Text(shown.fieldErrors["detail"] ?: "So the shop knows who is sending.")
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            SectionError(shown.error)

            SectionPrimaryButton(
                label = "Save",
                onClick = viewModel::saveForm,
                enabled = shown.canSave,
                busy = shown.busy,
                modifier = Modifier.padding(top = 4.dp),
            )

            Spacer(Modifier.height(24.dp))
        }
    }
}
