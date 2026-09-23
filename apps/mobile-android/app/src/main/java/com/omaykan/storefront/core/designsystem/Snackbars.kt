package com.omaykan.storefront.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.collectLatest

/**
 * One line, said once — "Added to cart", "Saved".
 *
 * The whole vocabulary of this app's acknowledgements. They are short on
 * purpose: a shopper who tapped a button knows what they tapped, and the only
 * thing missing was proof it landed.
 */
data class BriefMessage(
    val text: String,
    /** The one thing worth offering alongside, e.g. "View cart". Usually none. */
    val actionLabel: String? = null,
)

/**
 * The view-model half of the snackbar.
 *
 * An event stream rather than a piece of state: hearting the same product twice
 * is two things that happened, and a state field holding "Saved" cannot tell
 * the second tap from the first. Buffered and dropped rather than suspended —
 * the tap has already changed the basket or the list, and it must never wait on
 * whether a screen is listening.
 */
class SnackbarMessages {
    private val _messages = MutableSharedFlow<BriefMessage>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val messages: SharedFlow<BriefMessage> = _messages.asSharedFlow()

    fun send(text: String, actionLabel: String? = null) {
        _messages.tryEmit(BriefMessage(text, actionLabel))
    }
}

/**
 * Pipes a view model's messages into a host.
 *
 * collectLatest, so a second tap cancels the message from the first rather than
 * queueing behind it: someone hearting three things in a row should see the
 * third one answered, not watch two stale confirmations play out first.
 */
@Composable
fun SnackbarMessageEffect(
    messages: SnackbarMessages,
    hostState: SnackbarHostState,
    onAction: () -> Unit = {},
) {
    val action by rememberUpdatedState(onAction)

    LaunchedEffect(messages, hostState) {
        messages.messages.collectLatest { message ->
            val result = hostState.showSnackbar(
                message = message.text,
                actionLabel = message.actionLabel,
                duration = SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) action()
        }
    }
}

/**
 * The app's message prompt: the middle of the screen, and no wider than the
 * words in it.
 *
 * Material's default is a full-bleed bar along the bottom edge with generous
 * padding, which for "Saved" is a lot of furniture around one word. This one
 * wraps its text and lands where the shopper is already looking — the thumb
 * that just tapped is at the bottom of the screen, and the eyes are not.
 *
 * A Popup rather than a Box, so that the message is centred on the screen and
 * not on whatever slot it was hung in: two of the three callers pass this to
 * `Scaffold(snackbarHost = …)`, and a Scaffold pins that slot to the bottom.
 * The whole host goes inside the popup rather than each message, because
 * SnackbarHost fades its content in and out with a graphics layer, and a layer
 * on this side of the window boundary would not reach a child living on the
 * other side of it.
 */
@Composable
fun CompactSnackbarHost(hostState: SnackbarHostState, modifier: Modifier = Modifier) {
    Popup(alignment = Alignment.Center) {
        SnackbarHost(hostState, modifier) { data -> CompactSnackbar(data) }
    }
}

@Composable
private fun CompactSnackbar(data: SnackbarData) {
    val label = data.visuals.actionLabel

    Row(
        Modifier
            .widthIn(max = 320.dp)
            .clip(RoundedCornerShape(10.dp))
            // Not quite opaque. Sitting over the middle of the screen rather
            // than below it, the card covers something the shopper was reading
            // a moment ago, and letting a little of it through is what keeps
            // the message reading as a note laid on the page instead of a hole
            // punched in it. Only the ground is thinned — the words on it stay
            // at full strength.
            .background(OmaykanTheme.colors.ink.copy(alpha = 0.85f))
            .padding(
                start = 14.dp,
                end = if (label == null) 14.dp else 4.dp,
                top = 8.dp,
                bottom = 8.dp,
            ),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = data.visuals.message,
            style = MaterialTheme.typography.bodySmall,
            color = OmaykanTheme.colors.onInk,
            textAlign = TextAlign.Center,
        )
        if (label != null) {
            TextButton(
                onClick = data::performAction,
                contentPadding = PaddingValues(horizontal = 10.dp),
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = OmaykanTheme.colors.onInk,
                )
            }
        }
    }
}
