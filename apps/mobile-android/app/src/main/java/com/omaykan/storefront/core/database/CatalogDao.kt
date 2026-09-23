package com.omaykan.storefront.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface CatalogDao {

    @Query("SELECT * FROM cached_shop WHERE storeKey = :storeKey")
    fun observeShop(storeKey: String): Flow<CachedShopEntity?>

    @Query("SELECT * FROM cached_category WHERE storeKey = :storeKey ORDER BY position")
    fun observeCategories(storeKey: String): Flow<List<CachedCategoryEntity>>

    @Query("SELECT * FROM cached_product WHERE storeKey = :storeKey ORDER BY position")
    fun observeProducts(storeKey: String): Flow<List<CachedProductEntity>>

    @Query("SELECT * FROM cached_product WHERE storeKey = :storeKey AND productId = :productId")
    fun observeProduct(storeKey: String, productId: String): Flow<CachedProductEntity?>

    /**
     * Photos for products named by id, from whichever shelf cached them.
     *
     * Across every store rather than within one, because an order does not
     * carry a store: `GET /api/customer/orders` answers with what was bought,
     * not where the shelf was. Product ids are UUIDs, so an id is enough to be
     * sure of the match.
     *
     * A product this device has never cached simply has no row here, and the
     * caller draws the same quiet placeholder an unphotographed product gets
     * anywhere else. A photo is worth showing where there is one; it is not
     * worth a network call on a list that has to open instantly.
     */
    @Query(
        "SELECT productId, imageUrl FROM cached_product " +
            "WHERE productId IN (:productIds) AND imageUrl IS NOT NULL",
    )
    suspend fun photosFor(productIds: List<String>): List<CachedProductPhoto>

    /**
     * Replace one shop's whole shelf.
     *
     * A wholesale swap inside a transaction, not a merge: the catalog endpoint
     * already filters out everything the shopper may not buy — inactive
     * products, ones the store has overridden as unavailable, anything out of
     * stock — so a product missing from the response is a product that must
     * disappear here too. Merging would leave sold-out goods on the shelf.
     *
     * An empty products list is therefore written as an empty shelf, on purpose.
     * "Nothing reaches this address" is an answer; showing yesterday's catalog
     * instead would be a lie the shopper cannot detect.
     */
    @Transaction
    suspend fun replaceCatalog(
        storeKey: String,
        shop: CachedShopEntity,
        categories: List<CachedCategoryEntity>,
        products: List<CachedProductEntity>,
    ) {
        deleteCategories(storeKey)
        deleteProducts(storeKey)
        upsertShop(shop)
        upsertCategories(categories)
        upsertProducts(products)
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertShop(shop: CachedShopEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCategories(categories: List<CachedCategoryEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProducts(products: List<CachedProductEntity>)

    @Query("DELETE FROM cached_category WHERE storeKey = :storeKey")
    suspend fun deleteCategories(storeKey: String)

    @Query("DELETE FROM cached_product WHERE storeKey = :storeKey")
    suspend fun deleteProducts(storeKey: String)

    /** Used when the shopper unpairs, so a stranger's shelf does not linger. */
    @Transaction
    suspend fun clear(storeKey: String) {
        deleteCategories(storeKey)
        deleteProducts(storeKey)
        deleteShop(storeKey)
    }

    @Query("DELETE FROM cached_shop WHERE storeKey = :storeKey")
    suspend fun deleteShop(storeKey: String)
}
