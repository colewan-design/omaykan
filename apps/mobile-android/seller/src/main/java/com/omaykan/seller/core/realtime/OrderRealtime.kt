package com.omaykan.seller.core.realtime

import com.omaykan.seller.core.network.ApiBaseUrl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min

/**
 * The shop's live order channel, `private-store.{id}`, as a stream of "look
 * now" signals.
 *
 * ## Signals, not state
 *
 * An event says something happened to an order; it is not the order. Each one
 * nudges OrderFeed to fetch, the same fetch pull-to-refresh does. The payloads
 * are thin on purpose (documentation/mobile-plan.md §8), and one code path for
 * what an order looks like beats two that can disagree.
 *
 * ## Polling stays
 *
 * Slower while this is connected, not gone: a socket can die quietly behind a
 * carrier's NAT, and a merchant must never be left reading a list that stopped
 * updating. [connected] is what OrderFeed reads to choose its interval.
 *
 * ## Refusals
 *
 * Channel auth answers 403 for a shop that is suspended, or a membership that
 * has gone. That is not a reason to hammer the server, and not a reason to sign
 * anybody out either: this backs off for five minutes and polling carries on.
 *
 * See documentation/merchant-features.md §6.
 */
@Singleton
class OrderRealtime @Inject constructor(
    client: OkHttpClient,
    private val json: Json,
    private val config: ReverbConfig,
    @ApiBaseUrl private val baseUrl: String,
) {
    private companion object {
        const val PONG_TIMEOUT_SECONDS = 30
        const val REFUSED_RETRY_MS = 5 * 60_000L
        const val MAX_BACKOFF_MS = 60_000L
    }

    /**
     * No read timeout: a quiet socket is a healthy one. Liveness is the
     * protocol's ping and pong below, not a timer on the stream.
     */
    private val client: OkHttpClient = client.newBuilder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    private sealed interface Outcome {
        /** A settings problem the server will not change its mind about. */
        data object Fatal : Outcome

        /** Channel auth said no. */
        data object Refused : Outcome

        /** The connection ended; [wasLive] if it had got as far as subscribing. */
        data class Dropped(val wasLive: Boolean) : Outcome
    }

    /**
     * Stay subscribed to [storeId]'s channel until cancelled, calling [onSignal]
     * for every order event — and once on each (re)subscribe, to catch up on
     * anything that happened while the socket was down.
     *
     * Returns at once, doing nothing, when the build has no Reverb key.
     */
    suspend fun run(storeId: String, onSignal: () -> Unit) {
        if (!config.enabled) return

        var failures = 0
        try {
            while (currentCoroutineContext().isActive) {
                val outcome = connectOnce("private-store.$storeId", onSignal)
                _connected.value = false

                when (outcome) {
                    Outcome.Fatal -> return
                    Outcome.Refused -> delay(REFUSED_RETRY_MS)
                    is Outcome.Dropped -> {
                        failures = if (outcome.wasLive) 1 else failures + 1
                        // 2s, 4s, 8s … a minute; jitter-free is fine for one phone.
                        delay(min(MAX_BACKOFF_MS, 1_000L shl min(failures, 6)))
                    }
                }
            }
        } finally {
            _connected.value = false
        }
    }

    private suspend fun connectOnce(channel: String, onSignal: () -> Unit): Outcome {
        val frames = Channel<String>(Channel.UNLIMITED)

        val socket = client.newWebSocket(
            Request.Builder().url(PusherProtocol.socketUrl(config)).build(),
            object : WebSocketListener() {
                override fun onMessage(webSocket: WebSocket, text: String) {
                    frames.trySend(text)
                }

                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                    webSocket.close(1000, null)
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    frames.close()
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    frames.close()
                }
            },
        )

        var live = false
        try {
            var waitSeconds = PONG_TIMEOUT_SECONDS
            var activitySeconds = PONG_TIMEOUT_SECONDS
            var awaitingPong = false

            while (true) {
                val received = withTimeoutOrNull(waitSeconds * 1_000L) { frames.receiveCatching() }

                if (received == null) {
                    // Quiet for a whole activity window. Ask; a second silence
                    // means the socket is gone even if nobody told OkHttp.
                    if (awaitingPong) return Outcome.Dropped(live)
                    socket.send(PusherProtocol.ping())
                    awaitingPong = true
                    waitSeconds = PONG_TIMEOUT_SECONDS
                    continue
                }

                val text = received.getOrNull() ?: return Outcome.Dropped(live)
                awaitingPong = false
                waitSeconds = activitySeconds

                when (val frame = PusherProtocol.parse(json, text)) {
                    is PusherFrame.Established -> {
                        activitySeconds = frame.activityTimeoutSeconds.coerceIn(10, 300)
                        waitSeconds = activitySeconds
                        val auth = try {
                            authorize(frame.socketId, channel)
                        } catch (_: IOException) {
                            return Outcome.Dropped(live)
                        } ?: return Outcome.Refused
                        socket.send(PusherProtocol.subscribe(channel, auth))
                    }
                    is PusherFrame.Subscribed -> if (frame.channel == channel) {
                        live = true
                        _connected.value = true
                        onSignal()
                    }
                    PusherFrame.Ping -> socket.send(PusherProtocol.pong())
                    is PusherFrame.Error -> return if (PusherProtocol.isFatal(frame.code)) Outcome.Fatal else Outcome.Dropped(live)
                    is PusherFrame.Event -> if (frame.channel == channel) onSignal()
                    PusherFrame.Pong, PusherFrame.Unknown -> Unit
                }
            }
        } finally {
            socket.cancel()
            frames.close()
        }
    }

    /**
     * `POST /api/broadcasting/auth` — the server's signature for this socket on
     * this channel. The staff token is attached by StaffAuthInterceptor, as on
     * every call to our own API. Null for a refusal; a network failure throws
     * IOException, which ends this attempt like any other drop.
     */
    private suspend fun authorize(socketId: String, channel: String): String? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$baseUrl/api/broadcasting/auth")
            .post(FormBody.Builder().add("socket_id", socketId).add("channel_name", channel).build())
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@withContext null
            val body = response.body?.string().orEmpty()
            runCatching { json.parseToJsonElement(body).jsonObject["auth"]?.jsonPrimitive?.contentOrNull }.getOrNull()
        }
    }
}
