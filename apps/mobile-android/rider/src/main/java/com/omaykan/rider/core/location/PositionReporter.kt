package com.omaykan.rider.core.location

import com.omaykan.rider.core.network.ApiCaller
import com.omaykan.rider.core.network.ApiException
import com.omaykan.rider.core.network.RiderApi
import com.omaykan.rider.core.network.dto.PositionRequestDto
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

/**
 * What the rider is currently telling the world, and how it is going.
 *
 * Held so the switch on the work screen can say something more useful than
 * "on": how long ago the last fix went up, and whether anybody is actually
 * watching it.
 */
data class SharingState(
    val sharing: Boolean = false,
    /** Fixes accepted by the server since sharing started. */
    val sent: Int = 0,
    /** Deliveries the server says are listening. Zero means nobody is. */
    val listeners: Int = 0,
    /** The last failure, cleared by the next success. */
    val error: String? = null,
    /** True while waiting for the very first fix from the sensor. */
    val acquiring: Boolean = false,
)

/**
 * Turns fixes into pings.
 *
 * ## The three rules that keep this cheap
 *
 * A naive version posts every fix the sensor produces, which on a moving phone
 * is one or two a second, which is a rider's data allowance and a rider's
 * battery for no benefit — nothing on the other end can draw faster than it
 * repaints. So:
 *
 *  1. **The server sets the pace.** Each ack carries `nextPingSeconds` — ten
 *     while the rider is carrying something, sixty while they are not. The
 *     cadence is a platform decision, and this app obeys it rather than
 *     hard-coding one that would then need an APK to change.
 *  2. **A fix that says nothing new is not sent.** Standing at a shop waiting
 *     for an order is most of some deliveries, and re-sending the same
 *     coordinate every ten seconds tells nobody anything. Movement past
 *     [MIN_MOVEMENT_DEG] is the test.
 *  3. **A vague fix is not sent at all.** Accuracy past
 *     [LocationSource.MAX_USABLE_ACCURACY_M] is a cell-tower guess; drawing it
 *     puts a rider's marker in the wrong barangay, which is worse for everyone
 *     than a map that says "last seen four minutes ago".
 *
 * ## Failure is quiet
 *
 * A dropped ping is not an error a rider can act on and is not shown as one
 * until it has failed repeatedly — a phone goes through a tunnel on every
 * delivery in this city. The loop keeps going; the map goes stale on its own
 * and says so, which is exactly the right story for the customer watching it.
 */
@Singleton
class PositionReporter @Inject constructor(
    private val source: FixSource,
    private val api: RiderApi,
    private val caller: ApiCaller,
) {
    private companion object {
        /**
         * Roughly ten metres. Below this the rider has not moved; they are
         * standing outside a shop and the GPS is jittering.
         */
        const val MIN_MOVEMENT_DEG = 0.0001

        /** Failures in a row before the rider is told something is wrong. */
        const val FAILURES_BEFORE_COMPLAINING = 3

        /** Backoff after a failure, so a dead network is not hammered. */
        const val FAILURE_DELAY_MS = 20_000L

        /**
         * How long the sensor gets to produce a real fix before the cached one
         * is used instead.
         *
         * Long enough for a warm GPS on a phone that is outdoors and moving,
         * which is the case that matters; short enough that a rider indoors
         * still puts *something* on the map every tick.
         */
        const val FRESH_FIX_WAIT_MS = 6_000L
    }

    private val _state = MutableStateFlow(SharingState())
    val state: StateFlow<SharingState> = _state.asStateFlow()

    /**
     * Report until cancelled.
     *
     * Suspends for as long as sharing lasts, so the caller's structured
     * concurrency is the off switch: cancel the coroutine and reporting stops.
     * That is the whole lifecycle — there is no stop() to forget to call.
     *
     * The final [stopSharing] runs in a NonCancellable-free `finally` on
     * purpose: it is a best-effort courtesy, and a rider whose phone died owes
     * the server nothing.
     */
    suspend fun report() {
        _state.value = SharingState(sharing = true, acquiring = true)

        var lastSent: Fix? = null
        var intervalMs = 10_000L
        var failures = 0

        try {
            while (true) {
                // One fix per pass rather than a continuous collection: the
                // sensor is asked, answered, and let go until the next tick.
                // Collecting continuously and throwing most of it away is the
                // version that flattens a battery.
                //
                // The timeout is what makes this a *live* reading. The sensor
                // is given a few seconds to produce a real fix, and only if it
                // does not is the system's cached one used. Preferring the
                // cached one — which an earlier version did by emitting it
                // first from the flow — reports wherever the phone was last
                // seen, forever, and looks completely correct while doing it.
                val fix = withTimeoutOrNull(FRESH_FIX_WAIT_MS) { source.fixes().firstOrNull() }
                    ?: source.lastKnown()

                _state.update { it.copy(acquiring = false) }

                if (fix == null) {
                    // No permission, no provider, or nothing cached yet. Not an
                    // error the rider can act on; try again next tick.
                    delay(intervalMs)
                    continue
                }

                if (!fix.usable() || !fix.movedFrom(lastSent)) {
                    delay(intervalMs)
                    continue
                }

                try {
                    val ack = caller.call {
                        api.position(
                            PositionRequestDto(
                                lat = fix.lat,
                                lng = fix.lng,
                                headingDeg = fix.headingDeg,
                                speedKph = fix.speedKph,
                                accuracyM = fix.accuracyM,
                            ),
                        )
                    }

                    lastSent = fix
                    failures = 0
                    intervalMs = ack.nextPingSeconds.coerceIn(5, 300) * 1000L

                    _state.update {
                        it.copy(
                            sent = it.sent + 1,
                            listeners = ack.activeDeliveries,
                            error = null,
                        )
                    }
                } catch (e: ApiException) {
                    failures++
                    // A tunnel is not news. Three in a row is.
                    if (failures >= FAILURES_BEFORE_COMPLAINING) {
                        _state.update { it.copy(error = e.message) }
                    }
                    delay(FAILURE_DELAY_MS)
                    continue
                }

                delay(intervalMs)
            }
        } finally {
            _state.value = SharingState(sharing = false)
        }
    }

    /**
     * Tell the server to forget the last fix.
     *
     * Separate from cancelling the loop because the two answer different
     * questions: cancelling stops new positions, this erases the old one. A
     * rider who switches sharing off wants both, and gets both — the switch
     * cancels, then calls this.
     */
    suspend fun stopSharing() {
        runCatching { caller.call { api.stopSharingPosition() } }
    }

    private fun Fix.usable(): Boolean =
        accuracyM == null || accuracyM <= LocationSource.MAX_USABLE_ACCURACY_M

    private fun Fix.movedFrom(previous: Fix?): Boolean {
        if (previous == null) return true
        return abs(lat - previous.lat) > MIN_MOVEMENT_DEG ||
            abs(lng - previous.lng) > MIN_MOVEMENT_DEG
    }
}
