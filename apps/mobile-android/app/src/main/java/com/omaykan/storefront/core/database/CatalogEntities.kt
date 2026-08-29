package com.omaykan.storefront.core.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/*
 * The catalog cache, keyed by storeKey ("orgSlug/storeCode").
 *
 * Three tables rather than one blob of JSON: the category rail and the search
 * box both want to filter, and a shop with a few hundred lines should not have
 * its whole shelf re-parsed to draw one tab.
 *
 * Position columns preserve the server's ordering — products come back sorted
 * by name and categories by sort_order, and the merchant's chosen order is a
 * decision this app must not quietly re-make.
 */

@Entity(tableName = "cached_shop")
data class CachedShopEntity(
    @PrimaryKey val storeKey: String,
    val name: String,
    val businessTypeLabel: String?,
    val ownerName: String?,
    val address: String?,
    val fetchedAtEpochMs: Long,
)

@Entity(
    tableName = "cached_category",
    primaryKeys = ["storeKey", "categoryId"],
)
data class CachedCategoryEntity(
    val storeKey: String,
    val categoryId: String,
    val name: String,
    val position: Int,
)

@Entity(
    tableName = "cached_product",
    primaryKeys = ["storeKey", "productId"],
    indices = [Index(value = ["storeKey", "categoryId"])],
)
data class CachedProductEntity(
    val storeKey: String,
    val productId: String,
    val categoryId: String,
    val sku: String,
    val barcode: String,
    val name: String,
    val priceCents: Long,
    val compareAtPriceCents: Long?,
    val taxRate: Double,
    val kind: String,
    val imageUrl: String?,
    val unitLabel: String?,
    val stockQty: Double?,
    val lowStockThreshold: Double?,
    val position: Int,
)

/**
 * Two columns off [CachedProductEntity], for a query that wants nothing else.
 *
 * Not an entity and not a table — a projection. Reading whole product rows to
 * put a thumbnail on an order card would parse fifteen columns to use one.
 */
data class CachedProductPhoto(
    val productId: String,
    val imageUrl: String,
)
