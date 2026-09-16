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
