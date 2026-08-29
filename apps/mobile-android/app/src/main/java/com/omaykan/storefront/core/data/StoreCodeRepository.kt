package com.omaykan.storefront.core.data

import com.omaykan.storefront.core.model.PairedStore
import com.omaykan.storefront.core.network.ApiCaller
import com.omaykan.storefront.core.network.OmaykanApi
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Resolving the short code a merchant hands out. POST /api/store-codes/resolve.
 *
 * Two failures matter and they are not the same failure:
 *
 *  - 404 — no such code, or the shop is archived. The server deliberately gives
 *    the same message either way, because the endpoint is public and a short
 *    code space is worth guessing at. Say "we could not find that code".
 *  - 409 — the shop is real and open, it just does not sell online (a salon has
 *    nothing to put in a cart). Surface the server's own sentence. Telling
 *    someone their code is wrong when they read it correctly off the shop's own
 *    tarpaulin sends them back to the counter for a code that will never work.
 */
@Singleton
class StoreCodeRepository @Inject constructor(
    private val api: OmaykanApi,
    private val caller: ApiCaller,
) {
    suspend fun resolve(code: String): PairedStore =
        caller.call { api.resolveStoreCode(code.trim()) }.toModel()
}
