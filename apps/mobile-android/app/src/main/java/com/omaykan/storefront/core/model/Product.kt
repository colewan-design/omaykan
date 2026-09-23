package com.omaykan.storefront.core.model

data class Category(
    val id: String,
    val name: String,
)

enum class ProductKind { Standard, Weighted }

data class Product(
    val id: String,
    /** "uncategorized" for a product the merchant never filed — a real value, not a null. */
    val categoryId: String,
    val sku: String,
    val barcode: String,
    val name: String,
    val priceCents: Long,
    val compareAtPriceCents: Long?,
    val taxRate: Double,
    val kind: ProductKind,
    val imageUrl: String?,
    val unitLabel: String?,
    /** Null when the merchant does not track inventory for this product. */
    val stockQty: Double?,
    val lowStockThreshold: Double?,
) {
    /**
     * The server has already dropped anything out of stock, so a product that
     * arrives here is buyable. This is only about warning the shopper that the
     * shelf is nearly bare.
     */
    val runningLow: Boolean
        get() {
            val qty = stockQty ?: return false
            val threshold = lowStockThreshold ?: return false
            return threshold > 0 && qty <= threshold
        }

    val onSale: Boolean
        get() = compareAtPriceCents != null && compareAtPriceCents > priceCents

    /**
     * How much the shop knocked off, as a whole percent — null when nothing was.
     *
     * Same arithmetic and same rounding as discountPercent() in
     * packages/shared/src/index.ts, deliberately: a shopper comparing the web
     * page and the app must not see "Save 33%" on one and "Save 34%" on the
     * other for the same tin.
     */
    val discountPercent: Int?
        get() {
            val was = compareAtPriceCents ?: return null
            if (was <= priceCents) return null
            return Math.round((was - priceCents) * 100.0 / was).toInt()
        }
}

/**
 * One store's shelf, as rendered. `fetchedAt` is null only for a catalog that
 * has never been read from the network.
 */
data class Catalog(
    val shop: Shop,
    val categories: List<Category>,
    val products: List<Product>,
    val fetchedAtEpochMs: Long?,
)
