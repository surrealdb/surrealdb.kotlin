package com.surrealdb.kotlin

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests every RPC method: asserts the wire format (method name + params shape)
 * and that the response is propagated correctly. Uses MockEngine to capture the
 * exact HTTP body sent.
 */
class RpcMethodsTest {

    /** Captures the last RPC request and serves a stub `result` response. */
    private class Harness {
        var lastMethod: String? = null
        var lastParams: JsonArray? = null
        var stubResult: String = """{"id":"1","result":null}"""

        val engine = MockEngine { request ->
            val body = readBody(request)
            val parsed = json.parseToJsonElement(body).jsonObject
            lastMethod = parsed["method"]?.jsonPrimitive?.content
            lastParams = parsed["params"]?.jsonArray
            respond(
                content = stubResult,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }

        val client = SurrealClient(
            SurrealClientConfig(
                url = "http://localhost:8000",
                autoConnect = false,
                httpClientFactory = { _ -> HttpClient(engine) },
            ),
        )
    }

    // ── server methods ──

    @Test
    fun `ping sends ping with empty params`() = runTest {
        val h = Harness()
        h.client.ping()
        assertEquals("ping", h.lastMethod)
        assertEquals(0, h.lastParams?.size ?: 0)
    }

    @Test
    fun `version sends version with empty params`() = runTest {
        val h = Harness()
        h.client.version()
        assertEquals("version", h.lastMethod)
        assertEquals(0, h.lastParams?.size ?: 0)
    }

    @Test
    fun `use sends namespace and database as separate params`() = runTest {
        val h = Harness()
        h.client.use("ns1", "db1")
        assertEquals("use", h.lastMethod)
        assertEquals(2, h.lastParams?.size)
        assertEquals("ns1", h.lastParams?.get(0)?.jsonPrimitive?.content)
        assertEquals("db1", h.lastParams?.get(1)?.jsonPrimitive?.content)
    }

    @Test
    fun `auth sends info on the wire`() = runTest {
        val h = Harness()
        h.client.auth()
        assertEquals("info", h.lastMethod)
    }

    // ── auth methods ──

    @Test
    fun `signup sends signup with credentials object`() = runTest {
        val h = Harness()
        h.client.signup(buildJsonObject { put("user", JsonPrimitive("u")) })
        assertEquals("signup", h.lastMethod)
        assertEquals("u", h.lastParams?.get(0)?.jsonObject?.get("user")?.jsonPrimitive?.content)
    }

    @Test
    fun `signin sends signin with credentials object`() = runTest {
        val h = Harness()
        h.client.signin(buildJsonObject { put("user", JsonPrimitive("u")) })
        assertEquals("signin", h.lastMethod)
        assertEquals("u", h.lastParams?.get(0)?.jsonObject?.get("user")?.jsonPrimitive?.content)
    }

    @Test
    fun `authenticate sends authenticate with token string`() = runTest {
        val h = Harness()
        h.client.authenticate("jwt-here")
        assertEquals("authenticate", h.lastMethod)
        assertEquals("jwt-here", h.lastParams?.get(0)?.jsonPrimitive?.content)
    }

    @Test
    fun `invalidate sends invalidate with empty params`() = runTest {
        val h = Harness()
        h.client.invalidate()
        assertEquals("invalidate", h.lastMethod)
        assertEquals(0, h.lastParams?.size ?: 0)
    }

    @Test
    fun `reset sends reset with empty params`() = runTest {
        val h = Harness()
        h.client.reset()
        assertEquals("reset", h.lastMethod)
        assertEquals(0, h.lastParams?.size ?: 0)
    }

    // ── session variables ──

    @Test
    fun `let sends key and value`() = runTest {
        val h = Harness()
        h.client.`let`("k", JsonPrimitive("v"))
        assertEquals("let", h.lastMethod)
        assertEquals("k", h.lastParams?.get(0)?.jsonPrimitive?.content)
        assertEquals("v", h.lastParams?.get(1)?.jsonPrimitive?.content)
    }

    @Test
    fun `unset sends only the key`() = runTest {
        val h = Harness()
        h.client.unset("k")
        assertEquals("unset", h.lastMethod)
        assertEquals(1, h.lastParams?.size)
        assertEquals("k", h.lastParams?.get(0)?.jsonPrimitive?.content)
    }

    // ── query / run ──

    @Test
    fun `query without vars sends just the SQL`() = runTest {
        val h = Harness()
        h.client.query("SELECT 1")
        assertEquals("query", h.lastMethod)
        assertEquals(1, h.lastParams?.size)
        assertEquals("SELECT 1", h.lastParams?.get(0)?.jsonPrimitive?.content)
    }

    @Test
    fun `query with vars sends both`() = runTest {
        val h = Harness()
        h.client.query("SELECT type::table(\$tb)", buildJsonObject { put("tb", JsonPrimitive("person")) })
        assertEquals("query", h.lastMethod)
        assertEquals(2, h.lastParams?.size)
        assertEquals("person", h.lastParams?.get(1)?.jsonObject?.get("tb")?.jsonPrimitive?.content)
    }

    @Test
    fun `run with no version sends function, null, args array`() = runTest {
        val h = Harness()
        h.client.run("fn::greet", null, listOf(JsonPrimitive("world")))
        assertEquals("run", h.lastMethod)
        assertEquals(3, h.lastParams?.size)
        assertEquals("fn::greet", h.lastParams?.get(0)?.jsonPrimitive?.content)
        // Position 1 is JsonNull (version) — kotlinx serializes it as `null`
        assertTrue(h.lastParams?.get(1) is kotlinx.serialization.json.JsonNull)
        assertEquals(1, h.lastParams?.get(2)?.jsonArray?.size)
        assertEquals("world", h.lastParams?.get(2)?.jsonArray?.get(0)?.jsonPrimitive?.content)
    }

    @Test
    fun `run with version sends function, version, args`() = runTest {
        val h = Harness()
        h.client.run("fn::greet", "v1", listOf(JsonPrimitive("world")))
        assertEquals("v1", h.lastParams?.get(1)?.jsonPrimitive?.content)
    }

    // ── CRUD ──

    @Test
    fun `select sends thing as single param`() = runTest {
        val h = Harness()
        h.client.select("person:1")
        assertEquals("select", h.lastMethod)
        assertEquals(1, h.lastParams?.size)
        assertEquals("person:1", h.lastParams?.get(0)?.jsonPrimitive?.content)
    }

    @Test
    fun `create with data sends thing and data`() = runTest {
        val h = Harness()
        h.client.create("person:1", buildJsonObject { put("name", JsonPrimitive("Ada")) })
        assertEquals("create", h.lastMethod)
        assertEquals(2, h.lastParams?.size)
        assertEquals("Ada", h.lastParams?.get(1)?.jsonObject?.get("name")?.jsonPrimitive?.content)
    }

    @Test
    fun `create without data sends only thing`() = runTest {
        val h = Harness()
        h.client.create("person:1")
        assertEquals(1, h.lastParams?.size)
    }

    @Test
    fun `insert sends thing and data array`() = runTest {
        val h = Harness()
        h.client.insert("person", buildJsonArray { add(buildJsonObject { put("name", JsonPrimitive("X")) }) })
        assertEquals("insert", h.lastMethod)
        assertEquals(2, h.lastParams?.size)
        assertEquals(1, h.lastParams?.get(1)?.jsonArray?.size)
    }

    @Test
    fun `update sends thing and replacement data`() = runTest {
        val h = Harness()
        h.client.update("person:1", buildJsonObject { put("name", JsonPrimitive("New")) })
        assertEquals("update", h.lastMethod)
        assertEquals(2, h.lastParams?.size)
    }

    @Test
    fun `upsert sends thing and data`() = runTest {
        val h = Harness()
        h.client.upsert("person:1", buildJsonObject { put("name", JsonPrimitive("X")) })
        assertEquals("upsert", h.lastMethod)
        assertEquals(2, h.lastParams?.size)
    }

    @Test
    fun `merge sends thing and partial data`() = runTest {
        val h = Harness()
        h.client.merge("person:1", buildJsonObject { put("active", JsonPrimitive(true)) })
        assertEquals("merge", h.lastMethod)
        assertEquals(2, h.lastParams?.size)
    }

    @Test
    fun `patch sends thing and patches array`() = runTest {
        val h = Harness()
        h.client.patch(
            thing = "person:1",
            patches = buildJsonArray {
                add(buildJsonObject {
                    put("op", JsonPrimitive("replace"))
                    put("path", JsonPrimitive("/name"))
                    put("value", JsonPrimitive("Bob"))
                })
            },
        )
        assertEquals("patch", h.lastMethod)
        assertEquals(2, h.lastParams?.size)
        assertEquals("replace", h.lastParams?.get(1)?.jsonArray?.get(0)?.jsonObject?.get("op")?.jsonPrimitive?.content)
    }

    @Test
    fun `patch with diff sends third bool param`() = runTest {
        val h = Harness()
        h.client.patch("person:1", buildJsonArray {}, diff = true)
        assertEquals(3, h.lastParams?.size)
        assertEquals("true", h.lastParams?.get(2)?.jsonPrimitive?.content)
    }

    @Test
    fun `delete sends thing as single param`() = runTest {
        val h = Harness()
        h.client.delete("person:1")
        assertEquals("delete", h.lastMethod)
        assertEquals(1, h.lastParams?.size)
    }

    // ── graph ──

    @Test
    fun `relate sends in, relation, out as separate string params`() = runTest {
        val h = Harness()
        h.client.relate("person:a", "likes", "person:b")
        assertEquals("relate", h.lastMethod)
        // Regression: this must NOT collapse to a single "in->relation->out" string.
        assertEquals(3, h.lastParams?.size)
        assertEquals("person:a", h.lastParams?.get(0)?.jsonPrimitive?.content)
        assertEquals("likes", h.lastParams?.get(1)?.jsonPrimitive?.content)
        assertEquals("person:b", h.lastParams?.get(2)?.jsonPrimitive?.content)
    }

    @Test
    fun `relate with data appends a fourth param`() = runTest {
        val h = Harness()
        h.client.relate("person:a", "likes", "person:b", buildJsonObject { put("since", JsonPrimitive(2025)) })
        assertEquals(4, h.lastParams?.size)
    }

    @Test
    fun `insertRelation uses the insert_relation wire method`() = runTest {
        val h = Harness()
        h.client.insertRelation("person:a", "likes", "person:b")
        assertEquals("insert_relation", h.lastMethod)
        assertEquals(3, h.lastParams?.size)
    }

    // ── result propagation ──

    @Test
    fun `null result is returned as JsonNull`() = runTest {
        val h = Harness()
        h.stubResult = """{"id":"1","result":null}"""
        val result = h.client.ping()
        assertTrue(result is kotlinx.serialization.json.JsonNull)
    }

    @Test
    fun `complex result is returned verbatim`() = runTest {
        val h = Harness()
        h.stubResult = """{"id":"1","result":[{"a":1},{"b":2}]}"""
        val result = h.client.ping().jsonArray
        assertEquals(2, result.size)
    }

    @Test
    fun `missing id in response is tolerated`() = runTest {
        val h = Harness()
        h.stubResult = """{"result":{"ok":true}}"""
        val result = h.client.ping().jsonObject
        assertEquals("true", result["ok"]?.jsonPrimitive?.content)
    }

    companion object {
        private val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }

        private suspend fun readBody(request: HttpRequestData): String {
            val body = request.body
            val bytes = when (body) {
                is io.ktor.http.content.OutgoingContent.ByteArrayContent -> body.bytes()
                else -> error("unsupported body type: ${body::class.simpleName}")
            }
            return bytes.decodeToString()
        }
    }
}
