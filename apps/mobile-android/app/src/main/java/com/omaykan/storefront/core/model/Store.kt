package com.omaykan.storefront.core.model

/**
 * How every call addresses a shop: the organization slug plus the store code.
 * The API has no single "store id" a client may use — StoreCodeController and
 * StorefrontCatalogController both take this pair — so it travels as one thing.
 */
data class StoreRef(
    val orgSlug: String,
    val storeCode: String,
) {
    /** Stable key for the Room cache and for DataStore. */
    val key: String get() = "$orgSlug/$storeCode"

    companion object {
        fun fromKey(key: String): StoreRef? {
            val parts = key.split('/', limit = 2)
            if (parts.size != 2 || parts.any { it.isBlank() }) return null
            return StoreRef(parts[0], parts[1])
        }
    }
}

/**
 * The three modes that can put something in a cart. A salon has no shelf, and
 * the API rejects it with a 409 — see StoreCodeController::ONLINE_MODES.
 *
 * Unknown is not an error: the server may learn a fourth mode before this app
 * is updated, and a shop we cannot categorise should still be browsable.
 */
enum class BusinessMode(val wire: String) {
    CoffeeShop("coffee-shop"),
    Grocery("grocery"),
    Restaurant("restaurant"),
    Unknown("");

    companion object {
        fun fromWire(value: String?): BusinessMode =
            entries.firstOrNull { it.wire == value } ?: Unknown
    }
}

/**
 * A shop as the directory lists it — enough to render a card and decide whether
 * to walk in. `GET /api/stores`.
 */
data class ShopSummary(
    val ref: StoreRef,
    val name: String,
    val businessMode: BusinessMode,
    val businessTypeLabel: String?,
    val address: String,
    val imageUrl: String?,
    val lat: Double?,
    val lng: Double?,
    val productCount: Int,
    /** Null unless the request carried a pin — see StoreDirectoryController. */
    val distanceKm: Double?,
)

/**
 * Who the shopper is buying from, as the catalog names them.
 *
 * The shop is named on the goods rather than only in the footer on purpose:
 * the customer is about to hand cash to a rider on behalf of a shop they have
 * never seen. See StorefrontCatalogController::shopOf.
 */
data class Shop(
    val name: String,
    val businessTypeLabel: String?,
    val ownerName: String?,
    val address: String?,
)
