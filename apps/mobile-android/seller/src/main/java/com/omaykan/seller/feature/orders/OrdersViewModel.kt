package com.omaykan.seller.feature.orders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omaykan.seller.core.data.OrderFeed
import com.omaykan.seller.core.data.SellerOrderRepository
import com.omaykan.seller.core.data.SessionRepository
import com.omaykan.seller.core.model.DeliveryStage
import com.omaykan.seller.core.model.OrderStatus
import com.omaykan.seller.core.model.PaymentMethod
import com.omaykan.seller.core.model.RiderDirectory
import com.omaykan.seller.core.model.SellerOrder
import com.omaykan.seller.core.model.Takings
import com.omaykan.seller.core.model.takingsFor
import com.omaykan.seller.core.network.ApiException
import com.omaykan.seller.core.notify.OrderWatchController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The two lists a merchant asks for.
 *
 * Not four or five. [Live] is "what needs me now" and [All] is "what happened
 * today"; every finer cut — ready, on the road, unpaid — is visible on the card
 * itself, and a tab for each would be five taps to answer a question the list
 * already answers by being sorted.
 */
enum class OrderTab(val label: String) {
    Live("Live"),
    All("All"),
}

/** Which bottom sheet is open, if any. */
sealed interface OrderSheet {
    data object None : OrderSheet

    data class Rider(val orderId: String) : OrderSheet

    data class Settle(val orderId: String) : OrderSheet
}

data class OrdersUiState(
    val orders: List<SellerOrder> = emptyList(),
    val tab: OrderTab = OrderTab.Live,
    val takings: Takings = Takings(0, 0, 0, 0),
    val loading: Boolean = true,
    val watching: Boolean = false,
    /**
     * The last poll that failed, with the last good list still beside it.
     *
     * Shown as a line above the list rather than in place of it: a merchant
     * reading orders at a counter needs to know the list has stopped updating,
     * and must not have it taken away while they read.
     */
    val feedError: String? = null,
    /** The order with a write in flight. At most one — see [act]. */
    val busyOrderId: String? = null,
    /** The server's refusal of the last action, said once. */
    val actionError: String? = null,
    val sheet: OrderSheet = OrderSheet.None,
    /**
     * The shop's own riders, loaded when the assign sheet opens.
     *
     * Not part of the polled order feed. A saved rider's `online` flag is only
     * true for the ten seconds it describes, so it is read at the moment the
     * merchant is about to choose — offering a rider whose phone is off as the
     * one to send an order to is worse than a half-second of spinner.
     */
    val riders: RiderDirectory = RiderDirectory(),
    val ridersLoading: Boolean = false,
) {
    val visible: List<SellerOrder>
        get() = when (tab) {
            OrderTab.Live -> orders.filter { it.isOpen }
            OrderTab.All -> orders
        }

    /** The badge on the Live tab: what is actually outstanding. */
    val liveCount: Int get() = orders.count { it.isOpen }

    /** The order a sheet is about, read fresh out of the list every time. */
    fun sheetOrder(): SellerOrder? = when (val current = sheet) {
        is OrderSheet.Rider -> orders.firstOrNull { it.id == current.orderId }
        is OrderSheet.Settle -> orders.firstOrNull { it.id == current.orderId }
        OrderSheet.None -> null
    }
}

/**
 * The orders screen.
 *
 * Everything about *when* to fetch lives in [OrderFeed] — this view model
 * collects it and does the two things a screen has to: decide what is shown,
 * and send the four writes. It holds no copy of the list, so the screen and the
 * watcher service can never disagree about what the shop has.
 */
@HiltViewModel
class OrdersViewModel @Inject constructor(
    private val feed: OrderFeed,
    private val repository: SellerOrderRepository,
    private val sessions: SessionRepository,
    private val watcher: OrderWatchController,
) : ViewModel() {

    /** What the screen owns: a tab, a sheet, and whichever write is in flight. */
    private val local = MutableStateFlow(LocalState())

    private data class LocalState(
        val tab: OrderTab = OrderTab.Live,
        val busyOrderId: String? = null,
        val actionError: String? = null,
        val sheet: OrderSheet = OrderSheet.None,
        val riders: RiderDirectory = RiderDirectory(),
        val ridersLoading: Boolean = false,
    )

    val state: StateFlow<OrdersUiState> = combine(
        feed.state,
        local,
        watcher.watching,
    ) { feedState, screen, watching ->
        OrdersUiState(
            orders = feedState.orders,
            tab = screen.tab,
            // Recomputed on every emission rather than cached. It is a fold
            // over at most a hundred orders, and the alternative is a figure
            // that can disagree with the list underneath it.
            takings = feedState.orders.takingsFor(),
            loading = feedState.loading,
            watching = watching,
            feedError = feedState.error,
            busyOrderId = screen.busyOrderId,
            actionError = screen.actionError,
            sheet = screen.sheet,
            riders = screen.riders,
            ridersLoading = screen.ridersLoading,
        )
    }.stateIn(
        scope = viewModelScope,
        // WhileSubscribed, and this is what starts and stops the polling: the
        // feed runs while this flow is collected. Five seconds of grace so a
        // rotation does not tear the loop down and immediately rebuild it.
        started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
        initialValue = OrdersUiState(),
    )

    fun onTab(tab: OrderTab) = local.update { it.copy(tab = tab) }

    fun refresh() = feed.refresh()

    fun openRiderSheet(orderId: String) {
        local.update { it.copy(sheet = OrderSheet.Rider(orderId), actionError = null) }
        // Read at the moment the merchant is about to choose, so `online` is
        // true for the ten seconds it actually describes.
        loadRiders()
    }

    fun openSettleSheet(orderId: String) = local.update {
        it.copy(sheet = OrderSheet.Settle(orderId), actionError = null)
    }

    fun dismissSheet() = local.update { it.copy(sheet = OrderSheet.None) }

    fun dismissActionError() = local.update { it.copy(actionError = null) }

    fun setWatching(on: Boolean) {
        if (on) watcher.start() else watcher.stop()
    }

    fun signOut() {
        viewModelScope.launch {
            // Ordered: the watch first, so the service is on its way down
            // before the token it was using disappears underneath it.
            watcher.stop()
            sessions.signOut()
            // The shop is changing under the app. Without this the next
            // sign-in would read its whole order list as new arrivals and
            // announce every one of them.
            feed.reset()
        }
    }

    // -- Writes --------------------------------------------------------------

    fun advanceStatus(order: SellerOrder) {
        val next = order.status.next ?: return
        act(order.id) { repository.advanceStatus(order.id, next) }
    }

    fun advanceDelivery(order: SellerOrder) {
        val next = order.deliveryStage?.next ?: return
        act(order.id) { repository.advanceDelivery(order.id, next) }
    }

    fun assignRider(orderId: String, name: String, phone: String?, save: Boolean = false) {
        act(orderId, closeSheet = true) { repository.assignRider(orderId, name, phone, save) }
    }

    /**
     * Hand the order to somebody already on the shop's list.
     *
     * One tap, which is the entire reason for saving a rider. Assigning is also
     * what ranks them: the picker is ordered by use, so the rider a shop sends
     * out twice today is the first one they see tonight without anybody having
     * ranked anything by hand.
     */
    fun assignSavedRider(orderId: String, savedRiderId: String) {
        act(orderId, closeSheet = true) { repository.assignSavedRider(orderId, savedRiderId) }
    }

    /**
     * Take the rider off and put the order back on the platform board.
     *
     * The undo for an assignment, and the thing a shop needs when they gave the
     * job to their nephew and then found out he is asleep. Refused by the
     * server once the food has been picked up, because at that point handing it
     * back is a phone call between two people.
     */
    fun returnToBoard(orderId: String) {
        act(orderId, closeSheet = true) { repository.unassignRider(orderId) }
    }

    /** Read the shop's riders. Called when the assign sheet opens. */
    fun loadRiders() {
        local.update { it.copy(ridersLoading = true) }
        viewModelScope.launch {
            val directory = runCatching { repository.riders() }.getOrNull()
            local.update {
                // A failure leaves the list empty rather than raising an error:
                // the typed-in form below the picker is the whole feature for a
                // shop that has never saved anybody, and it still works.
                it.copy(riders = directory ?: RiderDirectory(), ridersLoading = false)
            }
        }
    }

    /** Keep a rider who has delivered here before, in one tap. */
    fun keepRider(riderId: String, name: String, phone: String?) {
        viewModelScope.launch {
            runCatching { repository.saveRider(name = name, phone = phone, riderId = riderId) }
            loadRiders()
        }
    }

    /** Forget one. Orders they already carried keep the name recorded on them. */
    fun forgetRider(savedRiderId: String) {
        viewModelScope.launch {
            runCatching { repository.deleteSavedRider(savedRiderId) }
            loadRiders()
        }
    }

    fun settle(orderId: String, method: PaymentMethod) {
        act(orderId, closeSheet = true) { repository.settle(orderId, method) }
    }

    /**
     * One write, and only one at a time on a given order.
     *
     * The guard is not decoration. Two of these — settling a payment, and
     * advancing a status — are things a merchant will double-tap on a slow
     * connection, and a second settle-payment is a duplicate cash row against a
     * drawer somebody has to reconcile at the end of the shift. The server
     * refuses that with a 422 as its last line of defence; this is the first
     * one, and it is the one that keeps the refusal from ever being needed.
     *
     * The buttons are also disabled while [OrdersUiState.busyOrderId] holds the
     * order, so the guard is a backstop rather than the only thing standing
     * between a fat thumb and a wrong till.
     */
    private fun act(
        orderId: String,
        closeSheet: Boolean = false,
        block: suspend () -> SellerOrder,
    ) {
        if (local.value.busyOrderId != null) return

        local.update { it.copy(busyOrderId = orderId, actionError = null) }

        viewModelScope.launch {
            try {
                // Straight into the feed, so the card is right immediately
                // rather than at the end of the polling interval.
                feed.replace(block())
                if (closeSheet) local.update { it.copy(sheet = OrderSheet.None) }
            } catch (e: ApiException) {
                /*
                 * The server's own sentence, kept whole. "That order has
                 * already been settled." tells a merchant exactly what
                 * happened; a generic failure would leave them tapping again.
                 *
                 * The sheet stays open on an error, so the refusal appears
                 * where the decision was made rather than on a screen behind it.
                 */
                local.update { it.copy(actionError = e.message) }
            } finally {
                local.update { it.copy(busyOrderId = null) }
            }
        }
    }
}
