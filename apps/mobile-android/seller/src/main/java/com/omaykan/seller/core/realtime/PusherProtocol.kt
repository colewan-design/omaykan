package com.omaykan.seller.core.realtime

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/**
 * Where Reverb is, as the build was told. Empty [key] means "no socket".
 */
data class ReverbConfig(
    val key: String,
    val host: String,
    val port: Int,
    /** "/reverb" in production — nginx's path to Reverb. Empty for the Pusher default. */
    val path: String,
    val tls: Boolean,
) {
    val enabled: Boolean get() = key.isNotBlank() && host.isNotBlank()
}

/** One message from Reverb, reduced to what this app acts on. */
internal sealed interface PusherFrame {
    data class Established(val socketId: String, val activityTimeoutSeconds: Int) : PusherFrame
    data object Ping : PusherFrame
    data object Pong : PusherFrame
    data class Subscribed(val channel: String) : PusherFrame
    data class Error(val code: Int?, val message: String?) : PusherFrame
    /** An application event — `order.placed` and friends. */
    data class Event(val name: String, val channel: String?) : PusherFrame
    data object Unknown : PusherFrame
}

/**
 * The part of the Pusher wire protocol (v7) this app needs, and nothing else.
 *
 * ## Why not pusher-websocket-java
 *
 * Production Reverb sits behind nginx at `/reverb/`, because `/app` is the
 * till's own page. The Pusher Java client hardcodes `/app/{key}` and offers no
 * way to put a path in front of it (documentation/mobile-plan.md §12.1). The
 * protocol is small — connect, authorize a private channel, subscribe, answer
 * pings — and OkHttp, already in the app, speaks WebSocket. So the URL is
 * built here, path included, and nothing on the server has to move.
 *
 * Pure functions, so the parsing and the frames are unit-tested rather than
 * trusted.
 */
internal object PusherProtocol {

    /** Reverb's documented default when a server does not say. */
    private const val DEFAULT_ACTIVITY_TIMEOUT_SECONDS = 120

    fun socketUrl(config: ReverbConfig): String {
        val scheme = if (config.tls) "wss" else "ws"
        val defaultPort = if (config.tls) 443 else 80
        val port = if (config.port == defaultPort) "" else ":${config.port}"
        val path = config.path.trim().trimEnd('/').let { if (it.isEmpty() || it.startsWith("/")) it else "/$it" }
        return "$scheme://${config.host}$port$path/app/${config.key}?protocol=7&client=omaykan-seller&version=1.0&flash=false"
    }

    fun parse(json: Json, text: String): PusherFrame {
        val message = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull() ?: return PusherFrame.Unknown
        val event = (message["event"] as? JsonPrimitive)?.contentOrNull ?: return PusherFrame.Unknown
        val channel = (message["channel"] as? JsonPrimitive)?.contentOrNull

        // `data` is a JSON *string* holding an object on most frames, and a
        // plain object on a few. Both are accepted.
        val data: JsonObject? = when (val raw = message["data"]) {
            is JsonObject -> raw
            is JsonPrimitive -> raw.contentOrNull?.let { runCatching { json.parseToJsonElement(it).jsonObject }.getOrNull() }
            else -> null
        }

        return when (event) {
            "pusher:connection_established" -> {
                val socketId = data?.get("socket_id")?.jsonPrimitive?.contentOrNull ?: return PusherFrame.Unknown
                val timeout = data["activity_timeout"]?.jsonPrimitive?.intOrNull ?: DEFAULT_ACTIVITY_TIMEOUT_SECONDS
                PusherFrame.Established(socketId, timeout)
            }
            "pusher:ping" -> PusherFrame.Ping
            "pusher:pong" -> PusherFrame.Pong
            "pusher_internal:subscription_succeeded" -> PusherFrame.Subscribed(channel.orEmpty())
            "pusher:error" -> PusherFrame.Error(
                data?.get("code")?.jsonPrimitive?.intOrNull,
                data?.get("message")?.jsonPrimitive?.contentOrNull,
            )
            else -> if (event.startsWith("pusher")) PusherFrame.Unknown else PusherFrame.Event(event, channel)
        }
    }

    fun subscribe(channel: String, auth: String): String = buildJsonObject {
        put("event", "pusher:subscribe")
        putJsonObject("data") {
            put("auth", auth)
            put("channel", channel)
        }
    }.toString()

    fun ping(): String = """{"event":"pusher:ping","data":{}}"""

    fun pong(): String = """{"event":"pusher:pong","data":{}}"""

    /**
     * Pusher's close-code ranges: 4000–4099 mean "do not reconnect with these
     * settings" (a wrong app key, SSL required); everything else is worth
     * another try.
     */
    fun isFatal(code: Int?): Boolean = code != null && code in 4000..4099
}
