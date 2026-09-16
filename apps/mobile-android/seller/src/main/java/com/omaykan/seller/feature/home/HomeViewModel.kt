package com.omaykan.seller.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omaykan.seller.core.data.CatalogRepository
import com.omaykan.seller.core.data.ConversationRepository
import com.omaykan.seller.core.data.OrderFeed
import com.omaykan.seller.core.data.StoreProfileRepository
import com.omaykan.seller.core.model.Product
import com.omaykan.seller.core.model.Takings
import com.omaykan.seller.core.model.TopProduct
import com.omaykan.seller.core.model.salesChangeVsYesterday
import com.omaykan.seller.core.model.takingsFor
import com.omaykan.seller.core.model.topProducts
import com.omaykan.seller.core.network.ApiException
import com.omaykan.seller.core.notify.OrderWatchController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
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
    val feedError: String? = null,
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
) : ViewModel() {

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
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    init {
        viewModelScope.launch { catalog.ensureLoaded() }
    }

    fun setWatching(on: Boolean) {
        if (on) watcher.start() else watcher.stop()
    }
}
