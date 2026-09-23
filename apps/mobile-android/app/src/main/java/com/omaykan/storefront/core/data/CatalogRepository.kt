package com.omaykan.storefront.core.data

import com.omaykan.storefront.core.database.CatalogDao
import com.omaykan.storefront.core.model.Catalog
import com.omaykan.storefront.core.model.Product
import com.omaykan.storefront.core.model.Shop
import com.omaykan.storefront.core.model.StoreRef
import com.omaykan.storefront.core.network.ApiBaseUrl
import com.omaykan.storefront.core.network.ApiCaller
import com.omaykan.storefront.core.network.OmaykanApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * One shop's shelf, cache first.
 *
 * The read path and the refresh path are deliberately separate calls rather than
 * one clever stream. Stale-while-revalidate is a decision about what to *show*,
 * and that belongs to the screen: the repository's job is to say what is cached
 * and to go and get more when asked.
 */
@Singleton
class CatalogRepository @Inject constructor(
    private val api: OmaykanApi,
    private val dao: CatalogDao,
    private val caller: ApiCaller,
    @ApiBaseUrl private val baseUrl: String,
) {
    /** Emits null until this shop has been fetched at least once on this device. */
    fun cached(ref: StoreRef): Flow<Catalog?> {
        val key = ref.key
        return combine(
            dao.observeShop(key),
            dao.observeCategories(key),
            dao.observeProducts(key),
        ) { shop, categories, products ->
            shop ?: return@combine null
            Catalog(
                shop = shop.toModel(),
                categories = categories.map { it.toModel() },
                products = products.map { it.toModel() },
                fetchedAtEpochMs = shop.fetchedAtEpochMs,
            )
        }
    }

    fun cachedProduct(ref: StoreRef, productId: String): Flow<Product?> =
        dao.observeProduct(ref.key, productId).map { it?.toModel() }

    /**
     * Cached photos for a set of products, by id — for screens that name a
     * product the shopper no longer has a shelf open on, such as their orders.
     *
     * A one-shot read rather than a flow: the caller has a list in hand and
     * wants pictures for it, and a photo arriving later would only reflow a
     * list somebody is already reading. Ids with nothing cached are absent
     * from the answer rather than mapped to null.
     */
    suspend fun photosFor(productIds: Collection<String>): Map<String, String> {
        val ids = productIds.filter { it.isNotBlank() }.distinct()
        if (ids.isEmpty()) return emptyMap()
        return dao.photosFor(ids).associate { it.productId to it.imageUrl }
    }

    /**
     * Just the shop, for screens that name the seller but do not draw a shelf.
     * Reading the whole catalog to render one address would re-parse every
     * product row on every write to the cart.
     */
    fun cachedShop(ref: StoreRef): Flow<Shop?> =
        dao.observeShop(ref.key).map { it?.toModel() }

    /**
     * Fetch and replace. Throws ApiException; the caller decides whether a
     * failure is worth showing given what is already on screen.
     *
     * A 200 with an empty product list is written through like any other
     * answer. That is the whole point of §7 of the mobile plan: a cached shelf
     * must never stand in for a live empty one, because the shopper cannot tell
     * the difference and the merchant is the one who pays for the mistake.
     */
    suspend fun refresh(ref: StoreRef) {
        val key = ref.key
        val dto = caller.call { api.catalog(ref.orgSlug, ref.storeCode) }
        dao.replaceCatalog(
            storeKey = key,
            shop = dto.toShopEntity(key, System.currentTimeMillis()),
            categories = dto.categories.mapIndexed { index, category -> category.toEntity(key, index) },
            products = dto.products.mapIndexed { index, product -> product.toEntity(key, index, baseUrl) },
        )
    }

    suspend fun forget(ref: StoreRef) = dao.clear(ref.key)
}
