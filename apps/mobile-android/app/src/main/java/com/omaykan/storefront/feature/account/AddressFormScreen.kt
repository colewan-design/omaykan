package com.omaykan.storefront.feature.account

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.omaykan.storefront.core.designsystem.OmaykanTheme

/**
 * Adding or editing one delivery address.
 *
 * A screen rather than the dialog this used to be. Five fields, their errors
 * and a scrollbar inside an AlertDialog is a form read through a letterbox: on
 * a phone with the keyboard up there was more furniture than form, and the
 * Save button spent most of its life off the bottom of the box.
 *
 * The form itself still lives in [AddressesViewModel], resolved against the
 * list's back-stack entry rather than a view model of this screen's own. That
 * is what lets "Address saved." land as a notice on the list this pops back to,
 * and it keeps a home address out of the back stack's saved state — the same
 * bargain the order review screen strikes with checkout.
 */
@Composable
fun AddressFormScreen(
    viewModel: AddressesViewModel,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val form = state.form

    /*
     * Clearing the form is the only way out of this screen.
     *
     * Cancel clears it, a completed save clears it, and process death restores
     * this destination over a view model that never had one. All three arrive
     * as the same null, and all three should leave the shopper on the list.
     */
    LaunchedEffect(form == null) { if (form == null) onDone() }

    /*
     * The form as it should still look on the way out.
     *
     * A save clears it and then this screen is popped, but the nav host
     * animates the screen away over the frames after that — by which point
     * there would be nothing left to draw. Holding the last one keeps Save
     * from fading out a blank screen.
     */
    val held = remember { mutableStateOf(form) }
    form?.let { held.value = it }
    val shown = held.value ?: return

    /*
     * System Back goes through the view model rather than straight to the nav
     * host, so backing out clears the form instead of leaving a filled-in one
     * behind a screen nobody is looking at. While a save is in flight it does
     * nothing at all: the dialog refused to dismiss for the same reason, and a
     * shopper dropped onto the list mid-write has no way to learn which answer
     * the server gave.
     */
    BackHandler { if (!shown.busy) viewModel.closeForm() }

    AccountSectionScaffold(
        title = if (shown.isNew) "Add an address" else "Edit address",
        onBack = { if (!shown.busy) viewModel.closeForm() },
        modifier = modifier,
    ) { inner ->
        Column(inner, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            AddressField(
                value = shown.label,
                label = "Name it",
                placeholder = "Home, Mum's, the office",
                error = shown.fieldErrors["label"],
                enabled = !shown.busy,
            ) { value -> viewModel.onFormChange { it.copy(label = value) } }

            AddressField(
                value = shown.line1,
                label = "Street address",
                error = shown.fieldErrors["line1"],
                enabled = !shown.busy,
            ) { value -> viewModel.onFormChange { it.copy(line1 = value) } }

            AddressField(
                value = shown.barangay,
                label = "Barangay (optional)",
                error = shown.fieldErrors["barangay"],
                enabled = !shown.busy,
            ) { value -> viewModel.onFormChange { it.copy(barangay = value) } }

            AddressField(
                value = shown.city,
                label = "City",
                error = shown.fieldErrors["city"],
                enabled = !shown.busy,
            ) { value -> viewModel.onFormChange { it.copy(city = value) } }

            AddressField(
                value = shown.notes,
                label = "Landmark (optional)",
                placeholder = "The green gate past the court",
                error = shown.fieldErrors["notes"],
                enabled = !shown.busy,
            ) { value -> viewModel.onFormChange { it.copy(notes = value) } }

            // Typing cannot produce a pin — there is no geocoder in this
            // project — so the form reports the pin rather than offering to
            // set one. Saving from a device location is checkout's job.
            if (shown.pinned) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "This address has a map pin.",
                        style = MaterialTheme.typography.bodySmall,
                        color = OmaykanTheme.colors.textSecondary,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = viewModel::clearPin, enabled = !shown.busy) {
                        Text("Clear")
                    }
                }
            }

            SectionError(shown.error)

            SectionPrimaryButton(
                label = "Save",
                onClick = viewModel::saveForm,
                enabled = shown.canSave,
                busy = shown.busy,
            )

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun AddressField(
    value: String,
    label: String,
    error: String?,
    enabled: Boolean,
    placeholder: String? = null,
    onValueChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it) } },
        singleLine = true,
        enabled = enabled,
        isError = error != null,
        supportingText = error?.let { { Text(it) } },
        modifier = Modifier.fillMaxWidth(),
    )
}
