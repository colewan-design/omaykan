package com.omaykan.storefront.feature.account

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.omaykan.storefront.core.designsystem.CompactSnackbarHost
import com.omaykan.storefront.core.designsystem.CtaButton
import com.omaykan.storefront.core.designsystem.ForestTopBar
import com.omaykan.storefront.core.designsystem.OmaykanTheme

/**
 * The frame every account section shares: the forest bar with a title and a
 * way back, and one place for the sentence that says a save went through.
 *
 * A snackbar rather than a banner for the confirmation, because these screens
 * are forms: a banner appearing above a field pushes the field the shopper is
 * looking at down the screen, which is a worse way to say "saved" than saying
 * nothing at all. The app's own compact host, not Material's bar, so that a
 * save confirms itself here in the same words and the same place as every
 * other message in the app.
 *
 * @param notice shown once and then cleared through [onNoticeShown].
 */
@Composable
fun AccountSectionScaffold(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    notice: String? = null,
    onNoticeShown: () -> Unit = {},
    scrollable: Boolean = true,
    content: @Composable (Modifier) -> Unit,
) {
    val snackbars = remember { SnackbarHostState() }

    LaunchedEffect(notice) {
        val message = notice ?: return@LaunchedEffect
        snackbars.showSnackbar(message)
        onNoticeShown()
    }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { CompactSnackbarHost(snackbars) },
        topBar = { ForestTopBar(title = title, onBack = onBack) },
    ) { padding ->
        val inner = Modifier
            .padding(padding)
            .fillMaxWidth()
            .let { if (scrollable) it.verticalScroll(rememberScrollState()) else it }
            .padding(horizontal = 20.dp)

        content(inner)
    }
}

/** The one full-width action at the bottom of a form. */
@Composable
fun SectionPrimaryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    busy: Boolean = false,
) {
    CtaButton(
        text = label,
        onClick = onClick,
        enabled = enabled,
        busy = busy,
        shape = RoundedCornerShape(12.dp),
        modifier = modifier.fillMaxWidth(),
    )
}

/** A heading inside a form, above the fields it groups. */
@Composable
fun SectionHeading(text: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(
            text,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = OmaykanTheme.colors.ink,
        )
    }
}

@Composable
fun SectionError(message: String?, modifier: Modifier = Modifier) {
    if (message == null) return

    Text(
        message,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error,
        modifier = modifier,
    )
}
