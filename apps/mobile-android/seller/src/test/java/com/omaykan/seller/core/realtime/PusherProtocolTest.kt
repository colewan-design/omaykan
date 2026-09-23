package com.omaykan.seller.core.realtime

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PusherProtocolTest {

    private val json = Json { ignoreUnknownKeys = true }

    private val production = ReverbConfig(key = "abc123", host = "omaykan.com", port = 443, path = "/reverb", tls = true)

    /** The whole reason this is not pusher-websocket-java: the path in front of /app. */
    @Test
    fun `the socket url keeps the reverb path in front of the pusher one`() {
        assertEquals(
            "wss://omaykan.com/reverb/app/abc123?protocol=7&client=omaykan-seller&version=1.0&flash=false",
            PusherProtocol.socketUrl(production),
        )
    }

    @Test
    fun `a local reverb on its own port with no path`() {
        val local = ReverbConfig(key = "k", host = "10.0.2.2", port = 8080, path = "", tls = false)

        assertEquals(
            "ws://10.0.2.2:8080/app/k?protocol=7&client=omaykan-seller&version=1.0&flash=false",
            PusherProtocol.socketUrl(local),
        )
    }

    @Test
    fun `a path given without its slash or with a trailing one is normalised`() {
        assertTrue(PusherProtocol.socketUrl(production.copy(path = "reverb/")).startsWith("wss://omaykan.com/reverb/app/"))
    }

    @Test
    fun `connection established carries the socket id inside a json string`() {
        val frame = PusherProtocol.parse(
            json,
            """{"event":"pusher:connection_established","data":"{\"socket_id\":\"123.456\",\"activity_timeout\":30}"}""",
        )

        assertEquals(PusherFrame.Established("123.456", 30), frame)
    }

    @Test
    fun `an order event on the channel is an event and pusher housekeeping is not`() {
        assertEquals(
            PusherFrame.Event("order.placed", "private-store.s1"),
            PusherProtocol.parse(json, """{"event":"order.placed","channel":"private-store.s1","data":"{\"orderId\":\"o1\"}"}"""),
        )
        assertEquals(PusherFrame.Ping, PusherProtocol.parse(json, """{"event":"pusher:ping","data":{}}"""))
        assertEquals(
            PusherFrame.Subscribed("private-store.s1"),
            PusherProtocol.parse(json, """{"event":"pusher_internal:subscription_succeeded","channel":"private-store.s1","data":"{}"}"""),
        )
        assertEquals(PusherFrame.Unknown, PusherProtocol.parse(json, "not json"))
    }

    @Test
    fun `errors in the 4000 range are fatal and the rest are worth retrying`() {
        val error = PusherProtocol.parse(json, """{"event":"pusher:error","data":{"code":4001,"message":"App key not in this cluster"}}""")

        assertEquals(PusherFrame.Error(4001, "App key not in this cluster"), error)
        assertTrue(PusherProtocol.isFatal(4001))
        assertFalse(PusherProtocol.isFatal(4200))
        assertFalse(PusherProtocol.isFatal(null))
    }

    @Test
    fun `the subscribe frame carries the channel and its signature`() {
        val frame = json.parseToJsonElement(PusherProtocol.subscribe("private-store.s1", "abc123:sig")).jsonObject
        val data = frame["data"]!!.jsonObject

        assertEquals("pusher:subscribe", frame["event"]!!.jsonPrimitive.content)
        assertEquals("private-store.s1", data["channel"]!!.jsonPrimitive.content)
        assertEquals("abc123:sig", data["auth"]!!.jsonPrimitive.content)
    }

    @Test
    fun `no key means no socket`() {
        assertFalse(production.copy(key = "").enabled)
        assertTrue(production.enabled)
    }
}
