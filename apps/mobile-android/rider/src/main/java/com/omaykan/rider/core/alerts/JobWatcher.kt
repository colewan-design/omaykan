package com.omaykan.rider.core.alerts

import com.omaykan.rider.core.data.DeliveryRepository
import com.omaykan.rider.core.model.DeliveryOffer
import com.omaykan.rider.core.network.ApiException
import kotlinx.coroutines.delay
import javax.inject.Inject

/**
 * The loop that notices a job appearing.
 *
 * Separate from the service so the rule that matters — *what counts as new* —
 * is testable without starting an Android component. Everything about polling
 * cadence and Android notifications lives on the other side of [onNewJobs].
 *
 * ## Why polling, and why this is the cheap version
 *
 * The README is blunt that push is the right answer and that this is not it:
 * FCM needs a device-token registry the backend does not have and a "job
 * posted" event that does not exist — today there is only `OrderPlaced` on a
 * store channel, which no rider is subscribed to. What exists is
 * `GET /api/rider/board`, so this asks it on a timer from a foreground service
 * the rider opted into and can see.
 *
 * That has one honest consequence worth stating: alerts stop when the service
 * does, and the service does not survive a reboot. This narrows the gap between
 * "app open" and "nothing at all"; it does not close it.
 *
 * ## The first poll never notifies
 *
 * See [NewJobFilter], which owns that rule and is where it is tested.
 */
class JobWatcher @Inject constructor(
    private val deliveries: DeliveryRepository,
) {
    private companion object {
        /**
         * Slower than the board's own 15s refresh, on purpose.
         *
         * That one runs while a rider is looking at the screen and can afford
         * to be quick. This one runs with the phone in a pocket, and the
         * difference between hearing about a job at once and hearing about it
         * within a minute is not worth the battery on a cheap phone at the end
         * of a shift.
         */
        const val INTERVAL_MS = 60_000L

        /**
         * After a failure. Deliberately not exponential: a rider in a dead spot
         * comes back out of it, and a backoff that had crept to eight minutes
         * would keep them deaf for eight more once they did.
         */
        const val RETRY_MS = 90_000L
    }

    private val fresh = NewJobFilter()

    /**
     * Poll until cancelled, calling [onNewJobs] with jobs that were not on the
     * previous board.
     *
     * Never throws. A watcher that dies on one failed request is a watcher that
     * silently stops working the first time a rider rides into a tunnel.
     */
    suspend fun watch(onNewJobs: (List<DeliveryOffer>) -> Unit) {
        while (true) {
            val wait = try {
                val appeared = fresh.newIn(deliveries.board())
                if (appeared.isNotEmpty()) onNewJobs(appeared)

                INTERVAL_MS
            } catch (e: ApiException) {
                /*
                 * Including the approval gate's 403. A rider suspended mid-shift
                 * keeps this service alive and quiet rather than being told
                 * about work they cannot take — the app's own session handling
                 * moves them to the status screen, and stopping the service is
                 * that screen's job, not this loop's.
                 */
                RETRY_MS
            } catch (e: Exception) {
                RETRY_MS
            }

            delay(wait)
        }
    }
}

/**
 * What counts as new.
 *
 * Pulled out of the loop so it can be tested without a repository, a service or
 * a clock — it is the only part of this feature that decides anything, and it
 * has two failure modes that both end with a rider switching alerts off:
 * announcing the whole board the first time, or announcing the same job every
 * minute until somebody takes it.
 *
 * Not thread-safe, and does not need to be: one watcher loop owns one of these
 * and calls it from a single coroutine.
 */
internal class NewJobFilter {

    /**
     * Board ids already reported.
     *
     * Replaced with whatever the board currently holds on every pass rather
     * than grown forever: an id that has left the board has been taken by
     * somebody, and if it ever comes back — released, or unassigned by a shop —
     * it is genuinely news again.
     */
    private var seen: Set<String> = emptySet()

    private var seeded = false

    /**
     * The jobs in [board] that were not in the previous one.
     *
     * Empty on the first call, always. [seen] starts empty, so without this the
     * first answer would be forty new jobs and forty reasons to turn the
     * feature off — the first pass learns what is already there and says
     * nothing.
     */
    fun newIn(board: List<DeliveryOffer>): List<DeliveryOffer> {
        val appeared = if (seeded) board.filter { it.id !in seen } else emptyList()

        seen = board.map { it.id }.toSet()
        seeded = true

        return appeared
    }
}
