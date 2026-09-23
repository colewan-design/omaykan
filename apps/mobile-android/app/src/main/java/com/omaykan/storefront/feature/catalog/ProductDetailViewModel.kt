package com.omaykan.storefront.feature.catalog

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.omaykan.storefront.core.data.CartRepository
import com.omaykan.storefront.core.data.CatalogRepository
import com.omaykan.storefront.core.data.SavedProductsStore
import com.omaykan.storefront.core.designsystem.SnackbarMessages
import com.omaykan.storefront.core.model.Product
import com.omaykan.storefront.core.model.Shop
import com.omaykan.storefront.core.model.StoreRef
import com.omaykan.storefront.navigation.ProductRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProductDetailUiState(
    val product: Product? = null,
    /** Who is selling it. Null only before this shop's catalog has been cached. */
    val shop: Shop? = null,
    val loading: Boolean = true,
    val saved: Boolean = false,
    /** How many this screen will add. Reset to 1 each time it is opened. */
    val quantity: Double = 1.0,
    /** How many of this product the basket already holds. */
    val inCart: Double = 0.0,
)

/**
 * Reads straight from the cache the shelf filled.
 *
 * There is no per-product endpoint — the catalog is the only read — so opening
 * a product must never trigger its own fetch. Backing out to the shelf and
 * refreshing is what updates it, and that is the honest shape: the price on
 * this screen is exactly the price on the grid behind it.
 */
@HiltViewModel
class ProductDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    repository: CatalogRepository,
    private val savedProductsStore: SavedProductsStore,
    private val cartRepository: CartRepository,
) : ViewModel() {

    private val route = savedStateHandle.toRoute<ProductRoute>()
    private val ref = StoreRef(route.orgSlug, route.storeCode)
    private val savedKey = "${ref.key}/${route.productId}"

    private val quantity = MutableStateFlow(1.0)

    /** What the two silent taps on this screen — Add to Cart, the heart — say back. */
    val snackbar = SnackbarMessages()

    val state: StateFlow<ProductDetailUiState> = combine(
        repository.cachedProduct(ref, route.productId),
        repository.cachedShop(ref),
        savedProductsStore.saved,
        quantity,
        cartRepository.cart(ref),
    ) { product, shop, saved, qty, cart ->
        ProductDetailUiState(
            product = product,
            shop = shop,
            loading = false,
            saved = savedKey in saved,
            quantity = qty,
            inCart = cart.lines.firstOrNull { it.product.id == route.productId }?.quantity ?: 0.0,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ProductDetailUiState(),
    )

    fun toggleSaved() {
        viewModelScope.launch {
            val saved = savedProductsStore.toggle(savedKey)
            snackbar.send(if (saved) "Saved" else "Removed from saved")
        }
    }

    fun setQuantity(value: Double) {
        quantity.value = value.coerceAtLeast(1.0)
    }

    /**
     * Adds the chosen quantity on top of whatever the basket already holds,
     * then says so.
     *
     * The basket is local and the write cannot fail, so the only thing missing
     * was the acknowledgement: without it the screen does not visibly move, and
     * a shopper who is not sure it worked taps again and orders two.
     */
    fun addToCart() {
        viewModelScope.launch {
            cartRepository.add(ref, route.productId, quantity.value)
            snackbar.send("Added to cart", actionLabel = "View cart")
        }
    }
}
