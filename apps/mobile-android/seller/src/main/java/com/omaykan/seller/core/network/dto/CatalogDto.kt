package com.omaykan.seller.core.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/*
 * The till's catalog endpoints, read by a phone.
 *
 * `GET /api/sync/bootstrap` serialises Eloquent models as they are, so these
 * keys are snake_case where every other payload this app reads is camelCase.
 * Numbers that Laravel casts to `decimal` — tax rate, stock on hand, the
 * reorder level — arrive as *strings* ("12.00", "5.000"), and whether an
 * integer column arrives as a number or a string depends on the database
 * driver. Those fields are read as raw JSON and parsed in the mapper, so one
 * driver's habit never costs the merchant their product list.
 */

/** `GET /api/sync/bootstrap`, as far as this app reads it. */
@Serializable
data class BootstrapDto(
    val organization: BootstrapOrganizationDto? = null,
    val store: BootstrapStoreDto? = null,
    val user: BootstrapUserDto? = null,
    val catalog: BootstrapCatalogDto = BootstrapCatalogDto(),
)

@Serializable
data class BootstrapOrganizationDto(
    val id: String,
    val name: String? = null,
    val slug: String? = null,
)

@Serializable
data class BootstrapStoreDto(
    val id: String,
    val name: String? = null,
    val code: String? = null,
)

@Serializable
data class BootstrapUserDto(
    val id: String? = null,
    val fullName: String? = null,
    /** 'admin', 'manager' or 'cashier' — StoreContext::$role. */
    val roleId: String? = null,
)

@Serializable
data class BootstrapCatalogDto(
    val categories: List<CategoryDto> = emptyList(),
    val products: List<ProductDto> = emptyList(),
    val overrides: List<ProductOverrideDto> = emptyList(),
    val inventoryLevels: List<InventoryLevelDto> = emptyList(),
)

@Serializable
data class CategoryDto(
    val id: String,
    val name: String? = null,
    @SerialName("sort_order") val sortOrder: JsonElement? = null,
)

@Serializable
data class ProductDto(
    val id: String,
    val name: String? = null,
    @SerialName("category_id") val categoryId: String? = null,
    val sku: String? = null,
    val barcode: String? = null,
    @SerialName("product_type") val productType: String? = null,
    @SerialName("tax_rate") val taxRate: JsonElement? = null,
    @SerialName("price_cents") val priceCents: JsonElement? = null,
    @SerialName("track_inventory") val trackInventory: Boolean = true,
    @SerialName("is_active") val isActive: Boolean = true,
    @SerialName("business_modes") val businessModes: List<String>? = null,
    @SerialName("image_url") val imageUrl: String? = null,
    @SerialName("unit_label") val unitLabel: String? = null,
    @SerialName("low_stock_threshold") val lowStockThreshold: JsonElement? = null,
)

/** A branch's own price or availability for one product. Null means "inherit". */
@Serializable
data class ProductOverrideDto(
    @SerialName("product_id") val productId: String,
    @SerialName("price_cents") val priceCents: JsonElement? = null,
    @SerialName("is_available") val isAvailable: Boolean? = null,
)

@Serializable
data class InventoryLevelDto(
    @SerialName("product_id") val productId: String,
    @SerialName("qty_on_hand") val qtyOnHand: JsonElement? = null,
    @SerialName("reorder_level") val reorderLevel: JsonElement? = null,
)

/**
 * `POST /api/sync/push` — the till's outbox, which is also how a product is
 * created or changed. There is no separate product endpoint; every catalog
 * write on this platform is an event in this shape, so the phone writes the
 * same events the register does and the server applies them the same way.
 */
@Serializable
data class SyncPushRequestDto(
    val organizationId: String,
    val storeId: String,
    val events: List<SyncEventDto>,
)

@Serializable
data class SyncEventDto(
    /** A fresh UUID per event: the server's idempotency key is built on it. */
    val id: String,
    val entityType: String,
    val entityId: String,
    val operation: String,
    val occurredAt: String,
    val payload: JsonObject,
)

@Serializable
data class SyncPushResponseDto(
    val results: List<SyncResultDto> = emptyList(),
)

/**
 * One event's fate: "applied", "duplicate" or "failed".
 *
 * A failed event is still a 200 for the request as a whole — the till's outbox
 * pushes a batch and retries the failures — so the phone has to read this to
 * know whether its save happened at all.
 */
@Serializable
data class SyncResultDto(
    val eventId: String = "",
    val status: String = "",
    val message: String? = null,
)
