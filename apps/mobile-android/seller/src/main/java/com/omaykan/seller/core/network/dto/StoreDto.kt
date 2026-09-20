package com.omaykan.seller.core.network.dto

import kotlinx.serialization.Serializable

/**
 * `GET /api/stores` — the public shop directory, as far as the store profile
 * reads it. There is no store id in it; a row is matched on the organization
 * slug and branch code the session already holds.
 */
@Serializable
data class StoreDirectoryDto(
    val stores: List<DirectoryStoreDto> = emptyList(),
)

@Serializable
data class DirectoryStoreDto(
    val orgSlug: String? = null,
    val storeCode: String? = null,
    val name: String? = null,
    val businessTypeLabel: String? = null,
    val address: String? = null,
    /**
     * The owner's uploaded photo when there is one — `/api/stores/{id}/image?v=…`
     * — and otherwise a picture off the shop's shelf. Only the first is the
     * shop's own face; see StoreProfileRepository.
     */
    val imageUrl: String? = null,
    val lat: Double? = null,
    val lng: Double? = null,
)

/**
 * `PUT /api/seller/store-image`. A data URL — the shape the register sends —
 * never null from this app: removing a photo is not offered here.
 */
@Serializable
data class StoreImageRequestDto(val image: String)

@Serializable
data class StoreImageDto(val imageUrl: String? = null)

/**
 * `GET|PUT /api/seller/ordering` — the shop's own "not taking online orders
 * right now". `StoreOrderingController::stateOf` on the server.
 */
@Serializable
data class OrderingStateDto(
    val paused: Boolean = false,
    /** ISO-8601; null while open, or while paused until reopened by hand. */
    val resumesAt: String? = null,
    /** What shoppers are told. Null while open. */
    val message: String? = null,
)

@Serializable
data class OrderingRequestDto(
    val paused: Boolean,
    val resumesAt: String? = null,
)

/** `POST /api/seller/product-images` — a data URL in, a URL out. */
@Serializable
data class ProductImageRequestDto(val image: String)

@Serializable
data class ProductImageDto(val url: String)

/** `PromoCodeController::present` on the server. */
@Serializable
data class PromoCodeDto(
    val id: String,
    val code: String,
    val kind: String = "percent",
    val percent: Double? = null,
    val amountCents: Long? = null,
    val minSubtotalCents: Long = 0,
    val maxDiscountCents: Long? = null,
    val channel: String = "online",
    val endsAt: String? = null,
    val maxRedemptions: Int? = null,
    val perCustomerLimit: Int? = null,
    val isActive: Boolean = true,
    val redemptions: Int = 0,
    val description: String = "",
)

@Serializable
data class PromoCodesDto(val promoCodes: List<PromoCodeDto> = emptyList())

@Serializable
data class PromoCodeEnvelopeDto(val promoCode: PromoCodeDto)

/** Nulls are left out on the wire (explicitNulls = false): the server's defaults apply. */
@Serializable
data class CreatePromoCodeRequestDto(
    val code: String,
    val kind: String,
    val percent: Double? = null,
    val amountCents: Long? = null,
    val minSubtotalCents: Long = 0,
    val channel: String = "online",
    val maxRedemptions: Int? = null,
    val perCustomerLimit: Int? = null,
)

@Serializable
data class UpdatePromoCodeRequestDto(val isActive: Boolean)
