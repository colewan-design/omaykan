package com.omaykan.storefront.core.data

import com.omaykan.storefront.core.database.CartDao
import com.omaykan.storefront.core.database.CartLineEntity
import com.omaykan.storefront.core.database.CatalogDao
import com.omaykan.storefront.core.model.Cart
import com.omaykan.storefront.core.model.CartLine
import com.omaykan.storefront.core.model.StoreRef
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CartRepository @Inject constructor(
    private val cartDao: CartDao,
    private val catalogDao: CatalogDao,
) {
    /**
     * The basket for one shop, joined against that shop's cached shelf.
     *
     * A line whose product is no longer in the catalog is not dropped — the
     * server has already filtered out anything sold out or delisted, so its
     * absence is news, and the cart screen says so.
     */
    fun cart(ref: StoreRef): Flow<Cart> = combine(
        cartDao.observeLines(ref.key),
        catalogDao.observeProducts(ref.key),
    ) { lines, products ->
        val shelf = products.associateBy { it.productId }
        val resolved = mutableListOf<CartLine>()
        val missing = mutableListOf<String>()

        lines.forEach { line ->
            val product = shelf[line.productId]
            if (product == null) {
                missing += line.productId
            } else {
                resolved += CartLine(product.toModel(), line.quantity)
            }
        }

        Cart(ref = ref, lines = resolved, unavailableIds = missing)
    }

    /** Total lines held on this device, across every shop — the market badge. */
    fun totalLineCount(): Flow<Int> = cartDao.observeAll().map { it.size }

    /**
     * Lines in one shop's basket — the badge inside that shop.
     *
     * Straight off the cart table rather than through [cart], because a count
     * does not need the shelf join and should not wait on it. It also counts a
     * line whose product has since left the catalog, which is right: the line
     * is still in the basket, and the cart screen is where that gets explained.
     */
    fun lineCount(ref: StoreRef): Flow<Int> = cartDao.observeLines(ref.key).map { it.size }

    suspend fun add(ref: StoreRef, productId: String, quantity: Double = 1.0) {
        val existing = cartDao.quantityOf(ref.key, productId) ?: 0.0
        setQuantity(ref, productId, existing + quantity)
    }

    /**
     * Sets a line's quantity. At or below zero removes it rather than storing a
     * nonsense one.
     *
     * An update first, an insert only if there was nothing to update, so that a
     * line that is already in the basket keeps the time it was put there. That
     * time is the sort order of the whole cart: stamping it afresh on every
     * change sent the item under the shopper's thumb to the bottom of the list,
     * and the row that slid up into its place — a different item, with a
     * different count — looked for all the world like the tap had landed on the
     * wrong thing.
     *
     * `addedAtEpochMs` means added, not touched. Nothing reads it as "last
     * changed", and the basket is in the order things went into it.
     */
    suspend fun setQuantity(ref: StoreRef, productId: String, quantity: Double) {
        if (quantity <= 0.0) {
            cartDao.remove(ref.key, productId)
            return
        }
        if (cartDao.updateQuantity(ref.key, productId, quantity) > 0) return

        cartDao.upsert(
            CartLineEntity(
                storeKey = ref.key,
                productId = productId,
                quantity = quantity,
                addedAtEpochMs = System.currentTimeMillis(),
            ),
        )
    }

    suspend fun remove(ref: StoreRef, productId: String) = cartDao.remove(ref.key, productId)

    suspend fun clear(ref: StoreRef) = cartDao.clear(ref.key)
}
