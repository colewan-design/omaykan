package com.omaykan.seller.core.data

import com.omaykan.seller.core.model.OrderingState
import com.omaykan.seller.core.model.PauseLength
import com.omaykan.seller.core.network.ApiCaller
import com.omaykan.seller.core.network.SellerApi
import com.omaykan.seller.core.network.dto.OrderingRequestDto
import com.omaykan.seller.core.network.dto.OrderingStateDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.OffsetDateTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The shop's own switch for online orders.
 *
 * Any role with the Orders page may use it, cashiers included — the server
 * decides, and answers 403 for anyone else. Held as a flow so the home card
 * and anything else that shows it agree without a second fetch.
 *
 * See documentation/merchant-features.md §3.
 */
@Singleton
class OrderingRepository @Inject constructor(
    private val api: SellerApi,
    private val caller: ApiCaller,
) {
    private val _state = MutableStateFlow<OrderingState?>(null)
    val state: StateFlow<OrderingState?> = _state.asStateFlow()

    suspend fun refresh() {
        _state.value = caller.call { api.ordering() }.toModel()
    }

    suspend fun pause(length: PauseLength) {
        val resumesAt = length.resumesAt()?.toString()
        _state.value = caller.call { api.setOrdering(OrderingRequestDto(paused = true, resumesAt = resumesAt)) }.toModel()
    }

    suspend fun reopen() {
        _state.value = caller.call { api.setOrdering(OrderingRequestDto(paused = false)) }.toModel()
    }

    private fun OrderingStateDto.toModel() = OrderingState(
        paused = paused,
        resumesAt = resumesAt?.let { runCatching { OffsetDateTime.parse(it) }.getOrNull() },
        message = message,
    )
}
