package com.omaykan.seller.core.data

import com.omaykan.seller.core.network.ApiCaller
import com.omaykan.seller.core.network.SellerApi
import com.omaykan.seller.core.network.dto.CreatePromoCodeRequestDto
import com.omaykan.seller.core.network.dto.PromoCodeDto
import com.omaykan.seller.core.network.dto.UpdatePromoCodeRequestDto
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The shop's promo and voucher codes. Straight through to the server, which
 * holds the rules and counts the uses; nothing is cached, because a use count
 * shown from memory is a number that was true a while ago.
 *
 * See documentation/merchant-features.md §8.
 */
@Singleton
class PromoCodeRepository @Inject constructor(
    private val api: SellerApi,
    private val caller: ApiCaller,
) {
    suspend fun list(): List<PromoCodeDto> = caller.call { api.promoCodes() }.promoCodes

    suspend fun create(request: CreatePromoCodeRequestDto): PromoCodeDto =
        caller.call { api.createPromoCode(request) }.promoCode

    suspend fun setActive(id: String, active: Boolean): PromoCodeDto =
        caller.call { api.updatePromoCode(id, UpdatePromoCodeRequestDto(active)) }.promoCode

    suspend fun retire(id: String) {
        caller.call { api.deletePromoCode(id) }
    }
}
