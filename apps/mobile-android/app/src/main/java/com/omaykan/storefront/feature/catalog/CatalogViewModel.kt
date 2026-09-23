package com.omaykan.storefront.feature.catalog

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.omaykan.storefront.core.data.CatalogRepository
import com.omaykan.storefront.core.data.PairedShopRecord
import com.omaykan.storefront.core.data.CartRepository
import com.omaykan.storefront.core.data.PairedStoreStore
import com.omaykan.storefront.core.data.SavedProductsStore
import com.omaykan.storefront.core.designsystem.SnackbarMessages
import com.omaykan.storefront.core.model.BusinessMode
import com.omaykan.storefront.core.model.Catalog
import com.omaykan.storefront.core.model.Category
import com.omaykan.storefront.core.model.Product
import com.omaykan.storefront.core.model.StoreRef
import com.omaykan.storefront.core.network.ApiException
import com.omaykan.storefront.navigation.CatalogRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CatalogUiState(
    val shopName: String = "",
    val ownerName: String? = null,
    val shopAddress: String? = null,
    val categories: List<Category> = emptyList(),
    val products: List<Product> = emptyList(),
    val selectedCategoryId: String? = null,
    val query: String = "",
    /** Nothing cached and nothing fetched yet — the only true blank state. */
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    /** A refresh failed, but there is a cached shelf worth showing anyway. */
    val staleMessage: String? = null,
    /** A refresh failed and there is nothing to fall back on. */
    val error: String? = null,
    /** The server said this shop has nothing on the shelf. Not an error. */
    val emptyShelf: Boolean = false,
    val fetchedAtEpochMs: Long? = null,
    val savedIds: Set<String> = emptySet(),
) {
    val filtered: Boolean get() = selectedCategoryId != null || query.isNotBlank()
}

private data class Filters(
    val categoryId: String? = null,
    val query: String = "",
)

private data class RefreshState(
    val refreshing: Boolean = false,
    val failure: ApiException? = null,
)

@HiltViewModel
class CatalogViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: CatalogRepository,
    private val cartRepository: CartRepository,
    private val pairedStoreStore: PairedStoreStore,
    private val savedProductsStore: SavedProductsStore,
) : ViewModel() {

    private val route = savedStateHandle.toRoute<CatalogRoute>()
    private val ref = StoreRef(route.orgSlug, route.storeCode)

    // Opened as an aisle when the front page sent us here from a category chip.
    private val filters = MutableStateFlow(Filters(categoryId = route.categoryId))
    private val refreshState = MutableStateFlow(RefreshState())

    /**
     * This shop's basket, for the badge on the cart button in the top bar.
     *
     * This shop's and not the whole device's: the button opens this shop's
     * cart, and baskets do not mix across shops. A badge counting items the
     * screen it opens does not contain would be a lie in two directions.
     */
    val cartCount: StateFlow<Int> = cartRepository.lineCount(ref).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = 0,
    )

    val state: StateFlow<CatalogUiState> = combine(
        repository.cached(ref),
        filters,
        refreshState,
        savedProductsStore.saved,
    ) { catalog, filter, refresh, saved ->
        buildState(catalog, filter, refresh, saved)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = CatalogUiState(),
    )

    /** The heart is the one silent tap on this shelf, so it says so. */
    val snackbar = SnackbarMessages()

    init {
        refresh()
    }

    private fun buildState(
        catalog: Catalog?,
        filter: Filters,
        refresh: RefreshState,
        saved: Set<String>,
    ): CatalogUiState {
        if (catalog == null) {
            return CatalogUiState(
                savedIds = saved,
                query = filter.query,
                loading = refresh.refreshing || refresh.failure == null,
                refreshing = refresh.refreshing,
                error = refresh.failure?.message,
            )
        }

        val term = filter.query.trim()
        val products = catalog.products.filter { product ->
            val inCategory = filter.categoryId == null || product.categoryId == filter.categoryId
            val matches = term.isEmpty() ||
                product.name.contains(term, ignoreCase = true) ||
                product.sku.equals(term, ignoreCase = true) ||
                product.barcode == term
            inCategory && matches
        }

        return CatalogUiState(
            shopName = catalog.shop.name,
            ownerName = catalog.shop.ownerName,
            shopAddress = catalog.shop.address,
            categories = catalog.categories,
            products = products,
            selectedCategoryId = filter.categoryId,
            query = filter.query,
            loading = false,
            refreshing = refresh.refreshing,
            // A cached shelf plus a failed refresh is worth showing, once, in a
            // strip. It is never worth showing in place of a live empty answer:
            // the cache is only ever consulted when the network did not answer
            // at all, because a successful fetch overwrites it first.
            staleMessage = refresh.failure?.let { failure ->
                when (failure) {
                    is ApiException.Offline -> "Showing saved prices — you are offline."
                    else -> "Showing saved prices — ${failure.message}"
                }
            },
            emptyShelf = catalog.products.isEmpty(),
            fetchedAtEpochMs = catalog.fetchedAtEpochMs,
            savedIds = saved,
        )
    }

    fun refresh() {
        if (refreshState.value.refreshing) return
        refreshState.update { it.copy(refreshing = true) }

        viewModelScope.launch {
            try {
                repository.refresh(ref)
                refreshState.value = RefreshState(refreshing = false, failure = null)
                rememberShop()
            } catch (e: ApiException) {
                refreshState.value = RefreshState(refreshing = false, failure = e)
            }
        }
    }

    /**
     * Remember this shop so the next launch reopens it.
     *
     * Written only after a successful fetch, and using the name the catalog
     * itself returned: a shop remembered from a call that failed would put a
     * name on the launch screen for a shelf nobody has ever seen.
     */
    private suspend fun rememberShop() {
        val catalog = repository.cached(ref).first() ?: return
        pairedStoreStore.pair(
            PairedShopRecord(
                ref = ref,
                name = catalog.shop.name,
                businessMode = BusinessMode.Unknown,
            ),
        )
    }

    fun onToggleSaved(productId: String) {
        viewModelScope.launch {
            val saved = savedProductsStore.toggle(savedKey(productId))
            snackbar.send(if (saved) "Saved" else "Removed from saved")
        }
    }

    /** A product id is only unique inside its own shop's catalog. */
    fun savedKey(productId: String): String = ref.key + "/" + productId

    fun onQueryChange(value: String) = filters.update { it.copy(query = value) }

    fun onCategorySelected(categoryId: String?) = filters.update { it.copy(categoryId = categoryId) }
}
