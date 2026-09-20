package com.omaykan.storefront.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omaykan.storefront.core.data.CartRepository
import com.omaykan.storefront.core.data.CatalogRepository
import com.omaykan.storefront.core.data.DefaultStore
import com.omaykan.storefront.core.data.SavedProductsStore
import com.omaykan.storefront.core.designsystem.SnackbarMessages
import com.omaykan.storefront.core.data.ShopDirectoryRepository
import com.omaykan.storefront.core.model.Catalog
import com.omaykan.storefront.core.model.Product
import com.omaykan.storefront.core.model.ShopSummary
import com.omaykan.storefront.core.model.StoreRef
import com.omaykan.storefront.core.network.ApiException
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The front page, mirroring apps/web/src/landing/LandingPage.vue.
 *
 * Same shelves, in the same order, off the same catalog: Popular now, then the
 * shops' own markdowns, then the vendors block, then everything under ₱100 —
 * with the shop directory underneath. The merchant strip and the seller sign-in
 * that sit at the bottom of the web page are deliberately absent: this app is
 * for the person buying, and a "list your shop" call to action in a shopping
 * app is a door into a room they did not come here for.
 */
/**
 * One aisle as the front page and the Shop tab draw it.
 *
 * The picture is a real one off the aisle's own shelf — the first product in
 * it that has a photo — rather than stock art of what such an aisle might
 * hold, so the circle for "Coffee" shows this shop's coffee.
 */
data class Aisle(
    val id: String,
    val name: String,
    /** Null when nothing in the aisle has been photographed. */
    val imageUrl: String?,
    val itemCount: Int,
    val fromCents: Long,
)

data class HomeUiState(
    val shopName: String = "",
    val query: String = "",
    val aisles: List<Aisle> = emptyList(),
    /** Everything on the shelf, for the Saved tab to resolve hearts against. */
    val allProducts: List<Product> = emptyList(),
    val popular: List<Product> = emptyList(),
    val deals: List<Product> = emptyList(),
    val cheapest: List<Product> = emptyList(),
    /** Products matching the current search, across every shelf. Null when not searching. */
    val matchCount: Int? = null,
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val staleMessage: String? = null,
    val error: String? = null,
    val emptyShelf: Boolean = false,
    val shops: List<ShopSummary> = emptyList(),
    val shopsLoading: Boolean = true,
    val shopsError: String? = null,
    /** Fully-qualified ids of hearted products - see SavedProductsStore. */
    val savedIds: Set<String> = emptySet(),
) {
    val searching: Boolean get() = query.isNotBlank()
}

private data class ShopsState(
    val shops: List<ShopSummary> = emptyList(),
    val loading: Boolean = true,
    val error: String? = null,
)

private data class RefreshState(
    val refreshing: Boolean = false,
    val failure: ApiException? = null,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val catalogRepository: CatalogRepository,
    cartRepository: CartRepository,
    private val shopDirectoryRepository: ShopDirectoryRepository,
    private val savedProductsStore: SavedProductsStore,
    @DefaultStore val store: StoreRef,
) : ViewModel() {

    /** Lines held across every shop — the badge on the cart button. */
    val cartCount: StateFlow<Int> = cartRepository.totalLineCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    private val query = MutableStateFlow("")
    private val refreshState = MutableStateFlow(RefreshState())
    private val shopsState = MutableStateFlow(ShopsState())

    val state: StateFlow<HomeUiState> = combine(
        catalogRepository.cached(store),
        query,
        refreshState,
        shopsState,
        savedProductsStore.saved,
    ) { catalog, term, refresh, shops, saved ->
        buildState(catalog, term, refresh, shops, saved)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HomeUiState(),
    )

    init {
        refresh()
        loadShops()
    }

    private fun buildState(
        catalog: Catalog?,
        term: String,
        refresh: RefreshState,
        shops: ShopsState,
        saved: Set<String>,
    ): HomeUiState {
        val base = HomeUiState(
            query = term,
            refreshing = refresh.refreshing,
            shops = shops.shops,
            shopsLoading = shops.loading,
            shopsError = shops.error,
            savedIds = saved,
        )

        if (catalog == null) {
            return base.copy(
                loading = refresh.refreshing || refresh.failure == null,
                error = refresh.failure?.message,
            )
        }

        val needle = term.trim()
        val visible = if (needle.isEmpty()) {
            catalog.products
        } else {
            catalog.products.filter { it.name.contains(needle, ignoreCase = true) }
        }

        return base.copy(
            shopName = catalog.shop.name,
            // An aisle with nothing in it is left out rather than drawn: a
            // circle that opens onto "nothing matches" is a door to an empty
            // room, and the count on the Shop tab would have to say zero.
            aisles = catalog.categories.mapNotNull { category ->
                val shelf = catalog.products.filter { it.categoryId == category.id }
                if (shelf.isEmpty()) return@mapNotNull null
                Aisle(
                    id = category.id,
                    name = category.name,
                    imageUrl = shelf.firstOrNull { !it.imageUrl.isNullOrBlank() }?.imageUrl,
                    itemCount = shelf.size,
                    fromCents = shelf.minOf { it.priceCents },
                )
            },
            allProducts = catalog.products,
            popular = visible.take(SHELF_SIZE),
            deals = visible.filter { it.discountPercent != null },
            // The shelf is titled "under ₱100", so it filters on that rather
            // than taking the twelve cheapest — a shop whose cheapest line is
            // ₱120 would otherwise have the row advertise it as under ₱100.
            cheapest = visible
                .filter { it.priceCents < CHEAP_MAX_CENTS }
                .sortedBy { it.priceCents }
                .take(SHELF_SIZE),
            matchCount = if (needle.isEmpty()) null else visible.size,
            loading = false,
            staleMessage = refresh.failure?.let { failure ->
                when (failure) {
                    is ApiException.Offline -> "Showing saved prices — you are offline."
                    else -> "Showing saved prices — ${failure.message}"
                }
            },
            emptyShelf = catalog.products.isEmpty(),
        )
    }

    fun refresh() {
        if (refreshState.value.refreshing) return
        refreshState.update { it.copy(refreshing = true) }

        viewModelScope.launch {
            try {
                catalogRepository.refresh(store)
                refreshState.value = RefreshState()
            } catch (e: ApiException) {
                refreshState.value = RefreshState(failure = e)
            }
        }
        loadShops()
    }

    private fun loadShops() {
        viewModelScope.launch {
            shopsState.update { it.copy(loading = true, error = null) }
            try {
                shopsState.value = ShopsState(
                    shops = shopDirectoryRepository.shops(),
                    loading = false,
                )
            } catch (e: ApiException) {
                // The shelves above are the point of this screen; a directory
                // that would not load says so in its own section rather than
                // taking the page down with it.
                shopsState.update { it.copy(loading = false, error = e.message) }
            }
        }
    }

    fun onQueryChange(value: String) {
        query.value = value
    }

    /**
     * Hearts live on three tabs behind one view model, so one stream of
     * messages serves all of them — the shell hosts it once.
     */
    val snackbar = SnackbarMessages()

    fun onToggleSaved(productId: String) {
        viewModelScope.launch {
            val saved = savedProductsStore.toggle(savedKey(productId))
            snackbar.send(if (saved) "Saved" else "Removed from saved")
        }
    }

    /** A product id is only unique inside its own shop's catalog. */
    fun savedKey(productId: String): String = store.key + "/" + productId

    private companion object {
        const val SHELF_SIZE = 12
        const val CHEAP_MAX_CENTS = 10_000L
    }
}
