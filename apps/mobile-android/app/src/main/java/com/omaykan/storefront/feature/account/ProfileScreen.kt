package com.omaykan.storefront.feature.account

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.omaykan.storefront.core.designsystem.LoadingState
import com.omaykan.storefront.core.designsystem.OmaykanTheme
import com.omaykan.storefront.core.model.Substitution

/**
 * Name, phone, and the decisions that change what happens to an order.
 *
 * Asked here rather than at checkout, where every extra question costs an
 * order — the same call the web portal makes. The email is not on this screen:
 * it is a credential, and it lives behind the current password in
 * [SecurityScreen].
 */
@Composable
fun ProfileScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ProfileViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    AccountSectionScaffold(
        title = "Your details",
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
            OutlinedTextField(
                value = state.name,
                onValueChange = viewModel::onName,
                label = { Text("Your name") },
                singleLine = true,
                enabled = !state.busy,
                isError = state.fieldErrors.containsKey("name"),
                supportingText = { state.fieldErrors["name"]?.let { Text(it) } },
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = state.phone,
                onValueChange = viewModel::onPhone,
                label = { Text("Phone") },
                singleLine = true,
                enabled = !state.busy,
                isError = state.fieldErrors.containsKey("phone"),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                supportingText = {
                    Text(
                        state.fieldErrors["phone"]
                            ?: "How a rider reaches you if they cannot find the door.",
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(8.dp))
            SectionHeading("When something is out of stock")
            Text(
                "What a picker should do about a line they cannot fill.",
                style = MaterialTheme.typography.bodySmall,
                color = OmaykanTheme.colors.textSecondary,
            )

            Substitution.entries.forEach { choice ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(
                            selected = state.preferences.substitutions == choice,
                            enabled = !state.busy,
                            onClick = { viewModel.onSubstitutions(choice) },
                        )
                        .padding(vertical = 4.dp),
                ) {
                    RadioButton(
                        selected = state.preferences.substitutions == choice,
                        onClick = null,
                        enabled = !state.busy,
                    )
                    Column(Modifier.padding(start = 8.dp)) {
                        Text(
                            choice.label,
                            style = MaterialTheme.typography.bodyLarge,
                            color = OmaykanTheme.colors.ink,
                        )
                        Text(
                            choice.detail,
                            style = MaterialTheme.typography.bodySmall,
                            color = OmaykanTheme.colors.textSecondary,
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            HorizontalDivider(color = OmaykanTheme.colors.separator)
            Spacer(Modifier.height(8.dp))

            SectionHeading("How we reach you")

            PreferenceToggle(
                title = "Order updates by email",
                detail = "Confirmations and status changes.",
                checked = state.preferences.emailUpdates,
                enabled = !state.busy,
                onChange = viewModel::onEmailUpdates,
            )
            PreferenceToggle(
                title = "Order updates by text",
                detail = "The same updates, to your phone.",
                checked = state.preferences.smsUpdates,
                enabled = !state.busy,
                onChange = viewModel::onSmsUpdates,
            )
            PreferenceToggle(
                title = "Offers and news",
                detail = "Occasional email from the shops you order from.",
                checked = state.preferences.marketingEmails,
                enabled = !state.busy,
                onChange = viewModel::onMarketingEmails,
            )

            SectionError(state.error)

            Spacer(Modifier.height(8.dp))

            SectionPrimaryButton(
                label = "Save",
                onClick = viewModel::save,
                enabled = state.canSave,
                busy = state.busy,
            )

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun PreferenceToggle(
    title: String,
    detail: String,
    checked: Boolean,
    enabled: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            // The whole row, not just the switch: a 32dp target beside a
            // sentence is a target people miss.
            .clickable(enabled = enabled) { onChange(!checked) }
            .padding(vertical = 6.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = OmaykanTheme.colors.ink,
            )
            Text(
                detail,
                style = MaterialTheme.typography.bodySmall,
                color = OmaykanTheme.colors.textSecondary,
            )
        }
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}
