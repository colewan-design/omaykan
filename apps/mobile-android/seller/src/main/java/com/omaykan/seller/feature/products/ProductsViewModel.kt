package com.omaykan.seller.feature.products

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omaykan.seller.core.data.CatalogRepository
import com.omaykan.seller.core.model.Catalog
import com.omaykan.seller.core.model.Product
import com.omaykan.seller.core.model.ProductDraft
import com.omaykan.seller.core.network.ApiException
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
 * The reference's "All / Active / Inactive", plus the one cut the home
 * screen's "Low stock" figure opens onto. The first three are the chips;
 * Low stock is only ever arrived at from Home, and shows as a banner that
 * can be cleared rather than a fourth chip that would not fit the row.
 */
enum class ProductFilter(val label: String) {
    All("All Products"),
    Active("Active"),
    Inactive("Inactive"),
    LowStock("Low stock"),
    ;

    fun includes(product: Product): Boolean = when (this) {
        All -> true
        Active -> product.active
        Inactive -> !product.active
        // Active only, like the home figure: a product the shop has switched
        // off is not one it is about to run out of.
        LowStock -> product.active && product.lowStock
    }
}

data class ProductsUiState(
    val catalog: Catalog? = null,
    val loading: Boolean = true,
    val error: String? = null,
    val query: String = "",
    /** Null for "All". */
    val categoryId: String? = null,
    val filter: ProductFilter = ProductFilter.All,
    /** The product whose active switch is being saved from the list. */
    val busyProductId: String? = null,
    /** The server's refusal of that save, in its own words. */
    val actionError: String? = null,
) {
    val canEdit: Boolean get() = catalog?.role?.managesCatalog == true

    /** Search and category applied; the filter chips count within this. */
    private val scoped: List<Product>
        get() {
            val needle = query.trim()
            return catalog?.products.orEmpty().filter { product ->
                (categoryId == null || product.categoryId == categoryId) &&
                    (
                        needle.isEmpty() ||
                            product.name.contains(needle, ignoreCase = true) ||
                            product.sku?.contains(needle, ignoreCase = true) == true
                        )
            }
        }

    val visible: List<Product>
        get() = scoped.filter(filter::includes).sortedBy { it.name.lowercase() }

    fun count(filter: ProductFilter): Int = scoped.count(filter::includes)
}

@HiltViewModel
class ProductsViewModel @Inject constructor(
    private val catalog: CatalogRepository,
) : ViewModel() {

    private data class Local(
        val query: String = "",
        val categoryId: String? = null,
        val filter: ProductFilter = ProductFilter.All,
        val busyProductId: String? = null,
        val actionError: String? = null,
    )

    private val local = MutableStateFlow(Local())

    val state: StateFlow<ProductsUiState> = combine(catalog.state, local) { loaded, screen ->
        ProductsUiState(
            catalog = loaded.catalog,
            // Nothing loaded and nothing failed is still loading — the first
            // frame, before ensureLoaded has had a chance to start.
            loading = loaded.loading || (loaded.catalog == null && loaded.error == null),
            error = loaded.error,
            query = screen.query,
            categoryId = screen.categoryId,
            filter = screen.filter,
            busyProductId = screen.busyProductId,
            actionError = screen.actionError,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProductsUiState())

    init {
        viewModelScope.launch { catalog.ensureLoaded() }
    }

    fun refresh() {
        viewModelScope.launch { catalog.refresh() }
    }

    fun onQuery(query: String) = local.update { it.copy(query = query) }

    /** Tapping the chosen category again goes back to all of them. */
    fun onCategory(id: String?) = local.update {
        it.copy(categoryId = if (it.categoryId == id) null else id)
    }

    fun onFilter(filter: ProductFilter) = local.update { it.copy(filter = filter) }

    fun dismissActionError() = local.update { it.copy(actionError = null) }

    /**
     * Switch a product on or off for customers, from its row's menu.
     *
     * The same save the form makes — every field sent back as it is, so
     * nothing but the switch moves — and the typed stock is the stock it
     * already has, so no count correction is recorded. One at a time, like
     * the order writes: a second tap while the first is in flight does
     * nothing.
     */
    fun setActive(product: Product, active: Boolean) {
        if (!state.value.canEdit || local.value.busyProductId != null) return

        local.update { it.copy(busyProductId = product.id, actionError = null) }

        viewModelScope.launch {
            try {
                catalog.save(
                    ProductDraft(
                        productId = product.id,
                        name = product.name,
                        categoryId = product.categoryId,
                        priceCents = product.priceCents,
                        stockQty = product.stockQty,
                        lowStockThreshold = product.lowStockThreshold,
                        active = active,
                    ),
                )
            } catch (e: ApiException) {
                local.update { it.copy(actionError = e.message) }
            } finally {
                local.update { it.copy(busyProductId = null) }
            }
        }
    }
}
