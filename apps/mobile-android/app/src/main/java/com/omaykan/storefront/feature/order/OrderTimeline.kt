package com.omaykan.storefront.feature.order

import com.omaykan.storefront.core.model.TrackedOrder

/**
 * How far along one step of an order is.
 *
 * Exactly one step in a list is [Current] — it is the headline of the tracking
 * screen, the answer to "where is my order" — and the rest are behind or ahead
 * of it.
 */
internal enum class StepState { Done, Current, Pending }

internal data class TrackStep(
    val label: String,
    /** Said only under the step in force; a finished step needs no commentary. */
    val detail: String?,
    val state: StepState,
)

/**
 * The order's journey, as a list of steps with one of them lit.
 *
 * Kept out of the composable and off `TrackedOrder` on purpose. It is the one
 * piece of this screen that can be wrong in a way the shopper would act on —
 * somebody standing at a door reading "On the way" believes a rider is holding
 * their food — so it is a plain function over the two wire fields, and it is
 * tested.
 *
 * Both fields are read, because the server answers two different questions:
 * `order_status` is how far the shop has got (preparing → ready → served →
 * completed), and `delivery_stage` is where the order is once it has left
 * (pending → assigned → picked_up → delivered). Either can run ahead of the
 * other — a shop that never marks a ticket ready while a rider is already
 * carrying it, a till closed out after the fact — so the step reached is the
 * furthest of the two. A progress rail that goes backwards is worse than one
 * that is generous.
 */
internal fun trackSteps(order: TrackedOrder): List<TrackStep> {
    if (order.cancelled) {
        return listOf(
            TrackStep(
                label = "Cancelled",
                detail = "This order was called off. Nothing was charged.",
                state = StepState.Current,
            ),
        )
    }

    val labels: List<Pair<String, String?>>
    val reached: Int

    if (order.isDelivery) {
        labels = listOf(
            "Order placed" to null,
            "Preparing" to "The shop is putting your order together.",
            "Ready" to "Packed, and waiting for a rider.",
            "On the way" to when (order.deliveryStage) {
                "picked_up" -> "The rider has your order."
                else -> "A rider is on their way to the shop."
            },
            "Delivered" to "Handed over at your address.",
        )
        // `assigned` and `picked_up` both count as on the way, matching
        // TrackedOrder.stage — one vocabulary, so the Orders tab and this
        // screen can never describe the same order differently.
        val byStatus = when (order.status) {
            "completed" -> 4
            "ready", "served" -> 2
            else -> 1
        }
        val byStage = when (order.deliveryStage) {
            "delivered" -> 4
            "picked_up", "assigned" -> 3
            else -> 0
        }
        reached = maxOf(byStatus, byStage)
    } else {
        labels = listOf(
            "Order placed" to null,
            "Preparing" to "The shop is putting your order together.",
            "Ready for pick-up" to "Show your ticket number at the counter.",
            "Picked up" to "Collected. Thank you.",
        )
        reached = when (order.status) {
            "completed" -> 3
            "ready", "served" -> 2
            else -> 1
        }
    }

    return labels.mapIndexed { index, (label, detail) ->
        TrackStep(
            label = label,
            detail = detail.takeIf { index == reached },
            state = when {
                index < reached -> StepState.Done
                index == reached -> StepState.Current
                else -> StepState.Pending
            },
        )
    }
}
