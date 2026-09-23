package com.omaykan.storefront.core.network.dto

import kotlinx.serialization.Serializable

/*
 * Wire shapes, one per response body, mirroring the Laravel controllers exactly.
 * Nothing outside core/network and core/data may reference these types — the
 * repositories map them into core/model, so a catalog shape change stays one
 * file's problem.
 *
 * Every field the server can omit or null is optional here. The catalog is the
 * hot path on a phone with one bar of signal; a strict decoder that throws on a
 * missing unitLabel fails the whole screen for a field nobody renders.
 */

@Serializable
data class StoreDirectoryDto(
    val stores: List<StoreDirectoryEntryDto> = emptyList(),
)

@Serializable
data class StoreDirectoryEntryDto(
    val orgSlug: String? = null,
    val storeCode: String? = null,
    val name: String = "",
    val businessMode: String? = null,
    val businessTypeLabel: String? = null,
    val address: String = "",
    val imageUrl: String? = null,
    val lat: Double? = null,
    val lng: Double? = null,
    val productCount: Int = 0,
    val distanceKm: Double? = null,
)

@Serializable
data class CatalogDto(
    val store: CatalogShopDto = CatalogShopDto(),
    val categories: List<CatalogCategoryDto> = emptyList(),
    val products: List<CatalogProductDto> = emptyList(),
)

@Serializable
data class CatalogShopDto(
    val name: String = "",
    val businessTypeLabel: String? = null,
    val ownerName: String? = null,
    val address: String? = null,
)

@Serializable
data class CatalogCategoryDto(
    val id: String,
    val name: String = "",
)

@Serializable
data class CatalogProductDto(
    val id: String,
    val categoryId: String = "uncategorized",
    val sku: String = "",
    val barcode: String = "",
    val name: String = "",
    val priceCents: Long = 0,
    val compareAtPriceCents: Long? = null,
    val taxRate: Double = 0.0,
    /** "standard" or "weighted". */
    val kind: String = "standard",
    val imageUrl: String? = null,
    val unitLabel: String? = null,
    val businessModes: List<String> = emptyList(),
    val outOfStock: Boolean = false,
    val stockQty: Double? = null,
    val lowStockThreshold: Double? = null,
)

/** The Laravel error envelope: message, plus errors keyed by field path. */
@Serializable
data class ApiErrorDto(
    val message: String? = null,
    val errors: Map<String, List<String>> = emptyMap(),
)

/**
 * GET /api/app-releases/{slug} — the update check that replaces the Firestore
 * appReleases doc.
 *
 * A 404 means nothing is published, which is a real answer and not a failure:
 * the app is as current as anything it could be told about. The server says
 * nothing about whether an update is *needed* — it has no idea what is
 * installed on the phone asking — so the comparison against
 * PackageInfo.longVersionCode happens here.
 */
@Serializable
data class AppReleaseDto(
    val slug: String = "",
    val versionCode: Long = 0,
    val versionName: String = "",
    val apkUrl: String? = null,
    val notes: String? = null,
    val publishedAt: String? = null,
)
