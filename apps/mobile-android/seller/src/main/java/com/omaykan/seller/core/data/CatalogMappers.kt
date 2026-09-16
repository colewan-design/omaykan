package com.omaykan.seller.core.data

import com.omaykan.seller.core.model.Catalog
import com.omaykan.seller.core.model.Category
import com.omaykan.seller.core.model.ConversationSummary
import com.omaykan.seller.core.model.ConversationThread
import com.omaykan.seller.core.model.Message
import com.omaykan.seller.core.model.OrderStatus
import com.omaykan.seller.core.model.Product
import com.omaykan.seller.core.model.StaffRole
import com.omaykan.seller.core.model.ThreadOrder
import com.omaykan.seller.core.network.dto.BootstrapDto
import com.omaykan.seller.core.network.dto.ConversationMessageDto
import com.omaykan.seller.core.network.dto.ConversationSummaryDto
import com.omaykan.seller.core.network.dto.ConversationThreadDto
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

/**
 * A number Laravel may have sent as a number or as a string.
 *
 * Decimal casts serialise as strings ("5.000"); an integer column comes back
 * as whatever the database driver hands PHP. Both read the same here, and
 * anything unreadable is null rather than a crash.
 */
internal fun JsonElement?.asDouble(): Double? =
    (this as? JsonPrimitive)?.content?.toDoubleOrNull()

/**
 * The catalog, as this branch sells it.
 *
 * [baseUrl] makes a relative image path absolute: Coil cannot load
 * "/storage/products/x.jpg" on its own, and a product photo is the one thing
 * on the products screen a merchant recognises before reading.
 */
internal fun BootstrapDto.toCatalog(baseUrl: String): Catalog {
    val overrides = catalog.overrides.associateBy { it.productId }
    val levels = catalog.inventoryLevels.associateBy { it.productId }

    return Catalog(
        organizationId = organization?.id.orEmpty(),
        storeId = store?.id.orEmpty(),
        userName = user?.fullName?.takeIf { it.isNotBlank() },
        role = StaffRole.fromWire(user?.roleId),
        categories = catalog.categories
            .map {
                Category(
                    id = it.id,
                    name = it.name?.takeIf { name -> name.isNotBlank() } ?: "Unnamed category",
                    sortOrder = it.sortOrder.asDouble()?.toInt() ?: 0,
                )
            }
            .sortedBy { it.sortOrder },
        products = catalog.products.map { dto ->
            val level = levels[dto.id]
            val override = overrides[dto.id]

            Product(
                id = dto.id,
                name = dto.name?.takeIf { it.isNotBlank() } ?: "Unnamed product",
                categoryId = dto.categoryId?.takeIf { it.isNotBlank() },
                sku = dto.sku,
                barcode = dto.barcode,
                productType = dto.productType?.takeIf { it.isNotBlank() } ?: "standard",
                taxRate = dto.taxRate.asDouble(),
                priceCents = dto.priceCents.asDouble()?.toLong() ?: 0L,
                trackInventory = dto.trackInventory,
                active = dto.isActive,
                businessModes = dto.businessModes.orEmpty(),
                unitLabel = dto.unitLabel?.takeIf { it.isNotBlank() },
                imageUrl = dto.imageUrl?.takeIf { it.isNotBlank() }?.let { absoluteUrl(it, baseUrl) },
                // No inventory row yet reads as nothing on the shelf — the
                // storefront catalog makes the same call (`?? 0`) and hides
                // the product, so the phone must not claim otherwise.
                stockQty = if (dto.trackInventory) level?.qtyOnHand.asDouble() ?: 0.0 else null,
                lowStockThreshold = level?.reorderLevel.asDouble() ?: dto.lowStockThreshold.asDouble(),
                branchPriceCents = override?.priceCents.asDouble()?.toLong(),
                branchAvailable = override?.isAvailable,
            )
        },
    )
}

/** A server path made loadable: "/api/stores/…/image" → "https://omaykan.com/api/stores/…/image". */
internal fun absoluteUrl(url: String, baseUrl: String): String = when {
    url.startsWith("http://") || url.startsWith("https://") || url.startsWith("data:") -> url
    url.startsWith("/") -> baseUrl.trimEnd('/') + url
    else -> baseUrl.trimEnd('/') + "/" + url
}

// -- Conversations --------------------------------------------------------

/** `Conversation::SENDER_STORE`. */
private const val SENDER_STORE = "store"

internal fun ConversationSummaryDto.toModel() = ConversationSummary(
    id = id,
    customerName = customer?.name?.takeIf { it.isNotBlank() } ?: "Customer",
    lastMessage = lastMessage?.toModel(),
    lastMessageAt = lastMessageAt,
    unreadCount = unreadCount,
)

internal fun ConversationMessageDto.toModel() = Message(
    id = (id as? JsonPrimitive)?.content.orEmpty(),
    fromStore = from == SENDER_STORE,
    body = body,
    orderTicket = order?.ticketNumber?.takeIf { it.isNotBlank() },
    createdAt = createdAt,
    authorName = authorName?.takeIf { it.isNotBlank() },
)

internal fun ConversationThreadDto.toModel() = ConversationThread(
    summary = conversation.toModel(),
    messages = messages.map { it.toModel() },
    recentOrders = recentOrders.map {
        ThreadOrder(
            id = it.id,
            ticketNumber = it.ticketNumber?.takeIf { ticket -> ticket.isNotBlank() },
            status = OrderStatus.fromWire(it.status),
            totalCents = it.totalCents,
            createdAt = it.createdAt,
        )
    },
)
