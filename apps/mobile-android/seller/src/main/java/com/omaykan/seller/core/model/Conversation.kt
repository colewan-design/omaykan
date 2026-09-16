package com.omaykan.seller.core.model

/** One customer's thread with this shop, as the inbox lists it. */
data class ConversationSummary(
    val id: String,
    val customerName: String,
    val lastMessage: Message?,
    val lastMessageAt: String?,
    val unreadCount: Int,
)

data class Message(
    val id: String,
    /** True for the shop's own replies — the bubbles on the right. */
    val fromStore: Boolean,
    val body: String,
    /** The ticket a message was sent about, when it was. */
    val orderTicket: String?,
    val createdAt: String?,
    /** Which member of staff answered, by first name. */
    val authorName: String?,
)

/** One of the customer's recent orders here, shown above the thread. */
data class ThreadOrder(
    val id: String,
    val ticketNumber: String?,
    val status: OrderStatus,
    val totalCents: Long,
    val createdAt: String?,
)

data class ConversationThread(
    val summary: ConversationSummary,
    val messages: List<Message>,
    val recentOrders: List<ThreadOrder>,
)
