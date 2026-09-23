package com.omaykan.rider.core.data

import com.omaykan.rider.core.network.ApiCaller
import com.omaykan.rider.core.network.RiderApi
import com.omaykan.rider.core.network.dto.AdvanceRequestDto
import com.omaykan.rider.core.network.dto.AssignmentDto
import com.omaykan.rider.core.network.dto.AssignmentEnvelopeDto
import com.omaykan.rider.core.network.dto.BoardDto
import com.omaykan.rider.core.network.dto.EarningsDto
import com.omaykan.rider.core.network.dto.ForgotPasswordRequestDto
import com.omaykan.rider.core.network.dto.LoginRequestDto
import com.omaykan.rider.core.network.dto.MessageDto
import com.omaykan.rider.core.network.dto.MyDeliveriesDto
import com.omaykan.rider.core.network.dto.OfferDto
import com.omaykan.rider.core.network.dto.PasswordChangeRequestDto
import com.omaykan.rider.core.network.dto.PositionAckDto
import com.omaykan.rider.core.network.dto.PositionRequestDto
import com.omaykan.rider.core.network.dto.ProfileUpdateRequestDto
import com.omaykan.rider.core.network.dto.RatingsDto
import com.omaykan.rider.core.network.dto.SupportDto
import com.omaykan.rider.core.network.dto.ReleasedDto
import com.omaykan.rider.core.network.dto.RiderEnvelopeDto
import com.omaykan.rider.core.network.dto.RiderSessionDto
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.MultipartBody
import okhttp3.RequestBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The generation counter, which is the one piece of real concurrency in this
 * app.
 *
 * A fetch that was already in flight when a rider took a job would, if
 * published, put that job back on the board for up to fifteen seconds — long
 * enough for a second rider to tap it and lose. Everything below is about that.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WorkFeedTest {

    private val offerA = OfferDto(id = "a", deliveryFeeCents = 6500)
    private val offerB = OfferDto(id = "b", deliveryFeeCents = 4500)

    private class FakeApi : RiderApi {
        var board: List<OfferDto> = emptyList()
        var active: List<AssignmentDto> = emptyList()
        var completed: List<AssignmentDto> = emptyList()

        /** Held open to keep one fetch in the air while a write lands. */
        var gate: CompletableDeferred<Unit>? = null

        var boardCalls = 0

        var failNext = false

        override suspend fun board(): BoardDto {
            boardCalls++
            if (failNext) {
                failNext = false
                throw IOException("no signal")
            }
            // The answer is captured on entry, so a gated call returns what the
            // server would have said when it was asked — which is the whole
            // point of the test.
            val answer = BoardDto(board)
            gate?.let {
                gate = null
                it.await()
            }
            return answer
        }

        override suspend fun mine(): MyDeliveriesDto = MyDeliveriesDto(active, completed)

        override suspend fun accept(orderId: String): AssignmentEnvelopeDto =
            throw UnsupportedOperationException()

        override suspend fun advance(
            orderId: String,
            request: AdvanceRequestDto,
        ): AssignmentEnvelopeDto = throw UnsupportedOperationException()

        override suspend fun release(orderId: String): ReleasedDto =
            throw UnsupportedOperationException()

        // The feed never reports a position; the two are separate concerns and
        // this fake is here to prove the board's behaviour, not the reporter's.
        override suspend fun position(request: PositionRequestDto): PositionAckDto =
            throw UnsupportedOperationException()

        override suspend fun stopSharingPosition(): PositionAckDto =
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

    @Test
    fun `a claim moves a job off the board and into my work at once`() = runTest {
        val api = FakeApi().apply { board = listOf(offerA, offerB) }
        val feed = WorkFeed(DeliveryRepository(api, ApiCaller(Json {})), backgroundScope)

        backgroundScope.launch { feed.state.collect {} }
        runCurrent()

        assertEquals(listOf("a", "b"), feed.state.value.board.map { it.id })

        // The refetch every write triggers is held open, so what is asserted
        // below is the optimistic edit itself and not the server agreeing with
        // it a moment later.
        api.gate = CompletableDeferred()

        feed.claimed(AssignmentDto(id = "a", deliveryStage = "assigned").toModel())
        runCurrent()

        assertEquals(listOf("b"), feed.state.value.board.map { it.id })
        assertEquals(listOf("a"), feed.state.value.active.map { it.id })
    }

    /**
     * The race this whole mechanism exists for: a fetch that started before the
     * claim comes back after it, still carrying the job on the board.
     */
    @Test
    fun `a fetch that started before a claim is discarded, not published`() = runTest {
        val api = FakeApi().apply { board = listOf(offerA, offerB) }
        val feed = WorkFeed(DeliveryRepository(api, ApiCaller(Json {})), backgroundScope)

        backgroundScope.launch { feed.state.collect {} }
        runCurrent()
        assertEquals(listOf("a", "b"), feed.state.value.board.map { it.id })

        // A second fetch, held open. It will answer with the board as it was.
        val gate = CompletableDeferred<Unit>()
        api.gate = gate
        feed.refresh()
        runCurrent()

        // The claim lands while that fetch is in the air. The server has moved
        // on too, so the *next* fetch will not carry the job on the board — it
        // will carry it in this rider's own work instead.
        api.board = listOf(offerB)
        api.active = listOf(AssignmentDto(id = "a", deliveryStage = "assigned"))
        feed.claimed(AssignmentDto(id = "a", deliveryStage = "assigned").toModel())
        runCurrent()
        assertEquals(listOf("b"), feed.state.value.board.map { it.id })

        // The stale answer arrives. It must not put "a" back.
        gate.complete(Unit)
        runCurrent()

        assertEquals(listOf("b"), feed.state.value.board.map { it.id })
        assertEquals(listOf("a"), feed.state.value.active.map { it.id })
    }

    /** Discarding is not dropping: the loop refetches instead of waiting. */
    @Test
    fun `a discarded fetch is retried straight away rather than after the interval`() = runTest {
        val api = FakeApi().apply { board = listOf(offerA) }
        val feed = WorkFeed(DeliveryRepository(api, ApiCaller(Json {})), backgroundScope)

        backgroundScope.launch { feed.state.collect {} }
        runCurrent()
        val afterFirst = api.boardCalls

        val gate = CompletableDeferred<Unit>()
        api.gate = gate
        feed.refresh()
        runCurrent()

        feed.claimed(AssignmentDto(id = "a", deliveryStage = "assigned").toModel())
        gate.complete(Unit)
        runCurrent()

        // The gated call, then the immediate refetch — with no virtual time
        // advanced, so neither of them waited out the fifteen-second interval.
        assertTrue(
            "expected at least two more fetches, got ${api.boardCalls - afterFirst}",
            api.boardCalls - afterFirst >= 2,
        )
    }

    /**
     * A rider halfway to a shop must be told the screen has stopped updating,
     * and must not have the address taken away from them.
     */
    @Test
    fun `a failed fetch keeps the last good lists and says so`() = runTest {
        val api = FakeApi().apply { board = listOf(offerA) }
        val feed = WorkFeed(DeliveryRepository(api, ApiCaller(Json {})), backgroundScope)

        backgroundScope.launch { feed.state.collect {} }
        runCurrent()

        api.failNext = true
        feed.refresh()
        runCurrent()

        assertEquals(listOf("a"), feed.state.value.board.map { it.id })
        assertEquals("No connection.", feed.state.value.error)
    }

    @Test
    fun `a delivered job leaves my active work for the finished list`() = runTest {
        val api = FakeApi()
        val feed = WorkFeed(DeliveryRepository(api, ApiCaller(Json {})), backgroundScope)

        backgroundScope.launch { feed.state.collect {} }
        runCurrent()

        api.gate = CompletableDeferred()
        feed.claimed(AssignmentDto(id = "a", deliveryStage = "assigned").toModel())
        feed.advanced(AssignmentDto(id = "a", deliveryStage = "delivered").toModel())
        runCurrent()

        assertTrue(feed.state.value.active.isEmpty())
        assertEquals(listOf("a"), feed.state.value.completed.map { it.id })
    }

    /**
     * Signing out must not leave the previous rider's addresses on screen for
     * whoever signs in next on a shared phone.
     */
    @Test
    fun `a reset empties everything`() = runTest {
        val api = FakeApi().apply { board = listOf(offerA) }
        val feed = WorkFeed(DeliveryRepository(api, ApiCaller(Json {})), backgroundScope)

        backgroundScope.launch { feed.state.collect {} }
        runCurrent()

        api.gate = CompletableDeferred()
        feed.claimed(AssignmentDto(id = "a", deliveryStage = "assigned").toModel())
        feed.reset()
        runCurrent()

        assertTrue(feed.state.value.board.isEmpty())
        assertTrue(feed.state.value.active.isEmpty())
        assertTrue(feed.state.value.loading)
    }
}
