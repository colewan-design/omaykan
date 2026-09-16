package com.omaykan.seller.core.data

import com.omaykan.seller.core.model.Catalog
import com.omaykan.seller.core.model.ProductDraft
import com.omaykan.seller.core.network.dto.SyncEventDto
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlin.math.abs

/**
 * The sync events that turn a product form into the server's catalog.
 *
 * ## Why every field goes back
 *
 * `SyncController::applyProductEvent` is an `updateOrCreate` that falls back to
 * a default for any key it is not sent: a missing `sku` becomes null, a missing
 * `taxRate` becomes 12, a missing `trackInventory` becomes true. The till always
 * sends the whole product, so this never bit it — but a phone form that edits
 * four fields and sent only those four would wipe the barcode the register
 * scans. So an edit starts from the product exactly as the server described it
 * and changes only what the merchant changed.
 *
 * ## Why stock is an adjustment
 *
 * On an existing product the server ignores `stockQty`: stock only moves
 * through inventory adjustments, which is what keeps the till's ledger
 * traceable. The merchant types the count they see on the shelf; this sends
 * the difference, as the same "manual correction" the register records when
 * somebody counts.
 *
 * Pure and internal so the rules above are tested rather than trusted.
 */
internal fun productEvents(
    catalog: Catalog,
    draft: ProductDraft,
    newId: () -> String,
    now: String,
): List<SyncEventDto> {
    val existing = draft.productId?.let { id -> catalog.products.firstOrNull { it.id == id } }
    val productId = existing?.id ?: newId()
    val tracked = existing?.trackInventory ?: true

    val payload = buildJsonObject {
        put("name", draft.name.trim())
        put("categoryId", draft.categoryId)
        put("priceCents", draft.priceCents)
        put("isActive", draft.active)
        put("trackInventory", tracked)

        if (existing != null) {
            put("sku", existing.sku)
            put("barcode", existing.barcode)
            put("productType", existing.productType)
            existing.taxRate?.let { put("taxRate", it) }
            // Omitted when empty, so the server keeps what it has — or, for a
            // product that somehow has none, falls back to the shop's own mode
            // rather than being listed nowhere.
            if (existing.businessModes.isNotEmpty()) {
                putJsonArray("businessModes") { existing.businessModes.forEach { add(it) } }
            }
        } else {
            // Read only when the inventory row is created, which for a new
            // product is now. No businessModes: the server fills in the store's
            // own, which is what a single-mode shop means.
            put("stockQty", draft.stockQty ?: 0.0)
        }

        // Present means "set the branch's reorder level", null included — an
        // emptied field is the merchant asking for the till's default back.
        if (tracked) put("lowStockThreshold", draft.lowStockThreshold)
    }

    val events = mutableListOf(
        SyncEventDto(
            id = newId(),
            entityType = "product",
            entityId = productId,
            operation = if (existing == null) "create" else "update",
            occurredAt = now,
            payload = payload,
        ),
    )

    val delta = if (existing != null && tracked) {
        draft.stockQty?.let { it - (existing.stockQty ?: 0.0) }
    } else {
        null
    }

    if (delta != null && abs(delta) > 1e-9) {
        events += SyncEventDto(
            id = newId(),
            entityType = "inventory_adjustment",
            entityId = newId(),
            operation = "create",
            occurredAt = now,
            payload = buildJsonObject {
                put("productId", productId)
                put("quantityDelta", delta)
                put("adjustmentType", "manual_correction")
                put("reason", "Counted on the Omaykan Seller app")
            },
        )
    }

    return events
}
