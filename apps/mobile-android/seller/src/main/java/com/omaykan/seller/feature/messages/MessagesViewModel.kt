package com.omaykan.seller.feature.messages

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omaykan.seller.core.data.ConversationRepository
import com.omaykan.seller.core.model.ConversationSummary
import com.omaykan.seller.core.model.ConversationThread
import com.omaykan.seller.core.network.ApiException
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

/** `Conversation::MAX_BODY`. The server refuses anything longer with a 422. */
const val MAX_MESSAGE_LENGTH = 2000

private const val INBOX_POLL_MS = 20_000L
private const val THREAD_POLL_MS = 10_000L

enum class InboxFilter(val label: String) {
    All("All"),
    Unread("Unread"),
}

data class MessagesUiState(
    val conversations: List<ConversationSummary> = emptyList(),
    val loading: Boolean = true,
    val error: String? = null,
    val filter: InboxFilter = InboxFilter.All,
) {
    val visible: List<ConversationSummary>
        get() = if (filter == InboxFilter.Unread) conversations.filter { it.unreadCount > 0 } else conversations

    fun count(filter: InboxFilter): Int = when (filter) {
        InboxFilter.All -> conversations.size
        InboxFilter.Unread -> conversations.count { it.unreadCount > 0 }
    }
}

/**
 * The inbox, polled while it is on screen.
 *
 * Polled for the same reason the orders are — see OrderFeed — and only while
 * collected: `collectAsStateWithLifecycle` stops at STOPPED and a covered
 * NavHost destination leaves composition, so opening a thread, or putting the
 * phone down, stops the loop, and coming back re-reads a list whose unread
 * counts have changed.
 */
@HiltViewModel
class MessagesViewModel @Inject constructor(
    private val repository: ConversationRepository,
) : ViewModel() {

    private val filter = MutableStateFlow(InboxFilter.All)
    private val list = MutableStateFlow<List<ConversationSummary>?>(null)
    private val error = MutableStateFlow<String?>(null)
    private val ticks = Channel<Unit>(Channel.CONFLATED)

    private val poller = flow {
        while (true) {
            try {
                list.value = repository.conversations()
                error.value = null
            } catch (e: ApiException) {
                error.value = e.message
            }
            emit(Unit)
            withTimeoutOrNull(INBOX_POLL_MS) { ticks.receive() }
        }
    }

    val state: StateFlow<MessagesUiState> =
        combine(list, error, filter, poller.onStart { emit(Unit) }) { rows, failure, chosen, _ ->
            MessagesUiState(
                conversations = rows.orEmpty(),
                loading = rows == null && failure == null,
                error = failure,
                filter = chosen,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MessagesUiState())

    fun onFilter(value: InboxFilter) = filter.update { value }

    fun refresh() {
        ticks.trySend(Unit)
    }
}

data class ThreadUiState(
    val customerName: String,
    val thread: ConversationThread? = null,
    val loading: Boolean = true,
    val error: String? = null,
    val draft: String = "",
    val sending: Boolean = false,
    val sendError: String? = null,
) {
    val canSend: Boolean
        get() = !sending && draft.isNotBlank() && draft.trim().length <= MAX_MESSAGE_LENGTH
}

/**
 * One customer's thread. Reading it marks it read for the shop, server-side,
 * so the inbox badge falls the moment this opens.
 */
@HiltViewModel
class ThreadViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: ConversationRepository,
) : ViewModel() {

    private val id: String = checkNotNull(savedStateHandle["id"]) { "A thread needs its conversation id." }
    private val name: String = savedStateHandle.get<String>("name").orEmpty().ifBlank { "Customer" }

    private val current = MutableStateFlow<ConversationThread?>(null)
    private val loadError = MutableStateFlow<String?>(null)
    private val local = MutableStateFlow(Local())
    private val ticks = Channel<Unit>(Channel.CONFLATED)

    private data class Local(
        val draft: String = "",
        val sending: Boolean = false,
        val sendError: String? = null,
    )

    private val poller = flow {
        while (true) {
            try {
                current.value = repository.thread(id)
                loadError.value = null
            } catch (e: ApiException) {
                loadError.value = e.message
            }
            emit(Unit)
            withTimeoutOrNull(THREAD_POLL_MS) { ticks.receive() }
        }
    }

    val state: StateFlow<ThreadUiState> =
        combine(current, loadError, local, poller.onStart { emit(Unit) }) { thread, failure, screen, _ ->
            ThreadUiState(
                customerName = thread?.summary?.customerName ?: name,
                thread = thread,
                loading = thread == null && failure == null,
                error = failure,
                draft = screen.draft,
                sending = screen.sending,
                sendError = screen.sendError,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ThreadUiState(customerName = name))

    fun onDraft(value: String) = local.update {
        it.copy(draft = value.take(MAX_MESSAGE_LENGTH), sendError = null)
    }

    /**
     * Send the reply. The server answers with the whole thread, which replaces
     * the one on screen, so the new bubble appears without waiting for a poll.
     */
    fun send() {
        val body = local.value.draft.trim()
        if (body.isEmpty() || local.value.sending) return

        local.update { it.copy(sending = true, sendError = null) }

        viewModelScope.launch {
            try {
                current.value = repository.reply(id, body)
                local.update { it.copy(draft = "", sending = false) }
            } catch (e: ApiException) {
                // The draft stays, so the words are not lost to a dropped signal.
                local.update { it.copy(sending = false, sendError = e.message) }
            }
        }
    }

    fun refresh() {
        ticks.trySend(Unit)
    }
}
