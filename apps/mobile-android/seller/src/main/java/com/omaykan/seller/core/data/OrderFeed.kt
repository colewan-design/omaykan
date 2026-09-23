package com.omaykan.seller.core.data

import com.omaykan.seller.core.model.SellerOrder
import com.omaykan.seller.core.network.ApiException
import com.omaykan.seller.core.notify.NewOrderNotifier
import com.omaykan.seller.core.notify.NewOrderWatch
import com.omaykan.seller.core.realtime.OrderRealtime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * What the shop's orders look like right now.
 *
 * Not a snapshot the caller asked for — a live reading that keeps itself
 * current for as long as anybody is watching it.
 */
data class OrderFeedState(
    val orders: List<SellerOrder> = emptyList(),
    /** True until the first answer arrives, good or bad. */
    val loading: Boolean = true,
    /**
     * The last fetch that failed, if the one after it has not yet succeeded.
     *
     * The orders beside it are the last good ones. That pairing is the whole
     * point: a merchant standing at a counter reading a list must be told the
     * list has stopped updating, and must not have it taken away from them.
     */
    val error: String? = null,
)

/**
 * The one place this app asks the server what is happening.
 *
 * ## Live, with polling underneath
 *
 * The backend broadcasts every order event on the private `store.{id}` Reverb
 * channel, and since staff sign in as themselves this app holds a token that
 * channel accepts. While anything watches this feed, [OrderRealtime] keeps that
 * channel open and every event triggers a fetch at once.
 *
 * Polling stays underneath it: every minute while the socket is up, every
 * fifteen seconds while it is not — and always, on a build with no Reverb key.
 * A socket can die quietly, and a merchant must never be reading a list that
 * stopped updating without being told.
 *
 * ## Why a singleton, and why a flow
 *
 * Two things watch this: the orders screen, and the watcher service that keeps
 * it alive while the app is in the background. Both collect the same
 * [OrderFeedState], and `SharingStarted.WhileSubscribed` means one HTTP loop
 * runs when either or both are listening, and none runs when neither is.
 *
 * That structure is also what makes the new-order alert correct rather than
 * merely likely: arrivals are detected once, in the single upstream flow, so no
 * arrangement of screens and services can produce two notifications for one
 * order — or miss one because two watchers each assumed the other had it.
 */
@Singleton
class OrderFeed @Inject constructor(
    private val repository: SellerOrderRepository,
    private val notifier: NewOrderNotifier,
    private val realtime: OrderRealtime,
    private val stores: PairedStoreStore,
    @AppScope private val scope: CoroutineScope,
) {
    private companion object {
        const val POLL_INTERVAL_MS = 15_000L

        /** While the socket is up, polling is only the safety net. */
        const val CONNECTED_POLL_INTERVAL_MS = 60_000L

        /**
         * How long the loop keeps running after the last watcher leaves.
         *
         * Long enough to ride out a rotation or a trip into the recents list
         * without tearing down and re-fetching, short enough that a merchant
         * who really has put the phone away is not still being polled for.
         */
        const val LINGER_MS = 5_000L
    }

    private val watch = NewOrderWatch()

    /** Manual refreshes. Conflated: three impatient pulls are one fetch. */
    private val ticks = Channel<Unit>(Channel.CONFLATED)

    /**
     * The server's reply to a write, held until a fetch has caught up with it.
     *
     * A tap that changes an order has to show on the card at once, not at the
     * end of the polling interval, and the reply to that write is the most
     * current reading of the order that exists — more current than the poll
     * that may already be in flight. So it is laid over the fetched list until
     * a fetch that *started after the write* comes back, at which point the
     * server's own list is the better source and the override is dropped.
     *
     * Identity, not equality, decides that: an entry is cleared only if it is
     * still the same object that was there when the fetch began, so a second
     * tap landing mid-fetch is never quietly discarded.
     */
    private val overrides = MutableStateFlow<Map<String, SellerOrder>>(emptyMap())

    /** Drives the polling. Collected only through [state]. */
    private val polled: StateFlow<OrderFeedState> = flow {
        // The socket lives exactly as long as this loop: started when the first
        // watcher arrives, closed when the last one has gone.
        coroutineScope {
            stores.paired.first()?.id?.let { storeId ->
                launch { realtime.run(storeId) { ticks.trySend(Unit) } }
            }

            var last = OrderFeedState()

            while (true) {
                val inFlight = overrides.value

                last = try {
                    val fetched = repository.orders()

                    /*
                     * Alerts before the emission, so the sound and the list arrive
                     * together. `arrivals` returns nothing on the first fetch of a
                     * session — see NewOrderWatch — which is what stops opening the
                     * app from firing a notification for every order of the day.
                     */
                    notifier.notifyArrivals(watch.arrivals(fetched))

                    overrides.value = overrides.value.filterNot { (id, order) ->
                        inFlight[id] === order
                    }

                    OrderFeedState(orders = fetched, loading = false, error = null)
                } catch (e: ApiException) {
                    // The last good list is kept. See OrderFeedState.error.
                    last.copy(loading = false, error = e.message)
                }

                emit(last)

                // Either the interval elapses, somebody pulled to refresh, or the
                // socket said something happened.
                val interval = if (realtime.connected.value) CONNECTED_POLL_INTERVAL_MS else POLL_INTERVAL_MS
                withTimeoutOrNull(interval) { ticks.receive() }
            }
        }
    }.stateIn(
        scope = scope,
        started = SharingStarted.WhileSubscribed(stopTimeoutMillis = LINGER_MS),
        initialValue = OrderFeedState(),
    )

    val state: StateFlow<OrderFeedState> = combine(polled, overrides) { feed, pending ->
        if (pending.isEmpty()) feed else feed.copy(orders = feed.orders.map { pending[it.id] ?: it })
    }.stateIn(
        scope = scope,
        started = SharingStarted.WhileSubscribed(stopTimeoutMillis = LINGER_MS),
        initialValue = OrderFeedState(),
    )

    /** Fetch now rather than at the end of the interval. */
    fun refresh() {
        ticks.trySend(Unit)
    }

    /**
     * Put the server's reply to a write straight onto the screen.
     *
     * The order an action endpoint returns is the whole order, not the field
     * that changed, which matters: assigning a rider also moves the delivery
     * stage, and settling a payment also fixes the payment method. A screen
     * that patched only what it asked for would be showing a different order
     * from the one the server holds.
     */
    fun replace(order: SellerOrder) {
        overrides.value = overrides.value + (order.id to order)
        refresh()
    }

    /**
     * Forget what has been seen, so the next fetch is a fresh baseline.
     *
     * Called when the shop changes under the app. Without it, signing in at a
     * second branch would announce its entire order list as new arrivals.
     */
    fun reset() {
        watch.reset()
        overrides.value = emptyMap()
    }
}
