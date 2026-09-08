package com.omaykan.seller.core.notify

import com.omaykan.seller.core.model.SellerOrder

/**
 * Which of these orders the merchant has not seen before.
 *
 * A pure function of "ids I have shown" and "orders the server just sent", kept
 * out of the view model because it has two rules that are easy to get wrong and
 * worth testing on their own:
 *
 *  1. **The first fetch announces nothing.** Opening the app to a shop's last
 *     hundred orders must not fire a hundred alerts, or one saying "97 new
 *     orders". The first list seen is the baseline, not news.
 *  2. **Only genuinely new ids count.** An order that changed — a rider
 *     assigned, a payment settled — is not a new order, and the merchant
 *     usually caused the change themselves.
 *
 * Deliberately in memory and not on disk. This answers "what arrived while I
 * was watching", and a process that has been killed and restarted was not
 * watching. Persisting the seen set would mean an app reopened after two hours
 * greeting the merchant with alerts for orders they have already dealt with at
 * the till.
 */
class NewOrderWatch {
    private var seen: Set<String>? = null

    /**
     * Record [orders] as seen, and return the ones that are new.
     *
     * Empty on the first call, whatever it contains.
     */
    fun arrivals(orders: List<SellerOrder>): List<SellerOrder> {
        val ids = orders.map { it.id }.toSet()
        val previous = seen

        seen = ids

        if (previous == null) return emptyList()

        return orders.filter { it.id !in previous }
    }

    /**
     * Forget everything, so the next list becomes a new baseline.
     *
     * Called when the shop changes under the app — signing out and signing in
     * somewhere else. Without it, the first fetch for the new shop would read
     * as a hundred arrivals, since none of those ids were in the old set.
     */
    fun reset() {
        seen = null
    }
}
