package com.omaykan.storefront.core.model

/**
 * One line of a basket: a product from the cached shelf, and how many.
 *
 * The product is resolved fresh each time rather than copied in when it was
 * added, so a line always shows the shop's current price. A cart holding its
 * own idea of what something cost is a cart that disagrees with the counter.
 */
data class CartLine(
    val product: Product,
    val quantity: Double,
) {
    val lineTotalCents: Long get() = Math.round(product.priceCents * quantity)
}

data class Cart(
    val ref: StoreRef,
    val lines: List<CartLine> = emptyList(),
    /**
     * Ids that are in the basket but no longer on the shelf — sold out, or the
     * shop took them down. Surfaced rather than dropped silently: someone who
     * put something in a basket deserves to be told it went, not to arrive at
     * checkout with a total that quietly shrank.
     */
    val unavailableIds: List<String> = emptyList(),
) {
    val isEmpty: Boolean get() = lines.isEmpty()

    /** Whole items, for the badge. A weighed line counts once. */
    val itemCount: Int get() = lines.size

    /**
     * What the phone thinks this costs.
     *
     * An estimate, and named one everywhere it is shown. Prices, tax and any
     * delivery fee are recomputed server-side from the merchant's own records
     * at checkout — nothing the client says about money is trusted, and the
     * confirmation renders the server's numbers, not these.
     */
    val estimatedSubtotalCents: Long get() = lines.sumOf { it.lineTotalCents }
}
