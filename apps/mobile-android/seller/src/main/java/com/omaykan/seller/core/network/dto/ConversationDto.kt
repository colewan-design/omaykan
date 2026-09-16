package com.omaykan.seller.core.network.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/*
 * Customer messages — `SellerConversationController`, `Conversation::toStoreSummary`
 * and `ConversationMessage::toStoreArray`.
 */

@Serializable
data class ConversationsDto(
    val conversations: List<ConversationSummaryDto> = emptyList(),
)

@Serializable
data class ConversationSummaryDto(
    val id: String,
    val customer: ConversationCustomerDto? = null,
    val lastMessage: ConversationMessageDto? = null,
    val lastMessageAt: String? = null,
    val unreadCount: Int = 0,
)

@Serializable
data class ConversationCustomerDto(
    val name: String? = null,
)

@Serializable
data class ConversationMessageDto(
    /** An auto-increment id on the server — a number, read raw. */
    val id: JsonElement? = null,
    /** "customer" or "store". */
    val from: String? = null,
    val body: String = "",
    val order: MessageOrderRefDto? = null,
    val createdAt: String? = null,
    /** The staff member's first name, on the shop's own replies. */
    val authorName: String? = null,
)

@Serializable
data class MessageOrderRefDto(
    val id: String? = null,
    val ticketNumber: String? = null,
)

/** One thread, with this customer's last few orders at this shop beside it. */
@Serializable
data class ConversationThreadDto(
    val conversation: ConversationSummaryDto,
    val messages: List<ConversationMessageDto> = emptyList(),
    val recentOrders: List<ThreadOrderDto> = emptyList(),
)

@Serializable
data class ThreadOrderDto(
    val id: String,
    val ticketNumber: String? = null,
    val status: String? = null,
    val totalCents: Long = 0,
    val createdAt: String? = null,
)

@Serializable
data class ReplyRequestDto(val body: String)

@Serializable
data class UnreadDto(val unread: Int = 0)
