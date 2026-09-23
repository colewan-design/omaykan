package com.omaykan.storefront.feature.orders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omaykan.storefront.core.data.AccountRepository
import com.omaykan.storefront.core.data.CatalogRepository
import com.omaykan.storefront.core.data.CheckoutPrefsStore
import com.omaykan.storefront.core.data.OrderRepository
import com.omaykan.storefront.core.model.OrderStage
import com.omaykan.storefront.core.model.SessionState
import com.omaykan.storefront.core.model.TrackedOrder
import com.omaykan.storefront.core.network.ApiException
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The chips above the list.
 *
 * The four stage chips are the same buckets the account page counts — one
 * vocabulary, so tapping "On the way" there lands on exactly the orders it
 * said were on the way. [Active] is kept beside them because "anything I am
 * still waiting on" is a question none of the individual stages answers.
 *
 * A cancelled order is in no stage, so it appears under [All] alone. That is
 * deliberate: it is not on the way to anything, and burying it in Completed
 * would say it finished.
 */
enum class OrderFilter(val label: String, val stage: OrderStage?) {
    All("All", null),
    Active("Active", null),
    Preparing("Preparing", OrderStage.Preparing),
    Ready("Ready", OrderStage.Ready),
    OnTheWay("On the way", OrderStage.OnTheWay),
    Completed("Completed", OrderStage.Completed),
    ;

    /**
     * Whether the tab carries a count.
     *
     * Only the tabs about something still owed to the shopper. A number beside
     * All restates the length of the list under it, and one beside Completed
     * grows forever — a badge that is always lit is furniture, not a signal.
     */
    val counted: Boolean get() = this != All && this != Completed
}

data class OrdersUiState(
    val orders: List<TrackedOrder> = emptyList(),
    val filter: OrderFilter = OrderFilter.All,
    /** What the shopper typed above the tabs. Matched against items and ticket. */
    val query: String = "",
    /**
     * Item photos by product id, for whatever this device still has cached.
     * Absent ids draw a placeholder; see CatalogRepository.photosFor.
     */
    val photos: Map<String, String> = emptyMap(),
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val error: String? = null,
    val signedIn: Boolean = false,
    /** True when the list is only what this device placed, with no account behind it. */
    val guestOnly: Boolean = false,
) {
    val visible: List<TrackedOrder> get() = inBucket(filter).filter { it.matches(query) }

    /**
     * What a tab's badge says.
     *
     * The search narrows the counts too, so a tab never advertises orders that
     * tapping it would not show. A "2" that opens onto an empty list is worse
     * than no number at all.
     */
    fun countOf(filter: OrderFilter): Int = inBucket(filter).count { it.matches(query) }

    private fun inBucket(filter: OrderFilter): List<TrackedOrder> = when (filter) {
        OrderFilter.All -> orders
        OrderFilter.Active -> orders.filter {
            it.stage != null && it.stage != OrderStage.Completed
        }
        else -> orders.filter { it.stage == filter.stage }
    }

    companion object {
        val TERMINAL = setOf("completed", "cancelled", "voided")
    }
}

/**
 * Search, over the two things a shopper would type: what they bought, and the
 * ticket they were given. Nothing about the seller — an order does not carry
 * one, and a field that silently matches nothing is a field that lies.
 */
private fun TrackedOrder.matches(query: String): Boolean {
    val needle = query.trim()
    if (needle.isEmpty()) return true
    return ticketNumber?.contains(needle, ignoreCase = true) == true ||
        items.any { it.name.contains(needle, ignoreCase = true) }
}

/**
 * Orders.
 *
 * Two sources, deliberately both:
 *
 *  - Signed in, the server answers — `GET /api/customer/orders` follows the
 *    person to a new phone.
 *  - Signed out, this device's own record answers. A guest order carries no
 *    account id and is findable only by its unguessable id, so the app keeps
 *    those ids (CheckoutPrefsStore) and fetches each one. Without that, a
 *    shopper who did not sign in loses sight of an order the moment they leave
 *    the confirmation screen.
 *
 * When both exist they are merged, because signing in does not retroactively
 * claim the orders placed before it — the server will not match them up by
 * email, for good reason (see CustomerOrderController), so the only place they
 * meet is here.
 */
@HiltViewModel
class OrdersViewModel @Inject constructor(
    private val orderRepository: OrderRepository,
    private val accountRepository: AccountRepository,
    private val catalogRepository: CatalogRepository,
    private val prefs: CheckoutPrefsStore,
) : ViewModel() {

    private val _state = MutableStateFlow(OrdersUiState())
    val state: StateFlow<OrdersUiState> = _state.asStateFlow()

    init {
        // Re-runs when the session changes, so signing in or out reloads the
        // list rather than leaving the previous shopper's orders on screen.
        viewModelScope.launch {
            combine(
                accountRepository.session,
                prefs.recentOrderIds,
            ) { session, ids -> session to ids }
                .collect { (session, ids) ->
                    if (session is SessionState.Restoring) return@collect
                    load(signedIn = session is SessionState.SignedIn, deviceIds = ids)
                }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            val session = accountRepository.session.first()
            load(
                signedIn = session is SessionState.SignedIn,
                deviceIds = prefs.recentOrderIds.first(),
                refreshing = true,
            )
        }
    }

    fun onFilter(filter: OrderFilter) = _state.update { it.copy(filter = filter) }

    fun onQuery(query: String) = _state.update { it.copy(query = query) }

    private suspend fun load(
        signedIn: Boolean,
        deviceIds: List<String>,
        refreshing: Boolean = false,
    ) {
        _state.update {
            it.copy(
                loading = it.orders.isEmpty() && !refreshing,
                refreshing = refreshing,
                error = null,
                signedIn = signedIn,
            )
        }

        try {
            // The merge lives in the repository because the account page counts
            // the same list — two copies of "which orders are mine" would drift
            // the moment one of them learned about a new source.
            val merged = orderRepository.mine(signedIn, deviceIds)

            // Best effort, and off the local cache only: a picture is worth
            // having where the device already has one, and is never worth
            // holding up a list somebody is waiting to read.
            val photos = catalogRepository.photosFor(
                merged.flatMap { order -> order.items.map { it.productId } },
            )

            _state.update {
                it.copy(
                    orders = merged,
                    photos = photos,
                    loading = false,
                    refreshing = false,
                    error = null,
                    signedIn = signedIn,
                    guestOnly = !signedIn && merged.isNotEmpty(),
                )
            }
        } catch (e: ApiException) {
            // A failed refresh must not blank a list already on screen: someone
            // may be standing at a door reading it.
            _state.update {
                it.copy(
                    loading = false,
                    refreshing = false,
                    error = if (it.orders.isEmpty()) e.message else null,
                )
            }
        }
    }
}
