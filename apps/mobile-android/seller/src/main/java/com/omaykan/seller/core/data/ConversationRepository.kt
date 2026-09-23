package com.omaykan.seller.core.data

import com.omaykan.seller.core.model.ConversationSummary
import com.omaykan.seller.core.model.ConversationThread
import com.omaykan.seller.core.network.ApiCaller
import com.omaykan.seller.core.network.SellerApi
import com.omaykan.seller.core.network.dto.ReplyRequestDto
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Customer messages to this shop.
 *
 * A shop answers and never starts: there is no endpoint to open a thread with
 * a customer, on purpose — see SellerConversationController. So the inbox is
 * only ever as long as the number of people who chose to write.
 */
@Singleton
class ConversationRepository @Inject constructor(
    private val api: SellerApi,
    private val caller: ApiCaller,
) {
    suspend fun conversations(): List<ConversationSummary> =
        caller.call { api.conversations() }.conversations.map { it.toModel() }

    suspend fun unread(): Int = caller.call { api.unreadMessages() }.unread

    /** Opening a thread marks it read, server-side. */
    suspend fun thread(id: String): ConversationThread =
        caller.call { api.conversation(id) }.toModel()

    suspend fun reply(id: String, body: String): ConversationThread =
        caller.call { api.reply(id, ReplyRequestDto(body.trim())) }.toModel()
}
