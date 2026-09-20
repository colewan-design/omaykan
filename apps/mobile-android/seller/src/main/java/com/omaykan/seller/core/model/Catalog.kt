package com.omaykan.seller.core.model

/**
 * The till's default for "running low", used when neither the branch nor the
 * product names a threshold. Mirrors `p.lowStockThreshold ?? 5` in
 * packages/core/src/stores/pos.ts, so the phone and the register count the
 * same products as low.
 */
const val DEFAULT_LOW_STOCK_THRESHOLD = 5.0

/**
 * What the signed-in person may do at this shop.
 *
 * Only the distinction the server itself draws: `StoreContext::isManager()` —
 * "the roles allowed to change what the shop sells" — is admin or manager.
 * Anything else, including a role this build has never heard of, is treated as
 * a cashier: shown the catalog, not handed the pen.
 */
enum class StaffRole {
    Admin,
    Manager,
    Cashier,
    ;

    val managesCatalog: Boolean get() = this == Admin || this == Manager

    companion object {
        fun fromWire(value: String?): StaffRole = when (value) {
            "admin" -> Admin
            "manager" -> Manager
            else -> Cashier
        }
    }
}

data class Category(
    val id: String,
    val name: String,
    val sortOrder: Int,
)

/**
 * One product, as this branch sells it.
 *
 * Carries every field the sync payload needs to write it back, not only the
 * ones on screen. `/sync/push` replaces a product row wholesale — a field left
 * out falls back to the server's default — so an edit from the phone has to
 * send back the SKU, barcode and tax rate it was never shown, or it would
 * quietly wipe them. See ProductEvents.
 */
data class Product(
    val id: String,
    val name: String,
    val categoryId: String?,
    val sku: String?,
    val barcode: String?,
    val productType: String,
    val taxRate: Double?,
    /** The organization's price. See [shownPriceCents] for what customers pay here. */
    val priceCents: Long,
    val trackInventory: Boolean,
    val active: Boolean,
    val businessModes: List<String>,
    val unitLabel: String?,
    val imageUrl: String?,
    /** What the product is, in the shop's words. Shown on the storefront. */
    val description: String? = null,
    /** On hand at this branch. Null when the product is not stock-tracked. */
    val stockQty: Double?,
    /** This branch's reorder level, else the product's own. Null: the till's default. */
    val lowStockThreshold: Double?,
    /** A price set for this branch alone, which customers here see instead. */
    val branchPriceCents: Long?,
    /** False when this branch has switched the product off. Null inherits. */
    val branchAvailable: Boolean?,
) {
    val shownPriceCents: Long get() = branchPriceCents ?: priceCents

    val soldOut: Boolean get() = stockQty != null && stockQty <= 0.0

    /** The till's rule, exactly: tracked, and at or under the threshold. */
    val lowStock: Boolean
        get() = stockQty != null && stockQty <= (lowStockThreshold ?: DEFAULT_LOW_STOCK_THRESHOLD)

    /** "50 kg", "30", "1.5 kg" — stock as a merchant would say it. */
    val stockLabel: String?
        get() = stockQty?.let { qty -> listOfNotNull(formatQuantity(qty), unitLabel).joinToString(" ") }
}

data class Catalog(
    val organizationId: String,
    val storeId: String,
    /** The signed-in person's name, for the greeting. */
    val userName: String?,
    val role: StaffRole,
    val categories: List<Category>,
    val products: List<Product>,
) {
    val lowStockCount: Int get() = products.count { it.active && it.lowStock }

    fun categoryName(id: String?): String? = categories.firstOrNull { it.id == id }?.name
}

/**
 * What the product form sends: a new product when [productId] is null, an edit
 * of that product otherwise.
 *
 * [stockQty] is the count the merchant typed — a target, not a change. The
 * repository turns it into the adjustment that gets the shelf there.
 */
data class ProductDraft(
    val productId: String?,
    val name: String,
    val categoryId: String?,
    val priceCents: Long,
    val stockQty: Double?,
    val lowStockThreshold: Double?,
    val active: Boolean,
    /**
     * How the product looks online. Null leaves all three exactly as the
     * server has them — the keys are not sent at all — which is what every
     * caller that never showed the merchant these fields must do.
     */
    val storefront: StorefrontFields? = null,
)

/**
 * The storefront-facing fields the product form edits. Each is sent as given,
 * null included: a cleared photo or description is the merchant removing it.
 */
data class StorefrontFields(
    /** A URL — upload a picked photo first, see CatalogRepository.uploadPhoto. */
    val imageUrl: String?,
    val unitLabel: String?,
    val description: String?,
)

/** 3 → "3", 1.5 → "1.5", 0.25 → "0.25". A grocery sells by the kilo. */
fun formatQuantity(quantity: Double): String =
    if (quantity % 1.0 == 0.0) {
        quantity.toLong().toString()
    } else {
        quantity.toBigDecimal().stripTrailingZeros().toPlainString()
    }
