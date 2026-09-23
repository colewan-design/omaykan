package com.omaykan.storefront.core.data

import com.omaykan.storefront.core.database.CachedCategoryEntity
import com.omaykan.storefront.core.database.CachedProductEntity
import com.omaykan.storefront.core.database.CachedShopEntity
import com.omaykan.storefront.core.model.BusinessMode
import com.omaykan.storefront.core.model.Category
import com.omaykan.storefront.core.model.Product
import com.omaykan.storefront.core.model.ProductKind
import com.omaykan.storefront.core.model.Shop
import com.omaykan.storefront.core.model.ShopSummary
import com.omaykan.storefront.core.model.StoreRef
import com.omaykan.storefront.core.network.dto.CatalogCategoryDto
import com.omaykan.storefront.core.network.dto.CatalogDto
import com.omaykan.storefront.core.network.dto.CatalogProductDto
import com.omaykan.storefront.core.network.dto.StoreDirectoryEntryDto

/**
 * DTO and entity translation, in one file, so that nothing above core/data ever
 * holds a wire type. This is the boundary the module layout exists to protect.
 */

/**
 * The directory hands back a shop photo as a path, not a URL — StoreImageController
 * returns "/api/stores/{id}/image?v=…" — while a product's image_url is whatever
 * the merchant saved, usually already absolute. Resolve here so the model always
 * carries something an image loader can fetch, and the UI never has to know
 * which of the two it is holding.
 */
internal fun absoluteUrl(baseUrl: String, raw: String?): String? {
    val value = raw?.trim().orEmpty()
    if (value.isEmpty()) return null
    if (value.startsWith("http://", ignoreCase = true) ||
        value.startsWith("https://", ignoreCase = true) ||
        value.startsWith("data:", ignoreCase = true)
    ) {
        return value
    }
    return baseUrl.trimEnd('/') + "/" + value.trimStart('/')
}

/**
 * A directory row without an orgSlug or storeCode cannot be opened, so it is
 * dropped rather than rendered as a card that goes nowhere. Both are nullable
 * on the wire only because the organization relation is.
 */
internal fun StoreDirectoryEntryDto.toModelOrNull(baseUrl: String): ShopSummary? {
    val slug = orgSlug?.takeIf { it.isNotBlank() } ?: return null
    val code = storeCode?.takeIf { it.isNotBlank() } ?: return null
    return ShopSummary(
        ref = StoreRef(slug, code),
        name = name,
        businessMode = BusinessMode.fromWire(businessMode),
        businessTypeLabel = businessTypeLabel?.takeIf { it.isNotBlank() },
        address = address,
        imageUrl = absoluteUrl(baseUrl, imageUrl),
        lat = lat,
        lng = lng,
        productCount = productCount,
        distanceKm = distanceKm,
    )
}

internal fun CatalogDto.toShopEntity(storeKey: String, fetchedAtEpochMs: Long) = CachedShopEntity(
    storeKey = storeKey,
    name = store.name,
    businessTypeLabel = store.businessTypeLabel?.takeIf { it.isNotBlank() },
    ownerName = store.ownerName?.takeIf { it.isNotBlank() },
    address = store.address?.takeIf { it.isNotBlank() },
    fetchedAtEpochMs = fetchedAtEpochMs,
)

internal fun CatalogCategoryDto.toEntity(storeKey: String, position: Int) = CachedCategoryEntity(
    storeKey = storeKey,
    categoryId = id,
    name = name,
    position = position,
)

internal fun CatalogProductDto.toEntity(storeKey: String, position: Int, baseUrl: String) =
    CachedProductEntity(
        storeKey = storeKey,
        productId = id,
        categoryId = categoryId,
        sku = sku,
        barcode = barcode,
        name = name,
        priceCents = priceCents,
        compareAtPriceCents = compareAtPriceCents,
        taxRate = taxRate,
        kind = kind,
        imageUrl = absoluteUrl(baseUrl, imageUrl),
        unitLabel = unitLabel?.takeIf { it.isNotBlank() },
        stockQty = stockQty,
        lowStockThreshold = lowStockThreshold,
        position = position,
    )

internal fun CachedShopEntity.toModel() = Shop(
    name = name,
    businessTypeLabel = businessTypeLabel,
    ownerName = ownerName,
    address = address,
)

internal fun CachedCategoryEntity.toModel() = Category(id = categoryId, name = name)

internal fun CachedProductEntity.toModel() = Product(
    id = productId,
    categoryId = categoryId,
    sku = sku,
    barcode = barcode,
    name = name,
    priceCents = priceCents,
    compareAtPriceCents = compareAtPriceCents,
    taxRate = taxRate,
    kind = if (kind == "weighted") ProductKind.Weighted else ProductKind.Standard,
    imageUrl = imageUrl,
    unitLabel = unitLabel,
    stockQty = stockQty,
    lowStockThreshold = lowStockThreshold,
)
