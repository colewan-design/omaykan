package com.omaykan.rider.core.data

import com.omaykan.rider.core.model.DeliveryAssignment
import com.omaykan.rider.core.model.DeliveryOffer
import com.omaykan.rider.core.model.DeliveryStage
import com.omaykan.rider.core.network.ApiCaller
import com.omaykan.rider.core.network.RiderApi
import com.omaykan.rider.core.network.dto.AdvanceRequestDto
import javax.inject.Inject
import javax.inject.Singleton

/** The board and this rider's own work, fetched together. */
data class Work(
    val board: List<DeliveryOffer>,
    val active: List<DeliveryAssignment>,
    val completed: List<DeliveryAssignment>,
)

/**
 * What a rider can see and do about deliveries.
 *
 * Every write answers with the whole order rather than an acknowledgement, and
 * the feed puts that answer straight onto the screen in place of the row it
 * had. That is what keeps a card honest after a tap: accepting also stamps the
 * accepted-at time and fills in the address and the customer's number, which
 * the board row never carried at all.
 */
@Singleton
class DeliveryRepository @Inject constructor(
    private val api: RiderApi,
    private val caller: ApiCaller,
) {
    /**
     * Both lists, in one go.
     *
     * Two calls rather than one, because they are two endpoints — but always
     * issued together and never separately, because every action moves a job
     * between them. Accepting takes one off the board and puts it in `active`;
     * releasing does the reverse. Refreshing only one of the two is how a
     * screen ends up showing the same job in both places.
     *
     * Sequential rather than concurrent, deliberately. On a phone that has just
     * come back into signal, two parallel requests are two chances to time out
     * and a partial answer to reconcile; here, the first failure aborts the
     * whole read and the caller keeps the last good pair. The board is capped
     * at 40 rows server-side, so there is nothing to gain by racing them.
     */
    suspend fun fetch(): Work = caller.call {
        val board = api.board().orders.map { it.toModel() }
        val mine = api.mine()

        Work(
            board = board,
            active = mine.active.map { it.toModel() },
            completed = mine.completed.map { it.toModel() },
        )
    }

    /**
     * Claim a job.
     *
     * Throws [com.omaykan.rider.core.network.ApiException.Taken] on the 409 when
     * another rider got there first — the normal outcome of two riders tapping
     * the same row, not a fault worth an error banner.
     */
    suspend fun accept(orderId: String): DeliveryAssignment =
        caller.call { api.accept(orderId) }.order.toModel()

    /**
     * Move a job one step along the road.
     *
     * Never retried automatically. The server checks the legal transition and
     * refuses a repeat with a 422, so a retry cannot skip a stage — but it can
     * turn a timeout on `delivered` into a customer's tracking page jumping
     * while the rider is still deciding whether the first tap worked. If it
     * fails, the rider taps again.
     */
    suspend fun advance(orderId: String, to: DeliveryStage): DeliveryAssignment =
        caller.call { api.advance(orderId, AdvanceRequestDto(to.wire)) }.order.toModel()

    /**
     * Give a job back to the board.
     *
     * Answers `{released: true}` rather than an order, because there is no
     * longer an order that belongs to this rider to describe. The feed drops
     * the row and refetches.
     */
    suspend fun release(orderId: String) {
        caller.call { api.release(orderId) }
    }
}
