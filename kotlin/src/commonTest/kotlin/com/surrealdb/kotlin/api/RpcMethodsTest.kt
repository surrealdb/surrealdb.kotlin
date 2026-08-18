package com.surrealdb.kotlin.api

import com.surrealdb.kotlin.api.query.RecordId
import com.surrealdb.kotlin.api.query.Table
import com.surrealdb.kotlin.api.query.eq
import com.surrealdb.kotlin.api.query.field
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Wire-format tests for every public RPC method. Asserts the JSON-RPC envelope
 * (method + params) that hits the transport, using a MockEngine to intercept
 * HTTP requests.
 *
 * Builder-style CRUD (select/create/...) all compile down to the `query` RPC,
 * so we verify the resulting SurrealQL fragment + bindings instead of the
 * legacy dedicated-RPC names.
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

        fun lastSurql(): String = lastParams?.get(0)?.jsonPrimitive?.content
            ?: error("no SurrealQL in last params")
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
    fun `auth uses query RPC with SELECT FROM ONLY $auth`() = runTest {
        val h = Harness()
        // Stub the wrapped `[{status, result}]` envelope shape.
        h.stubResult = """{"id":"1","result":[{"status":"OK","result":{"id":"u:1"}}]}"""
        h.client.auth()
        assertEquals("query", h.lastMethod)
        assertEquals("SELECT * FROM ONLY \$auth", h.lastSurql())
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

    // ── query ──

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

    // ── builder CRUD: each compiles to the `query` RPC ──

    private fun envelope(stub: String = "null") =
        """{"id":"1","result":[{"status":"OK","time":"1ms","result":$stub}]}"""

    @Test
    fun `select builder compiles to SELECT FROM ONLY with table binding`() = runTest {
        val h = Harness().apply { stubResult = envelope() }
        h.client.select(Table("person")).await()
        assertEquals("query", h.lastMethod)
        assertTrue(h.lastSurql().startsWith("SELECT * FROM ONLY type::table("))
        // Bound table name lives in the vars object (second param).
        val vars = h.lastParams?.get(1)?.jsonObject
        assertEquals("person", vars?.values?.firstOrNull()?.jsonPrimitive?.content)
    }

    @Test
    fun `select where compiles into a WHERE clause`() = runTest {
        val h = Harness().apply { stubResult = envelope() }
        h.client.select(Table("person")).where(field("age") eq 30).await()
        val surql = h.lastSurql()
        assertTrue(surql.contains("WHERE"), "expected WHERE in: $surql")
        assertTrue(surql.contains("age"), "expected 'age' in: $surql")
    }

    @Test
    fun `create builder compiles to CREATE ONLY with CONTENT binding`() = runTest {
        val h = Harness().apply { stubResult = envelope() }
        h.client.create(RecordId("person", "1"))
            .content(buildJsonObject { put("name", JsonPrimitive("Ada")) })
            .await()
        val surql = h.lastSurql()
        assertTrue(surql.startsWith("CREATE ONLY type::record("))
        assertTrue(surql.contains(" CONTENT "))
    }

    @Test
    fun `update builder compiles to UPDATE ONLY`() = runTest {
        val h = Harness().apply { stubResult = envelope() }
        h.client.update(RecordId("person", "1"))
            .content(buildJsonObject { put("name", JsonPrimitive("New")) })
            .await()
        assertTrue(h.lastSurql().startsWith("UPDATE ONLY type::record("))
    }

    @Test
    fun `upsert builder compiles to UPSERT ONLY`() = runTest {
        val h = Harness().apply { stubResult = envelope() }
        h.client.upsert(RecordId("person", "1"))
            .content(buildJsonObject { put("name", JsonPrimitive("X")) })
            .await()
        assertTrue(h.lastSurql().startsWith("UPSERT ONLY type::record("))
    }

    @Test
    fun `merge builder compiles to UPDATE ONLY MERGE`() = runTest {
        val h = Harness().apply { stubResult = envelope() }
        h.client.merge(RecordId("person", "1"), buildJsonObject { put("active", JsonPrimitive(true)) })
            .await()
        val surql = h.lastSurql()
        assertTrue(surql.startsWith("UPDATE ONLY type::record("))
        assertTrue(surql.contains(" MERGE "))
    }

    @Test
    fun `patch builder compiles to UPDATE ONLY PATCH`() = runTest {
        val h = Harness().apply { stubResult = envelope() }
        h.client.patch(RecordId("person", "1"), buildJsonObject { put("op", JsonPrimitive("replace")) })
            .await()
        val surql = h.lastSurql()
        assertTrue(surql.startsWith("UPDATE ONLY type::record("))
        assertTrue(surql.contains(" PATCH "))
    }

    @Test
    fun `patch builder with diff appends RETURN DIFF`() = runTest {
        val h = Harness().apply { stubResult = envelope() }
        h.client.patch(RecordId("person", "1"), buildJsonObject {}, diff = true)
            .await()
        assertTrue(h.lastSurql().endsWith(" RETURN DIFF"))
    }

    @Test
    fun `delete builder compiles to DELETE ONLY`() = runTest {
        val h = Harness().apply { stubResult = envelope() }
        h.client.delete(RecordId("person", "1")).await()
        assertTrue(h.lastSurql().startsWith("DELETE ONLY type::record("))
    }

    // ── graph ──

    @Test
    fun `relate builder compiles to RELATE arrow chain`() = runTest {
        val h = Harness().apply { stubResult = envelope() }
        h.client.relate(RecordId("person", "a"), Table("likes"), RecordId("person", "b")).await()
        val surql = h.lastSurql()
        assertTrue(surql.startsWith("RELATE "), "got $surql")
        assertTrue(surql.contains("->"))
    }

    @Test
    fun `insert builder compiles to INSERT INTO with bound data`() = runTest {
        val h = Harness().apply { stubResult = envelope() }
        h.client.insert(Table("person"), buildJsonObject { put("name", JsonPrimitive("A")) }).await()
        assertTrue(h.lastSurql().startsWith("INSERT INTO $"))
    }

    @Test
    fun `insertRelation builder compiles to INSERT RELATION INTO`() = runTest {
        val h = Harness().apply { stubResult = envelope() }
        h.client.insertRelation(Table("likes"), buildJsonObject { put("in", JsonPrimitive("p:a")) }).await()
        assertTrue(h.lastSurql().startsWith("INSERT RELATION INTO $"))
    }

    @Test
    fun `run builder compiles to function call with bound args`() = runTest {
        val h = Harness().apply { stubResult = envelope() }
        h.client.run("fn::greet").args("world").await()
        assertEquals("query", h.lastMethod)
        val surql = h.lastSurql()
        assertTrue(surql.startsWith("fn::greet("), "got $surql")
    }

    @Test
    fun `run with version emits angle-bracket version`() = runTest {
        val h = Harness().apply { stubResult = envelope() }
        h.client.run("fn::greet").version("1.0").args("world").await()
        assertTrue(h.lastSurql().startsWith("fn::greet<1.0>("))
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
