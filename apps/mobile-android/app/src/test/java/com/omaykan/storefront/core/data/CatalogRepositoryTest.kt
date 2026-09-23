package com.omaykan.storefront.core.data

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.omaykan.storefront.core.database.CachedCategoryEntity
import com.omaykan.storefront.core.database.CachedProductEntity
import com.omaykan.storefront.core.database.CachedProductPhoto
import com.omaykan.storefront.core.database.CachedShopEntity
import com.omaykan.storefront.core.database.CatalogDao
import com.omaykan.storefront.core.model.StoreRef
import com.omaykan.storefront.core.network.ApiCaller
import com.omaykan.storefront.core.network.OmaykanApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit

class CatalogRepositoryTest {

    private lateinit var server: MockWebServer
    private lateinit var dao: FakeCatalogDao
    private lateinit var repository: CatalogRepository

    private val ref = StoreRef("demo-coffee", "main")

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()

        val json = Json { ignoreUnknownKeys = true }
        val api = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(OmaykanApi::class.java)

        dao = FakeCatalogDao()
        repository = CatalogRepository(api, dao, ApiCaller(json), "https://omaykan.com")
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    /**
     * The rule from §7 of the mobile plan, as a test.
     *
     * A shop that empties its shelf — or a catalog that comes back empty for any
     * other reason — must overwrite the cache, not be quietly replaced by it.
     * The shopper cannot tell the difference between a stale shelf and a live
     * one, and the merchant is the one who pays for an order placed against
     * goods that are gone.
     */
    @Test
    fun `an empty catalog replaces a cached one instead of falling back to it`() = runTest {
        server.enqueue(MockResponse().setBody(CATALOG_WITH_ONE_PRODUCT))
        repository.refresh(ref)
        assertEquals(1, repository.cached(ref).first()?.products?.size)

        server.enqueue(MockResponse().setBody(EMPTY_CATALOG))
        repository.refresh(ref)

        val catalog = repository.cached(ref).first()
        assertNotNull("the shop itself is still known", catalog)
        assertTrue("the shelf is empty, not stale", catalog!!.products.isEmpty())
        assertTrue(catalog.categories.isEmpty())
    }

    @Test
    fun `a failed refresh leaves the cached shelf intact`() = runTest {
        server.enqueue(MockResponse().setBody(CATALOG_WITH_ONE_PRODUCT))
        repository.refresh(ref)

        server.enqueue(MockResponse().setResponseCode(500).setBody("""{"message":"boom"}"""))
        runCatching { repository.refresh(ref) }

        assertEquals(1, repository.cached(ref).first()?.products?.size)
    }

    @Test
    fun `the server's product order is preserved`() = runTest {
        server.enqueue(MockResponse().setBody(CATALOG_WITH_THREE_PRODUCTS))
        repository.refresh(ref)

        val names = repository.cached(ref).first()?.products?.map { it.name }

        // The merchant's register sorts by name; re-sorting here would quietly
        // overrule a decision that is theirs to make.
        assertEquals(listOf("Barako", "Kapeng Barako", "Zamboanga Blend"), names)
    }

    private companion object {
        const val EMPTY_CATALOG = """
            {"store":{"name":"Demo Coffee","ownerName":"Ana"},"categories":[],"products":[]}
        """

        const val CATALOG_WITH_ONE_PRODUCT = """
            {"store":{"name":"Demo Coffee","ownerName":"Ana"},
             "categories":[{"id":"c1","name":"Drinks"}],
             "products":[{"id":"p1","categoryId":"c1","name":"Barako","priceCents":6500}]}
        """

        const val CATALOG_WITH_THREE_PRODUCTS = """
            {"store":{"name":"Demo Coffee"},
             "categories":[],
             "products":[
               {"id":"p1","name":"Barako","priceCents":6500},
               {"id":"p2","name":"Kapeng Barako","priceCents":7500},
               {"id":"p3","name":"Zamboanga Blend","priceCents":9500}]}
        """
    }
}

/**
 * In-memory stand-in for Room. replaceCatalog() and clear() are not overridden
 * on purpose — they carry the transaction logic under test, and a fake that
 * reimplemented them would be testing itself.
 */
private class FakeCatalogDao : CatalogDao {
    private val shops = MutableStateFlow<Map<String, CachedShopEntity>>(emptyMap())
    private val categories = MutableStateFlow<List<CachedCategoryEntity>>(emptyList())
    private val products = MutableStateFlow<List<CachedProductEntity>>(emptyList())

    override fun observeShop(storeKey: String): Flow<CachedShopEntity?> =
        shops.map { it[storeKey] }

    override fun observeCategories(storeKey: String): Flow<List<CachedCategoryEntity>> =
        categories.map { rows -> rows.filter { it.storeKey == storeKey }.sortedBy { it.position } }

    override fun observeProducts(storeKey: String): Flow<List<CachedProductEntity>> =
        products.map { rows -> rows.filter { it.storeKey == storeKey }.sortedBy { it.position } }

    override fun observeProduct(storeKey: String, productId: String): Flow<CachedProductEntity?> =
        products.map { rows ->
            rows.firstOrNull { it.storeKey == storeKey && it.productId == productId }
        }

    override suspend fun photosFor(productIds: List<String>): List<CachedProductPhoto> =
        products.value
            .filter { it.productId in productIds && it.imageUrl != null }
            .map { CachedProductPhoto(it.productId, it.imageUrl!!) }

    override suspend fun upsertShop(shop: CachedShopEntity) {
        shops.value = shops.value + (shop.storeKey to shop)
    }

    override suspend fun upsertCategories(categories: List<CachedCategoryEntity>) {
        this.categories.value = this.categories.value + categories
    }

    override suspend fun upsertProducts(products: List<CachedProductEntity>) {
        this.products.value = this.products.value + products
    }

    override suspend fun deleteCategories(storeKey: String) {
        categories.value = categories.value.filterNot { it.storeKey == storeKey }
    }

    override suspend fun deleteProducts(storeKey: String) {
        products.value = products.value.filterNot { it.storeKey == storeKey }
    }

    override suspend fun deleteShop(storeKey: String) {
        shops.value = shops.value - storeKey
    }
}
