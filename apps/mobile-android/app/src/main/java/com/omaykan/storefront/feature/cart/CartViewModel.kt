package com.omaykan.storefront.feature.cart

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.omaykan.storefront.core.data.CartRepository
import com.omaykan.storefront.core.data.CatalogRepository
import com.omaykan.storefront.core.model.Cart
import com.omaykan.storefront.core.model.StoreRef
import com.omaykan.storefront.navigation.CartRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CartUiState(
    val cart: Cart,
    val shopName: String = "",
)

@HiltViewModel
class CartViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val cartRepository: CartRepository,
    catalogRepository: CatalogRepository,
) : ViewModel() {

    private val route = savedStateHandle.toRoute<CartRoute>()
    val ref = StoreRef(route.orgSlug, route.storeCode)

    val state: StateFlow<CartUiState> = combine(
        cartRepository.cart(ref),
        catalogRepository.cached(ref),
    ) { cart, catalog ->
        CartUiState(cart = cart, shopName = catalog?.shop?.name.orEmpty())
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = CartUiState(Cart(ref)),
    )

    fun increment(productId: String, by: Double = 1.0) {
        viewModelScope.launch { cartRepository.add(ref, productId, by) }
    }

    fun setQuantity(productId: String, quantity: Double) {
        viewModelScope.launch { cartRepository.setQuantity(ref, productId, quantity) }
    }

    fun remove(productId: String) {
        viewModelScope.launch { cartRepository.remove(ref, productId) }
    }

    /** Clears the lines the shop no longer sells, and only those. */
    fun dropUnavailable() {
        viewModelScope.launch {
            state.value.cart.unavailableIds.forEach { cartRepository.remove(ref, it) }
        }
    }
}
