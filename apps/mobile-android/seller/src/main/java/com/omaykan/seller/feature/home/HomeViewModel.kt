package com.omaykan.seller.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omaykan.seller.core.data.CatalogRepository
import com.omaykan.seller.core.data.ConversationRepository
import com.omaykan.seller.core.data.OrderFeed
import com.omaykan.seller.core.data.OrderingRepository
import com.omaykan.seller.core.data.StoreProfileRepository
import com.omaykan.seller.core.model.OrderingState
import com.omaykan.seller.core.model.PauseLength
import com.omaykan.seller.core.model.Product
import com.omaykan.seller.core.model.Takings
import com.omaykan.seller.core.model.TopProduct
import com.omaykan.seller.core.model.salesChangeVsYesterday
import com.omaykan.seller.core.model.takingsFor
import com.omaykan.seller.core.model.topProducts
import com.omaykan.seller.core.network.ApiException
import com.omaykan.seller.core.notify.NewOrderNotifier
import com.omaykan.seller.core.notify.OrderWatchController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

private const val UNREAD_POLL_MS = 30_000L

data class HomeUiState(
    /** The signed-in person, for the greeting. Null until the catalog says. */
    val userName: String? = null,
    val takings: Takings = Takings(0, 0, 0, 0),
    /** Today against yesterday by this hour, or null when there is no fair comparison. */
    val salesChangePercent: Int? = null,
    val ordersLoading: Boolean = true,
    /** Orders still owed something — the badge on the Orders shortcut. */
    val liveCount: Int = 0,
    /** Null while the catalog loads, so a "0" never flashes before the real count. */
    val lowStockCount: Int? = null,
    val topProducts: List<TopProduct> = emptyList(),
    /** Catalog rows by id, so a top seller can wear its own photo. */
    val products: Map<String, Product> = emptyMap(),
    val unreadMessages: Int = 0,
    /** The shop's photo as last uploaded or read — versioned, so a new one shows at once. */
    val storePhotoUrl: String? = null,
    val watching: Boolean = false,
    /** Keep sounding until the notification is opened. */
    val insistent: Boolean = false,
    val feedError: String? = null,
    /** Whether the shop is taking online orders. Null until read, or when it cannot be. */
    val ordering: OrderingState? = null,
    val orderingBusy: Boolean = false,
    /** Why the last pause or reopen did not go through — a 403 for a role without Orders. */
    val orderingError: String? = null,
)

/**
 * The dashboard: today's figures, the shortcuts, the alert switch and what
 * sold this week.
 *
 * Every figure is read from something another tab already shows — the order
 * feed, the catalog, the inbox — so the home screen can never tell a merchant
 * one number and the tab behind it another.
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    feed: OrderFeed,
    private val catalog: CatalogRepository,
    private val conversations: ConversationRepository,
    private val watcher: OrderWatchController,
    profiles: StoreProfileRepository,
    private val ordering: OrderingRepository,
    private val notifier: NewOrderNotifier,
) : ViewModel() {

    private val orderingBusy = MutableStateFlow(false)
    private val orderingError = MutableStateFlow<String?>(null)

    /**
     * The inbox count, every thirty seconds while the screen is up. A failure
     * keeps the last count rather than dropping to zero — "no messages" is a
     * claim, and a dropped signal is not grounds for making it.
     */
    private val unread = flow {
        while (true) {
            try {
                emit(conversations.unread())
            } catch (_: ApiException) {
                // Keep what we had.
            }
            delay(UNREAD_POLL_MS)
        }
    }.onStart { emit(0) }

    val state: StateFlow<HomeUiState> =
        combine(
            feed.state,
            catalog.state,
            unread,
            watcher.watching,
            profiles.profile,
        ) { orders, loaded, messages, watching, profile ->
            HomeUiState(
                userName = loaded.catalog?.userName,
                takings = orders.orders.takingsFor(),
                salesChangePercent = orders.orders.salesChangeVsYesterday(),
                ordersLoading = orders.loading,
                liveCount = orders.orders.count { it.isOpen },
                lowStockCount = loaded.catalog?.lowStockCount,
                topProducts = orders.orders.topProducts(since = LocalDate.now().minusDays(6)),
                products = loaded.catalog?.products.orEmpty().associateBy { it.id },
                unreadMessages = messages,
                storePhotoUrl = profile?.photoUrl,
                watching = watching,
                feedError = orders.error,
            )
        }.let { base ->
            // A second combine: the typed overload stops at five flows.
            combine(base, ordering.state, orderingBusy, orderingError, notifier.insistent) { home, open, busy, error, insistent ->
                home.copy(ordering = open, orderingBusy = busy, orderingError = error, insistent = insistent)
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    init {
        viewModelScope.launch { catalog.ensureLoaded() }
        viewModelScope.launch {
            // Not fatal: the card simply does not show if this cannot be read.
            try { ordering.refresh() } catch (_: ApiException) {}
        }
    }

    fun pauseOrdering(length: PauseLength) = changeOrdering { ordering.pause(length) }

    fun reopenOrdering() = changeOrdering { ordering.reopen() }

    private fun changeOrdering(change: suspend () -> Unit) {
        if (orderingBusy.value) return
        viewModelScope.launch {
            orderingBusy.value = true
            orderingError.value = null
            try {
                change()
            } catch (e: ApiException) {
                orderingError.value = e.message
            } finally {
                orderingBusy.value = false
            }
        }
    }

    fun setInsistent(on: Boolean) = notifier.setInsistent(on)

    fun setWatching(on: Boolean) {
        if (on) watcher.start() else watcher.stop()
    }
}
