package com.omaykan.storefront.core.data

import com.omaykan.storefront.core.database.CachedCategoryEntity
import com.omaykan.storefront.core.database.CachedProductEntity
import com.omaykan.storefront.core.database.CachedProductPhoto
import com.omaykan.storefront.core.database.CachedShopEntity
import com.omaykan.storefront.core.database.CartDao
import com.omaykan.storefront.core.database.CartLineEntity
import com.omaykan.storefront.core.database.CatalogDao
import com.omaykan.storefront.core.model.StoreRef
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class CartRepositoryTest {

    private lateinit var cartDao: FakeCartDao
    private lateinit var repository: CartRepository

    private val ref = StoreRef("demo-coffee", "main")

    @Before
    fun setUp() {
        cartDao = FakeCartDao()
        val catalogDao = ShelfOnlyCatalogDao(
            listOf(
                product("beans", "Beans", position = 0),
                product("milk", "Milk", position = 1),
                product("sugar", "Sugar", position = 2),
            ),
        )
        repository = CartRepository(cartDao, catalogDao)
    }

    /**
     * The bug this exists for: changing one line's quantity re-inserted the row
     * with the current time in `addedAtEpochMs`, which is what the cart sorts
     * on, so the item being adjusted jumped to the bottom of the basket. On
     * screen that reads as the tap having landed on some other item — the row
     * under the thumb is suddenly a different product showing a different
     * count.
     */
    @Test
    fun `changing a quantity leaves the basket in the order things were added`() = runTest {
        repository.add(ref, "beans")
        repository.add(ref, "milk")
        repository.add(ref, "sugar")

        repository.setQuantity(ref, "beans", 4.0)
        repository.add(ref, "milk", 2.0)

        val cart = repository.cart(ref).first()
        assertEquals(
            listOf("beans", "milk", "sugar"),
            cart.lines.map { it.product.id },
        )
        assertEquals(
            listOf(4.0, 3.0, 1.0),
            cart.lines.map { it.quantity },
        )
    }

    /** The count a shopper set lands on that line and on nothing else. */
    @Test
    fun `a quantity change touches one line only`() = runTest {
        repository.add(ref, "beans")
        repository.add(ref, "milk")

        repository.setQuantity(ref, "milk", 7.0)

        val quantities = repository.cart(ref).first().lines.associate { it.product.id to it.quantity }
        assertEquals(mapOf("beans" to 1.0, "milk" to 7.0), quantities)
    }

    /** A line that was never in the basket is still an insert, timed now. */
    @Test
    fun `a new line is appended rather than lost`() = runTest {
        repository.add(ref, "beans")
        repository.setQuantity(ref, "sugar", 2.0)

        assertEquals(
            listOf("beans", "sugar"),
            repository.cart(ref).first().lines.map { it.product.id },
        )
    }

    @Test
    fun `zero removes the line`() = runTest {
        repository.add(ref, "beans")
        repository.add(ref, "milk")

        repository.setQuantity(ref, "beans", 0.0)

        assertEquals(listOf("milk"), repository.cart(ref).first().lines.map { it.product.id })
    }

    private fun product(id: String, name: String, position: Int) = CachedProductEntity(
        storeKey = ref.key,
        productId = id,
        categoryId = "uncategorized",
        sku = id.uppercase(),
        barcode = "",
        name = name,
        priceCents = 10_000,
        compareAtPriceCents = null,
        taxRate = 0.0,
        kind = "standard",
        imageUrl = null,
        unitLabel = null,
        stockQty = null,
        lowStockThreshold = null,
        position = position,
    )
}

/**
 * Room's cart table, in memory: a composite key of store and product, and reads
 * sorted the way the real query sorts them.
 */
private class FakeCartDao : CartDao {
    private val lines = MutableStateFlow<List<CartLineEntity>>(emptyList())

    /** Stands in for the clock, so "added later" is a fact rather than a race. */
    private var tick = 0L

    override fun observeLines(storeKey: String): Flow<List<CartLineEntity>> =
        lines.map { rows ->
            rows.filter { it.storeKey == storeKey }
                .sortedWith(compareBy({ it.addedAtEpochMs }, { it.productId }))
        }

    override fun observeAll(): Flow<List<CartLineEntity>> = lines

    override suspend fun quantityOf(storeKey: String, productId: String): Double? =
        lines.value.firstOrNull { it.storeKey == storeKey && it.productId == productId }?.quantity

    override suspend fun upsert(line: CartLineEntity) {
        // The insert path always stamps a fresh time, exactly as the repository
        // does; the fake counter only makes that time predictable.
        val stamped = line.copy(addedAtEpochMs = ++tick)
        lines.value = lines.value.filterNot {
            it.storeKey == line.storeKey && it.productId == line.productId
        } + stamped
    }

    override suspend fun updateQuantity(
        storeKey: String,
        productId: String,
        quantity: Double,
    ): Int {
        var updated = 0
        lines.value = lines.value.map { row ->
            if (row.storeKey == storeKey && row.productId == productId) {
                updated++
                row.copy(quantity = quantity)
            } else {
                row
            }
        }
        return updated
    }

    override suspend fun remove(storeKey: String, productId: String) {
        lines.value = lines.value.filterNot {
            it.storeKey == storeKey && it.productId == productId
        }
    }

    override suspend fun clear(storeKey: String) {
        lines.value = lines.value.filterNot { it.storeKey == storeKey }
    }
}

/** Enough of the catalog cache for the cart to join against: a fixed shelf. */
private class ShelfOnlyCatalogDao(private val shelf: List<CachedProductEntity>) : CatalogDao {

    override fun observeShop(storeKey: String): Flow<CachedShopEntity?> =
        MutableStateFlow(null)

    override fun observeCategories(storeKey: String): Flow<List<CachedCategoryEntity>> =
        MutableStateFlow(emptyList())

    override fun observeProducts(storeKey: String): Flow<List<CachedProductEntity>> =
        MutableStateFlow(shelf.filter { it.storeKey == storeKey })

    override fun observeProduct(storeKey: String, productId: String): Flow<CachedProductEntity?> =
        MutableStateFlow(
            shelf.firstOrNull { it.storeKey == storeKey && it.productId == productId },
        )

    override suspend fun photosFor(productIds: List<String>): List<CachedProductPhoto> =
        shelf.filter { it.productId in productIds && it.imageUrl != null }
            .map { CachedProductPhoto(it.productId, it.imageUrl!!) }

    override suspend fun upsertShop(shop: CachedShopEntity) = Unit
    override suspend fun upsertCategories(categories: List<CachedCategoryEntity>) = Unit
    override suspend fun upsertProducts(products: List<CachedProductEntity>) = Unit
    override suspend fun deleteCategories(storeKey: String) = Unit
    override suspend fun deleteProducts(storeKey: String) = Unit
    override suspend fun deleteShop(storeKey: String) = Unit
}
