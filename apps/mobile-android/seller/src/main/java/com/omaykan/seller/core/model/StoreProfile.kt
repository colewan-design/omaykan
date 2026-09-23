package com.omaykan.seller.core.model

/**
 * What the public directory says about this shop: the facts the store
 * profile shows beyond its name and code.
 */
data class StoreProfile(
    /** False when the directory does not list the shop — nothing sellable on its shelf yet. */
    val listed: Boolean,
    val businessTypeLabel: String?,
    val address: String?,
    val lat: Double?,
    val lng: Double?,
    /** The owner's own uploaded photo, versioned. Never a shelf photo standing in. */
    val photoUrl: String?,
) {
    /** Both coordinates, and not the 0,0 an unset pin defaults to. */
    val placed: Boolean get() = lat != null && lng != null && !(lat == 0.0 && lng == 0.0)

    companion object {
        val Unknown = StoreProfile(false, null, null, null, null, null)
    }
}
