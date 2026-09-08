package com.omaykan.rider.core.data

import com.omaykan.rider.core.model.DeliveryAssignment
import com.omaykan.rider.core.model.DeliveryOffer
import com.omaykan.rider.core.model.DeliveryStage
import com.omaykan.rider.core.network.ApiException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

/**
 * What is available and what this rider is carrying, right now.
 *
 * Not a snapshot somebody asked for — a live reading that keeps itself current
 * for as long as anyone is watching it.
 */
data class WorkState(
    val board: List<DeliveryOffer> = emptyList(),
    val active: List<DeliveryAssignment> = emptyList(),
    val completed: List<DeliveryAssignment> = emptyList(),
    /** True until the first answer arrives, good or bad. */
    val loading: Boolean = true,
    /**
     * The last fetch that failed, if the one after it has not yet succeeded.
     *
     * The jobs beside it are the last good ones. That pairing is the whole
     * point: a rider halfway to a shop must be told the screen has stopped
     * updating, and must not have the address taken away from them.
     */
    val error: String? = null,
)

/**
 * The one place this app asks the server what is happening.
 *
 * ## Why polling
 *
 * For the same reason :seller polls, and one more. The backend broadcasts order
 * events on a private `store.{id}` channel and a public `order.{uuid}` one, and
 * `routes/channels.php` authorizes the private one on a row in
 * `store_memberships` keyed by user id. A rider has no membership row anywhere —
 * they are not attached to a shop at all — so there is no channel for them to
 * listen to. There is no `riders` channel and no "new job" broadcast to put on
 * one; a board is a query, not an event.
 *
 * So this polls every fifteen seconds — well inside the API's limits, and fast
 * enough that a job appears while it is still worth taking. Making it a push
 * feed is a backend change, not an app one, and it is written up in
 * rider/README.md.
 *
 * ## The generation counter
 *
 * A tap that changes a job has to show at once, not at the end of the interval,
 * and the reply to that write is the most current reading of the job that
 * exists. So actions write their result straight into [state].
 *
 * The hazard is a fetch that was already in flight when they did: it started
 * before the write and its answer is older, so publishing it would put a taken
 * job back on the board for up to fifteen seconds — long enough for a second
 * rider to tap it and lose. [writes] is bumped by every action; the loop reads
 * it before fetching and discards any answer that comes back into a changed
 * one, then immediately refetches. The cost of being wrong is one extra
 * request; the cost of not doing it is a board that lies.
 */
@Singleton
class WorkFeed @Inject constructor(
    private val repository: DeliveryRepository,
    @AppScope private val scope: CoroutineScope,
) {
    private companion object {
        const val POLL_INTERVAL_MS = 15_000L

        /**
         * How long the loop keeps running after the last watcher leaves.
         *
         * Long enough to ride out a rotation or a trip into the recents list
         * without tearing down and re-fetching, short enough that a rider who
         * really has put the phone away is not still being polled for.
         */
        const val LINGER_MS = 5_000L
    }

    /** Manual refreshes. Conflated: three impatient pulls are one fetch. */
    private val ticks = Channel<Unit>(Channel.CONFLATED)

    /** Bumped by every write. See the class comment. */
    private val writes = AtomicLong(0)

    private val current = MutableStateFlow(WorkState())

    /**
     * Drives the polling.
     *
     * The flow's own emissions are ignored — [current] is the single mutable
     * cell that both this loop and the write path put answers into, which is
     * what lets a write survive a fetch that was already running. This flow
     * exists to own the loop's *lifetime*, and it is combined into [state] so
     * that subscribing to the jobs is what starts fetching them. A screen
     * cannot hold the list without holding the loop, or the loop without the
     * list.
     *
     * The one `emit` before the loop is load-bearing: `combine` produces
     * nothing until every input has emitted once, so without it the screen
     * would show no state at all until the first fetch returned.
     */
    private val polling: Flow<Unit> = flow {
        emit(Unit)

        while (true) {
            val generation = writes.get()

            try {
                val work = repository.fetch()

                // Discarded if anything was written while this was in the air.
                // The `continue` skips the wait as well as the publish: the
                // refetch is wanted now, not in fifteen seconds.
                if (writes.get() != generation) continue

                current.value = WorkState(
                    board = work.board,
                    active = work.active,
                    completed = work.completed,
                    loading = false,
                    error = null,
                )
            } catch (e: ApiException) {
                // The last good lists are kept. See WorkState.error.
                //
                // A Gated exception reaches here too and is deliberately shown
                // like any other: the screen is about to be replaced by the
                // status screen anyway, because whichever action or refresh
                // surfaced it has already handed it to SessionRepository.
                current.value = current.value.copy(loading = false, error = e.message)
            }

            // Either the interval elapses or somebody pulled to refresh.
            withTimeoutOrNull(POLL_INTERVAL_MS) { ticks.receive() }
        }
    }

    /**
     * The jobs, and the loop that keeps them current, as one thing.
     *
     * `WhileSubscribed` is what makes this stop: a rider who puts the phone in
     * a pocket has a screen that is no longer collecting, and fifteen seconds
     * of polling a job board on mobile data every fifteen seconds is a battery
     * cost with nothing on the other side of it. The linger rides out a
     * rotation without tearing the loop down and re-fetching.
     *
     * It restarts where it left off rather than blank: [current] holds the last
     * good lists, so a rider coming back to the app sees the board they had
     * while the first fetch is in the air.
     */
    val state: StateFlow<WorkState> = combine(current, polling) { work, _ -> work }
        .stateIn(
            scope = scope,
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis = LINGER_MS),
            initialValue = WorkState(),
        )

    /** Fetch now rather than at the end of the interval. */
    fun refresh() {
        ticks.trySend(Unit)
    }

    /**
     * A job this rider has just claimed.
     *
     * Off the board and into `active` in one move, because that is what the
     * server did. Keeping the two edits together is what stops a frame where
     * the job is in both lists — or, worse, neither.
     */
    fun claimed(assignment: DeliveryAssignment) = write {
        current.value = current.value.let { state ->
            state.copy(
                board = state.board.filterNot { it.id == assignment.id },
                active = state.active + assignment,
            )
        }
    }

    /**
     * A job this rider has moved along.
     *
     * A delivered job leaves `active` for `completed` — the server's `mine`
     * splits on exactly that stage, so a screen that left it in place would
     * disagree with the next fetch by fifteen seconds.
     */
    fun advanced(assignment: DeliveryAssignment) = write {
        current.value = current.value.let { state ->
            if (assignment.stage == DeliveryStage.Delivered) {
                state.copy(
                    active = state.active.filterNot { it.id == assignment.id },
                    completed = listOf(assignment) + state.completed,
                )
            } else {
                state.copy(
                    active = state.active.map { if (it.id == assignment.id) assignment else it },
                )
            }
        }
    }

    /**
     * A job handed back.
     *
     * Dropped from `active` and *not* put back on the board, even though that
     * is where it has gone. The offer the board shows is the server's, and this
     * app never held one for a job it had claimed — an assignment carries the
     * customer's name and address, and a board row must not. The refetch this
     * triggers brings back the real offer a moment later.
     */
    fun released(orderId: String) = write {
        current.value = current.value.let { state ->
            state.copy(active = state.active.filterNot { it.id == orderId })
        }
    }

    /**
     * Forget everything and start over.
     *
     * Called when the rider signs out. Without it, the next person to sign in
     * on this phone would see the previous one's active deliveries for as long
     * as the first fetch takes — with the addresses and phone numbers on them.
     */
    fun reset() = write {
        current.value = WorkState()
    }

    private inline fun write(block: () -> Unit) {
        writes.incrementAndGet()
        block()
        refresh()
    }
}
