package com.omaykan.storefront.feature.order

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.omaykan.storefront.core.data.CatalogRepository
import com.omaykan.storefront.core.data.OrderRepository
import com.omaykan.storefront.core.model.TrackedOrder
import com.omaykan.storefront.core.network.ApiException
import com.omaykan.storefront.core.push.PushRegistrar
import com.omaykan.storefront.navigation.OrderRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.coroutines.coroutineContext

data class OrderUiState(
    val order: TrackedOrder? = null,
    /**
     * Item photos by product id, for whatever this device still has cached —
     * the same best-effort lookup the Orders tab does. Absent ids draw a
     * placeholder; see CatalogRepository.photosFor.
     */
    val photos: Map<String, String> = emptyMap(),
    val loading: Boolean = true,
    /**
     * A refresh the shopper asked for, as opposed to the 20s poll.
     *
     * Kept apart from [loading] because they mean different things to the
     * screen: loading is "there is nothing to show yet", this is "what you are
     * looking at is being checked". The poll deliberately does not set it — a
     * spinner appearing on its own every twenty seconds, on a screen someone is
     * holding at a door, reads as the page being broken.
     */
    val refreshing: Boolean = false,
    val error: String? = null,
)

/**
 * One order, polled.
 *
 * Every twenty seconds, matching the web storefront. The Reverb socket that
 * would make this live is Phase 3 — the server already broadcasts
 * `order.status-changed` and `order.delivery-updated` on the public
 * `order.{uuid}` channel, and nothing subscribes yet. When it does, this poll
 * stays: mobile sockets die silently, and a tracking screen that has quietly
 * stopped updating is worse than one that never claimed to be live.
 *
 * Polling stops at a terminal status, so a completed order is not a request
 * every twenty seconds for as long as the screen is open.
 */
@HiltViewModel
class OrderViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: OrderRepository,
    private val catalogRepository: CatalogRepository,
    private val pushRegistrar: PushRegistrar,
) : ViewModel() {

    private val route = savedStateHandle.toRoute<OrderRoute>()

    private val _state = MutableStateFlow(OrderUiState())
    val state: StateFlow<OrderUiState> = _state.asStateFlow()

    /**
     * The ids the photo map was built for.
     *
     * An order's lines do not change once it is placed, so this is a single
     * cache lookup rather than one on every poll for as long as the screen is
     * open — and it still re-reads if the server ever answers with a different
     * set.
     */
    private var photographed: Set<String> = emptySet()

    init {
        viewModelScope.launch {
            while (coroutineContext.isActive) {
                load()
                if (_state.value.order?.status in TERMINAL) return@launch
                delay(pollInterval())
            }
        }
    }

    /**
     * How long to wait before asking again.
     *
     * Twenty seconds is right for "is it being made yet" — that answer changes
     * a handful of times an hour. It is wrong for a marker moving across a map:
     * the rider's phone reports every ten seconds, and a screen that reads it
     * every twenty draws a rider who jumps a block at a time.
     *
     * So the interval halves while there is actually a position to watch, and
     * goes back up the moment there is not. An order waiting for a rider, or
     * carried by somebody the shop rang personally, costs exactly what it
     * always did.
     */
    private fun pollInterval(): Long {
        val position = _state.value.order?.riderPosition
        return if (position != null && !position.stale) MOVING_POLL_MS else POLL_MS
    }

    fun refresh() {
        if (_state.value.refreshing) return

        _state.update { it.copy(refreshing = true) }
        viewModelScope.launch {
            load()
            _state.update { it.copy(refreshing = false) }
        }
    }

    private suspend fun load() {
        try {
            val order = repository.track(route.orderId)
            _state.update {
                it.copy(order = order, loading = false, error = null)
            }
            // Every poll, not once: the registrar skips what it has already
            // sent, and re-sends after the phone's token changes.
            if (order.awaitsRider) pushRegistrar.register(order.orderId)
            loadPhotos(order)
        } catch (e: ApiException) {
            // A poll that failed must not blank an order already on screen —
            // the shopper is standing at a door with this open.
            _state.update {
                it.copy(loading = false, error = if (it.order == null) e.message else null)
            }
        }
    }

    /**
     * Best effort, and off the local cache only: a picture is worth having
     * where the device already has one, and is never worth failing an order
     * somebody is trying to read.
     */
    private suspend fun loadPhotos(order: TrackedOrder) {
        val ids = order.items.map { it.productId }.filterNot { it.isBlank() }.toSet()
        if (ids == photographed) return

        val photos = runCatching { catalogRepository.photosFor(ids) }.getOrNull() ?: return
        photographed = ids
        _state.update { it.copy(photos = photos) }
    }

    private companion object {
        const val POLL_MS = 20_000L

        /** While a rider is actually reporting — see [pollInterval]. */
        const val MOVING_POLL_MS = 10_000L
        val TERMINAL = setOf("completed", "cancelled", "voided")
    }
}
