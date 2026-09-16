package com.omaykan.rider.core.location

import com.omaykan.rider.core.network.ApiCaller
import com.omaykan.rider.core.network.RiderApi
import com.omaykan.rider.core.network.dto.AdvanceRequestDto
import com.omaykan.rider.core.network.dto.AssignmentEnvelopeDto
import com.omaykan.rider.core.network.dto.BoardDto
import com.omaykan.rider.core.network.dto.EarningsDto
import com.omaykan.rider.core.network.dto.ForgotPasswordRequestDto
import com.omaykan.rider.core.network.dto.LoginRequestDto
import com.omaykan.rider.core.network.dto.MessageDto
import com.omaykan.rider.core.network.dto.MyDeliveriesDto
import com.omaykan.rider.core.network.dto.PasswordChangeRequestDto
import com.omaykan.rider.core.network.dto.PositionAckDto
import com.omaykan.rider.core.network.dto.PositionRequestDto
import com.omaykan.rider.core.network.dto.ProfileUpdateRequestDto
import com.omaykan.rider.core.network.dto.RatingsDto
import com.omaykan.rider.core.network.dto.SupportDto
import com.omaykan.rider.core.network.dto.ReleasedDto
import com.omaykan.rider.core.network.dto.RiderEnvelopeDto
import com.omaykan.rider.core.network.dto.RiderSessionDto
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.MultipartBody
import okhttp3.RequestBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The rules that keep a live map from flattening a rider's battery.
 *
 * A position feed is easy to write and easy to write badly: the naive version
 * posts every fix the sensor produces, which on a moving phone is one or two a
 * second, for a map that repaints far slower than that. The three rules below
 * are what stand between this app and that, and each one would look removable
 * to somebody in a hurry unless a test failed when they removed it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PositionReporterTest {

    /**
     * A sensor that answers with a scripted sequence, one fix per ask, and
     * repeats the last one forever. That last part matters: a rider standing at
     * a shop is a real phone returning the same coordinate indefinitely, and it
     * is exactly the case the movement rule exists for.
     */
    private class FakeSource(
        private val script: List<Fix>,
        /** What the system already had. Used only when the sensor times out. */
        private val cached: Fix? = null,
        /** A sensor that never answers, so the fallback is the only way through. */
        private val silent: Boolean = false,
    ) : FixSource {
        var asked = 0

        override fun permitted() = true

        override fun enabled() = true

        override fun fixes(minIntervalMs: Long): Flow<Fix> {
            if (silent) return emptyFlow()
            val fix = script[minOf(asked, script.lastIndex)]
            asked++
            return flowOf(fix)
        }

        override fun lastKnown(): Fix? = cached
    }

    private class FakeApi(private val ack: PositionAckDto) : RiderApi {
        val posted = mutableListOf<PositionRequestDto>()
        var stopped = 0

        override suspend fun position(request: PositionRequestDto): PositionAckDto {
            posted += request
            return ack
        }

        override suspend fun stopSharingPosition(): PositionAckDto {
            stopped++
            return PositionAckDto(recorded = false)
        }

        override suspend fun board(): BoardDto = throw UnsupportedOperationException()

        override suspend fun mine(): MyDeliveriesDto = throw UnsupportedOperationException()

        override suspend fun accept(orderId: String): AssignmentEnvelopeDto =
            throw UnsupportedOperationException()

        override suspend fun advance(orderId: String, request: AdvanceRequestDto): AssignmentEnvelopeDto =
            throw UnsupportedOperationException()

        override suspend fun release(orderId: String): ReleasedDto =
            throw UnsupportedOperationException()

        override suspend fun me(): RiderEnvelopeDto = throw UnsupportedOperationException()

        override suspend fun logout(): ReleasedDto = throw UnsupportedOperationException()

        override suspend fun login(request: LoginRequestDto): RiderSessionDto =
            throw UnsupportedOperationException()

        override suspend fun register(
            name: RequestBody,
            email: RequestBody,
            phone: RequestBody,
            password: RequestBody,
            passwordConfirmation: RequestBody,
            licenseNumber: RequestBody,
            plateNumber: RequestBody,
            vehicleType: RequestBody,
            vehicleMake: RequestBody,
            vehicleModel: RequestBody,
            vehicleColor: RequestBody,
            licenseImage: MultipartBody.Part,
            plateImage: MultipartBody.Part,
        ): RiderSessionDto = throw UnsupportedOperationException()

        override suspend fun uploadAvatar(photo: MultipartBody.Part): RiderEnvelopeDto =
            throw UnsupportedOperationException()

        override suspend fun deleteAvatar(): RiderEnvelopeDto =
            throw UnsupportedOperationException()

        override suspend fun ratings(): RatingsDto = throw UnsupportedOperationException()

        override suspend fun support(): SupportDto = throw UnsupportedOperationException()

        override suspend fun updateProfile(request: ProfileUpdateRequestDto): RiderEnvelopeDto =
            throw UnsupportedOperationException()

        override suspend fun updatePassword(request: PasswordChangeRequestDto): RiderEnvelopeDto =
            throw UnsupportedOperationException()

        override suspend fun forgotPassword(request: ForgotPasswordRequestDto): MessageDto =
            throw UnsupportedOperationException()

        override suspend fun earnings(): EarningsDto = throw UnsupportedOperationException()

    }

    private fun fix(lat: Double, lng: Double, accuracy: Float? = 8f) =
        Fix(lat = lat, lng = lng, headingDeg = 90f, speedKph = 18f, accuracyM = accuracy)

    @Test
    fun `the server sets the cadence, not the app`() = runTest {
        // Carrying nothing: the server says back off to sixty seconds.
        val api = FakeApi(PositionAckDto(recorded = true, activeDeliveries = 0, nextPingSeconds = 60))
        val reporter = PositionReporter(
            FakeSource(script = listOf(fix(16.4023, 120.5960), fix(16.4100, 120.6000))),
            api,
            ApiCaller(Json {}),
        )

        val job = launch { reporter.report() }
        runCurrent()

        assertEquals(1, api.posted.size)

        // Well past the app's own ten seconds, and still nothing: the app is
        // obeying the interval it was handed rather than one of its own.
        advanceTimeBy(30_000)
        runCurrent()
        assertEquals(1, api.posted.size)

        advanceTimeBy(31_000)
        runCurrent()
        assertEquals(2, api.posted.size)

        job.cancelAndJoin()
    }

    @Test
    fun `a rider standing still sends nothing after the first fix`() = runTest {
        val api = FakeApi(PositionAckDto(recorded = true, activeDeliveries = 1, nextPingSeconds = 10))
        // The same coordinate every time — a rider waiting at a shop, which is
        // most of some deliveries.
        val reporter = PositionReporter(
            FakeSource(script = listOf(fix(16.4023, 120.5960))),
            api,
            ApiCaller(Json {}),
        )

        val job = launch { reporter.report() }
        runCurrent()
        advanceTimeBy(60_000)
        runCurrent()

        assertEquals(1, api.posted.size)

        job.cancelAndJoin()
    }

    @Test
    fun `a cell-tower guess is not sent as a position`() = runTest {
        val api = FakeApi(PositionAckDto(recorded = true, activeDeliveries = 1, nextPingSeconds = 10))
        // 800m of accuracy is a tower, not a rider. Drawing it puts a marker in
        // somebody else's barangay, which is worse than an empty map.
        val reporter = PositionReporter(
            FakeSource(script = listOf(fix(16.4023, 120.5960, accuracy = 800f))),
            api,
            ApiCaller(Json {}),
        )

        val job = launch { reporter.report() }
        runCurrent()
        advanceTimeBy(60_000)
        runCurrent()

        assertTrue(api.posted.isEmpty())

        job.cancelAndJoin()
    }

    @Test
    fun `a live fix beats the system's cached one`() = runTest {
        val api = FakeApi(PositionAckDto(recorded = true, activeDeliveries = 1, nextPingSeconds = 10))

        // The bug this guards against was found the first time the app ran on a
        // device: the cached fix used to be the flow's first emission, so it won
        // every race and the rider reported wherever the phone was last seen —
        // forever, and looking entirely correct while doing it.
        val reporter = PositionReporter(
            FakeSource(
                script = listOf(fix(16.4152, 120.5941)),
                cached = fix(37.4220, -122.0840),
            ),
            api,
            ApiCaller(Json {}),
        )

        val job = launch { reporter.report() }
        runCurrent()

        assertEquals(1, api.posted.size)
        assertEquals(16.4152, api.posted[0].lat, 0.00001)

        job.cancelAndJoin()
    }

    @Test
    fun `a silent sensor falls back to the cached fix rather than reporting nothing`() = runTest {
        val api = FakeApi(PositionAckDto(recorded = true, activeDeliveries = 1, nextPingSeconds = 10))

        // Indoors, or GPS cold. A stale position clearly labelled by its age is
        // worth more to somebody waiting than an empty map.
        val reporter = PositionReporter(
            FakeSource(
                script = emptyList(),
                cached = fix(16.4152, 120.5941),
                silent = true,
            ),
            api,
            ApiCaller(Json {}),
        )

        val job = launch { reporter.report() }
        advanceTimeBy(7_000)
        runCurrent()

        assertEquals(1, api.posted.size)
        assertEquals(16.4152, api.posted[0].lat, 0.00001)

        job.cancelAndJoin()
    }

    @Test
    fun `the reported state names how many people are watching`() = runTest {
        val api = FakeApi(PositionAckDto(recorded = true, activeDeliveries = 2, nextPingSeconds = 10))
        val reporter = PositionReporter(
            FakeSource(script = listOf(fix(16.4023, 120.5960))),
            api,
            ApiCaller(Json {}),
        )

        val job = launch { reporter.report() }
        runCurrent()

        assertEquals(2, reporter.state.value.listeners)
        assertTrue(reporter.state.value.sharing)

        job.cancelAndJoin()

        // Cancelling the loop is the off switch, and the state follows it.
        assertTrue(!reporter.state.value.sharing)
    }
}
