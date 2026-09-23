package com.omaykan.storefront.core.database

import androidx.room.Entity

/**
 * A line in the cart, per shop.
 *
 * In Room rather than a SavedStateHandle because the cart has to survive
 * process death: someone fills a basket, takes a call, and comes back twenty
 * minutes later to a phone that has killed the app. Losing their basket there
 * is losing the order.
 *
 * Only the id and the quantity are stored. Name and price are read back off the
 * cached catalog at render time, so a cart cannot quietly show a price the shop
 * has since changed — and the server re-prices everything at checkout anyway.
 */
@Entity(
    tableName = "cart_line",
    primaryKeys = ["storeKey", "productId"],
)
data class CartLineEntity(
    val storeKey: String,
    val productId: String,
    val quantity: Double,
    val addedAtEpochMs: Long,
)
