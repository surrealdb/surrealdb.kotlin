package com.surrealdb.kotlin.runtime.codec

import com.surrealdb.kotlin.api.SurrealClientConfig
import com.surrealdb.kotlin.api.error.SurrealProtocolException
import com.surrealdb.kotlin.runtime.SurrealRpcRequest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class CodecTest {

    private val codec = SurrealCodec(SurrealClientConfig(url = "http://localhost:8000"))

    // ── HTTP payload round-trip ──

    @Test
    fun `encodes request id, method and params over HTTP`() {
        val request = SurrealRpcRequest(
            id = "abc-123",
            method = "query",
            params = listOf(JsonPrimitive("SELECT * FROM person"), buildJsonObject { put("limit", JsonPrimitive(10)) }),
        )

        val bytes = codec.encodeHttpPayload(request)
        val text = bytes.decodeToString()

        // Parse it back through Json to assert structure (avoid string equality flakiness)
        val parsed = SurrealClientConfig(url = "x").json
            .parseToJsonElement(text).jsonObject
        assertEquals("abc-123", parsed["id"]?.jsonPrimitive?.content)
        assertEquals("query", parsed["method"]?.jsonPrimitive?.content)
        assertNotNull(parsed["params"])
    }

    @Test
    fun `decodes successful HTTP response`() {
        val payload = """{"id":"1","result":{"ok":true}}""".encodeToByteArray()
        val response = codec.decodeHttpPayload(payload)

        assertEquals("1", response.id)
        assertEquals(true, (response.result as JsonObject)["ok"]?.jsonPrimitive?.content?.toBooleanStrict())
        assertNull(response.error)
    }

    @Test
    fun `decodes error HTTP response`() {
        val payload = """{"id":"1","error":{"code":-32000,"message":"oops"}}""".encodeToByteArray()
        val response = codec.decodeHttpPayload(payload)

        assertEquals("1", response.id)
        assertNotNull(response.error)
        assertEquals(-32000, response.error?.code)
        assertEquals("oops", response.error?.message)
    }

    @Test
    fun `decode of malformed payload throws SurrealProtocolException`() {
        assertFailsWith<SurrealProtocolException> {
            codec.decodeHttpPayload("not json at all".encodeToByteArray())
        }
    }

    // ── WS round-trip ──

    @Test
    fun `WS encodeWsText produces valid JSON`() {
        val request = SurrealRpcRequest(
            id = "ws-1",
            method = "ping",
            params = emptyList(),
        )
        val text = codec.encodeWsText(request)
        val parsed = SurrealClientConfig(url = "x").json
            .parseToJsonElement(text).jsonObject
        assertEquals("ws-1", parsed["id"]?.jsonPrimitive?.content)
        assertEquals("ping", parsed["method"]?.jsonPrimitive?.content)
    }

    @Test
    fun `WS decodeWsText parses live notification frame`() {
        val frame = """{"result":{"action":"CREATE","id":"live-1","result":{"id":"person:1"}}}"""
        val response = codec.decodeWsText(frame)

        // No top-level id → live notification
        assertNull(response.id)
        assertNotNull(response.result)
    }

    @Test
    fun `WS decode of malformed text throws SurrealProtocolException`() {
        assertFailsWith<SurrealProtocolException> {
            codec.decodeWsText("definitely not json")
        }
    }

    @Test
    fun `contentTypeHeader is application slash json`() {
        assertEquals("application/json", codec.contentTypeHeader())
    }
}
