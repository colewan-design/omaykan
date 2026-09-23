package com.omaykan.storefront.core.data

import com.omaykan.storefront.core.model.ShopSummary
import com.omaykan.storefront.core.model.StoreRef
import com.omaykan.storefront.core.network.ApiBaseUrl
import com.omaykan.storefront.core.network.ApiCaller
import com.omaykan.storefront.core.network.OmaykanApi
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Which shops there are. GET /api/stores.
 *
 * Not cached, on purpose. The catalog is cached because a shopper standing in a
 * dead spot still wants to see the shelf they were just looking at; a directory
 * of shops that may since have closed, emptied their shelf or been suspended is
 * worth less than an honest "we could not reach the market". Revisit if a cold
 * launch on a bad connection turns out to feel broken.
 */
@Singleton
class ShopDirectoryRepository @Inject constructor(
    private val api: OmaykanApi,
    private val caller: ApiCaller,
    @ApiBaseUrl private val baseUrl: String,
) {
    /**
     * @param lat and [lng] are all-or-nothing: with both, the server sorts by
     * distance and fills in distanceKm; with neither it sorts by name. Half a
     * coordinate is a 422, so one without the other is dropped here.
     */
    suspend fun shops(
        query: String? = null,
        lat: Double? = null,
        lng: Double? = null,
    ): List<ShopSummary> {
        val pinned = lat != null && lng != null
        val dto = caller.call {
            api.stores(
                query = query?.trim()?.takeIf { it.isNotEmpty() },
                lat = if (pinned) lat else null,
                lng = if (pinned) lng else null,
            )
        }
        return dto.stores.mapNotNull { it.toModelOrNull(baseUrl) }
    }

    /**
     * One shop, by reference.
     *
     * The directory is the only public endpoint that reports a shop's business
     * mode — the catalog returns a name and an address but not the mode.
     * Checkout needs the mode, so this is where it comes from.
     */
    suspend fun shop(ref: StoreRef): ShopSummary? = shops().firstOrNull { it.ref == ref }
}
