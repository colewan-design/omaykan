package com.omaykan.seller.feature.messages

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.omaykan.seller.core.designsystem.ButtonShape
import com.omaykan.seller.core.designsystem.ChipTabs
import com.omaykan.seller.core.designsystem.CountBadge
import com.omaykan.seller.core.designsystem.ForestTopBar
import com.omaykan.seller.core.designsystem.InitialAvatar
import com.omaykan.seller.core.designsystem.PillShape
import com.omaykan.seller.core.designsystem.ScreenMessage
import com.omaykan.seller.core.designsystem.SellerTheme
import com.omaykan.seller.core.designsystem.TabItem
import com.omaykan.seller.core.model.ConversationSummary
import com.omaykan.seller.core.model.Message
import com.omaykan.seller.core.model.Money
import com.omaykan.seller.core.model.ThreadOrder
import com.omaykan.seller.core.model.relativeStamp

/**
 * Customer messages. "All" and "Unread" rather than the reference's
 * "Customers" and "System": every thread here is a customer — the platform
 * sends shops no system messages to file under a tab of their own.
 */
@Composable
fun MessagesScreen(
    onBack: () -> Unit,
    onOpenThread: (id: String, customerName: String) -> Unit,
    viewModel: MessagesViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        ForestTopBar(
            title = "Messages",
            onBack = onBack,
            actions = {
                IconButton(onClick = viewModel::refresh) {
                    Icon(Icons.Filled.Refresh, "Refresh messages", tint = SellerTheme.colors.onCanopy)
                }
            },
        )

        ChipTabs(
            // A count on Unread only: beside "All" it would restate the length
            // of the list under it.
            tabs = InboxFilter.entries.map {
                TabItem(it.label, if (it == InboxFilter.Unread) state.count(it) else null)
            },
            selected = InboxFilter.entries.indexOf(state.filter),
            onSelect = { viewModel.onFilter(InboxFilter.entries[it]) },
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            equalWidth = true,
            filledSelected = true,
        )

        val rows = state.visible
        when {
            state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(strokeWidth = 2.dp)
            }

            state.conversations.isEmpty() && state.error != null -> Box(
                Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                ScreenMessage(
                    icon = Icons.Filled.CloudOff,
                    title = "Couldn't load messages",
                    body = state.error.orEmpty(),
                    actionLabel = "Try again",
                    onAction = viewModel::refresh,
                )
            }

            rows.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                ScreenMessage(
                    icon = Icons.Filled.ChatBubbleOutline,
                    title = if (state.filter == InboxFilter.Unread) "You're all caught up" else "No messages yet",
                    body = if (state.filter == InboxFilter.Unread) {
                        "Every customer message has been read."
                    } else {
                        "When a customer writes to your shop from the Omaykan app, it lands here."
                    },
                )
            }

            else -> LazyColumn(Modifier.fillMaxSize()) {
                items(rows, key = { it.id }) { conversation ->
                    ConversationRow(conversation) { onOpenThread(conversation.id, conversation.customerName) }
                    HorizontalDivider(
                        Modifier.padding(start = 76.dp),
                        color = SellerTheme.colors.separator,
                    )
                }
            }
        }
    }
}

/**
 * One thread, as the reference lays it out: the customer, their name over the
 * last line said, and down the right side when and how many are unread, then
 * a chevron. Unread threads carry a red count and a darker preview, so the
 * ones waiting on the shop stand out without anybody reading a word.
 */
@Composable
private fun ConversationRow(conversation: ConversationSummary, onClick: () -> Unit) {
    val unread = conversation.unreadCount > 0
    val preview = conversation.lastMessage?.let { (if (it.fromStore) "You: " else "") + it.body }.orEmpty()

    Row(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        InitialAvatar(text = conversation.customerName, size = 48)
        Column(
            Modifier
                .weight(1f)
                .padding(horizontal = 12.dp),
        ) {
            Text(
                text = conversation.customerName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (unread) FontWeight.SemiBold else FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = preview,
                style = MaterialTheme.typography.bodyMedium,
                color = if (unread) MaterialTheme.colorScheme.onSurface else SellerTheme.colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = relativeStamp(conversation.lastMessageAt).orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                color = SellerTheme.colors.textTertiary,
            )
            if (unread) {
                CountBadge(
                    conversation.unreadCount,
                    Modifier.padding(top = 6.dp),
                    ground = SellerTheme.colors.danger,
                )
            } else {
                // Holds the row's height steady, so read and unread rows line up.
                Spacer(Modifier.height(24.dp))
            }
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = SellerTheme.colors.textTertiary,
            modifier = Modifier.padding(start = 4.dp),
        )
    }
}

/**
 * One conversation: the customer's recent orders here along the top — "where's
 * my order" is most of what a shop gets asked — then the thread, then a box to
 * answer in.
 */
@Composable
fun ThreadScreen(
    onBack: () -> Unit,
    viewModel: ThreadViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val thread = state.thread

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .navigationBarsPadding()
            .imePadding(),
    ) {
        ForestTopBar(title = state.customerName, subtitle = "Customer", onBack = onBack)

        Box(Modifier.weight(1f)) {
            when {
                thread == null && state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(strokeWidth = 2.dp)
                }

                thread == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    ScreenMessage(
                        icon = Icons.Filled.CloudOff,
                        title = "Couldn't open this conversation",
                        body = state.error.orEmpty(),
                        actionLabel = "Try again",
                        onAction = viewModel::refresh,
                    )
                }

                else -> Column(Modifier.fillMaxSize()) {
                    if (thread.recentOrders.isNotEmpty()) {
                        RecentOrders(thread.recentOrders)
                    }
                    MessageList(thread.messages, Modifier.weight(1f))
                }
            }
        }

        if (thread != null) {
            state.sendError?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = SellerTheme.colors.danger,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
            Composer(
                draft = state.draft,
                onDraft = viewModel::onDraft,
                canSend = state.canSend,
                sending = state.sending,
                onSend = viewModel::send,
            )
        }
    }
}

@Composable
private fun RecentOrders(orders: List<ThreadOrder>) {
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        orders.forEach { order ->
            Text(
                text = listOf(
                    order.ticketNumber?.let { "#$it" } ?: "Order",
                    order.status.label,
                    Money.peso(order.totalCents),
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = SellerTheme.colors.onAccentSoft,
                modifier = Modifier
                    .clip(PillShape)
                    .background(SellerTheme.colors.accentSoft)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
    }
}

@Composable
private fun MessageList(messages: List<Message>, modifier: Modifier = Modifier) {
    val listState = rememberLazyListState()

    // Open at the newest message, and follow a new one in.
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        state = listState,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(messages, key = { it.id.ifBlank { it.hashCode().toString() } }) { message ->
            Bubble(message)
        }
    }
}

@Composable
private fun Bubble(message: Message) {
    val mine = message.fromStore

    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = if (mine) Alignment.End else Alignment.Start,
    ) {
        message.orderTicket?.let {
            Text(
                text = "About order #$it",
                style = MaterialTheme.typography.bodySmall,
                color = SellerTheme.colors.textTertiary,
                modifier = Modifier.padding(bottom = 3.dp),
            )
        }
        Box(
            Modifier
                .widthIn(max = 300.dp)
                .clip(
                    RoundedCornerShape(
                        topStart = 16.dp,
                        topEnd = 16.dp,
                        bottomEnd = if (mine) 4.dp else 16.dp,
                        bottomStart = if (mine) 16.dp else 4.dp,
                    ),
                )
                .background(if (mine) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface)
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            Text(
                text = message.body,
                style = MaterialTheme.typography.bodyMedium,
                color = if (mine) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
            )
        }
        Text(
            // Which of the staff answered, on the shop's own side — a phone
            // shared across a shift should not leave a reply anonymous.
            text = listOfNotNull(
                if (mine) message.authorName else null,
                relativeStamp(message.createdAt),
            ).joinToString(" · "),
            style = MaterialTheme.typography.bodySmall,
            color = SellerTheme.colors.textTertiary,
            modifier = Modifier.padding(top = 3.dp),
        )
    }
}

@Composable
private fun Composer(
    draft: String,
    onDraft: (String) -> Unit,
    canSend: Boolean,
    sending: Boolean,
    onSend: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = draft,
            onValueChange = onDraft,
            modifier = Modifier.weight(1f),
            placeholder = { Text("Write a reply…") },
            maxLines = 4,
            enabled = !sending,
            shape = ButtonShape,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedBorderColor = SellerTheme.colors.separator,
            ),
        )
        Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
            if (sending) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
                IconButton(onClick = onSend, enabled = canSend) {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send",
                        tint = if (canSend) MaterialTheme.colorScheme.primary else SellerTheme.colors.textTertiary,
                    )
                }
            }
        }
    }
}
