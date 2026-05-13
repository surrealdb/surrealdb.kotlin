package com.surrealdb.kotlin.spectron

import com.surrealdb.kotlin.spectron.model.QueryMode
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class SpectronTransportTest {

    @Test
    fun knowledgeQueryBuildsCorrectRequest() = runTest {
        val recorded = mutableListOf<HttpRequestData>()
        val engine = MockEngine { request ->
            recorded += request
            respond(
                """{"query_ms":42,"results":[]}""",
                HttpStatusCode.OK,
                headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val s = Spectron("acme-prod", "sk-test", "https://api.spectron.dev", httpClient = HttpClient(engine))

        val resp = s.knowledge.query(
            "return window?",
            mode = QueryMode.HYBRID_GRAPH,
            k = 10,
            threshold = 0.5,
            graphEdges = listOf("knowledge_has_keyword"),
        )

        assertEquals(42, resp.queryMs)
        assertEquals(1, recorded.size)
        val req = recorded.single()
        assertEquals("POST", req.method.value)
        assertEquals(
            "https://api.spectron.dev/api/v1/acme-prod/knowledge/query",
            req.url.toString(),
        )
        assertEquals("Bearer sk-test", req.headers[HttpHeaders.Authorization])
        assertEquals("application/json", req.headers[HttpHeaders.Accept])
        assertTrue(req.headers[HttpHeaders.UserAgent]?.startsWith("surrealdb-kotlin-spectron/") == true)

        val bodyText = (req.body as io.ktor.http.content.OutgoingContent.ByteArrayContent)
            .bytes().decodeToString()
        val body = Json.parseToJsonElement(bodyText).jsonObject
        assertEquals("return window?", body["query"]?.jsonPrimitive?.content)
        assertEquals("hybrid_graph", body["mode"]?.jsonPrimitive?.content)
        assertEquals("10", body["k"]?.jsonPrimitive?.content)
    }

    @Test
    fun pathComponentsAreEscaped() = runTest {
        val recorded = mutableListOf<HttpRequestData>()
        val engine = MockEngine { request ->
            recorded += request
            respond(
                """{"content_hash":"h","created_at":"now","id":"doc:abc","mime_type":"application/pdf","size_bytes":1,"source":"src","status":"ready","title":"t","updated_at":"now","version":1}""",
                HttpStatusCode.OK,
                headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val s = Spectron("acme-prod", "sk-test", "https://api.spectron.dev", httpClient = HttpClient(engine))
        s.knowledge.get("doc:with spaces & symbols")
        val req = recorded.single()
        val urlStr = req.url.toString()
        assertTrue("doc:with spaces & symbols" !in urlStr, "raw chars must be escaped: $urlStr")
        assertTrue("doc%3Awith%20spaces" in urlStr, "expected escaped form in $urlStr")
    }

    @Test
    fun notFoundMapsToTypedException() = runTest {
        val engine = MockEngine {
            respond(
                """{"title":"Not found","detail":"doc:xyz missing"}""",
                HttpStatusCode.NotFound,
                headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val s = Spectron("ctx", "sk", "https://api.spectron.dev", httpClient = HttpClient(engine))
        val ex = assertFailsWith<SpectronNotFoundException> {
            s.knowledge.get("doc:xyz")
        }
        assertEquals(404, ex.status)
        assertEquals("Not found", ex.title)
        assertEquals("doc:xyz missing", ex.detail)
    }

    @Test
    fun rateLimitParsesRetryAfter() = runTest {
        val engine = MockEngine {
            respond(
                """{"title":"Too many"}""",
                HttpStatusCode.TooManyRequests,
                headersOf(
                    HttpHeaders.ContentType to listOf("application/json"),
                    "Retry-After" to listOf("2.5"),
                ),
            )
        }
        val s = Spectron("ctx", "sk", "https://api.spectron.dev", httpClient = HttpClient(engine))
        val ex = assertFailsWith<SpectronRateLimitException> { s.state() }
        assertEquals(429, ex.status)
        assertEquals(2.5.seconds, ex.retryAfter)
    }

    @Test
    fun getRetriesOn503() = runTest {
        var calls = 0
        val engine = MockEngine {
            calls++
            if (calls < 3) {
                respondError(HttpStatusCode.ServiceUnavailable)
            } else {
                respond(
                    """{"identity":null}""",
                    HttpStatusCode.OK,
                    headersOf(HttpHeaders.ContentType, "application/json"),
                )
            }
        }
        val s = Spectron("ctx", "sk", "https://api.spectron.dev", httpClient = HttpClient(engine))
        s.state()
        assertEquals(3, calls)
    }

    @Test
    fun postDoesNotRetryOn503() = runTest {
        var calls = 0
        val engine = MockEngine {
            calls++
            respondError(HttpStatusCode.ServiceUnavailable)
        }
        val s = Spectron("ctx", "sk", "https://api.spectron.dev", httpClient = HttpClient(engine))
        assertFailsWith<SpectronServerException> {
            s.knowledge.query("x")
        }
        assertEquals(1, calls)
    }

    @Test
    fun deleteSendsNoBodyAndAccepts204() = runTest {
        val recorded = mutableListOf<HttpRequestData>()
        val engine = MockEngine { req ->
            recorded += req
            respond("", HttpStatusCode.NoContent)
        }
        val s = Spectron("ctx", "sk", "https://api.spectron.dev", httpClient = HttpClient(engine))
        s.knowledge.delete("doc:42")
        val req = recorded.single()
        assertEquals("DELETE", req.method.value)
        assertNull(req.headers[HttpHeaders.ContentType])
    }

    @Test
    fun forgetAcceptsIntPrimitive() = runTest {
        val engine = MockEngine {
            respond("7", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        }
        val s = Spectron("ctx", "sk", "https://api.spectron.dev", httpClient = HttpClient(engine))
        val r = s.forget("old job")
        assertEquals(7, r.deleted)
    }

    @Test
    fun baseUrlAndApiKeyAreMutable() = runTest {
        val recorded = mutableListOf<HttpRequestData>()
        val engine = MockEngine { req ->
            recorded += req
            respond(
                """{"identity":null}""",
                HttpStatusCode.OK,
                headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val s = Spectron("ctx", "sk-1", "https://api.spectron.dev", httpClient = HttpClient(engine))
        s.state()
        s.apiKey = "sk-2"
        s.baseUrl = "https://other.spectron.test/"
        s.state()
        assertEquals("Bearer sk-1", recorded[0].headers[HttpHeaders.Authorization])
        assertEquals("Bearer sk-2", recorded[1].headers[HttpHeaders.Authorization])
        assertTrue(recorded[1].url.toString().startsWith("https://other.spectron.test/api/v1/"))
    }

    @Test
    fun scopeIsSentAsListOfKeyValuePairs() = runTest {
        val recorded = mutableListOf<HttpRequestData>()
        val engine = MockEngine { req ->
            recorded += req
            respond(
                """{"id":"sess-1"}""",
                HttpStatusCode.OK,
                headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val s = Spectron("ctx", "sk", "https://api.spectron.dev", httpClient = HttpClient(engine))
        s.sessions.create(scope = mapOf("org" to "anneal", "user" to "tobie"))
        val bodyText = (recorded.single().body as io.ktor.http.content.OutgoingContent.ByteArrayContent)
            .bytes().decodeToString()
        val body = Json.parseToJsonElement(bodyText).jsonObject
        val scope = body["scope"]
        assertNotNull(scope)
        val list = (scope as kotlinx.serialization.json.JsonArray).map { it.jsonObject }
        assertEquals("org", list[0]["key"]?.jsonPrimitive?.content)
        assertEquals("anneal", list[0]["value"]?.jsonPrimitive?.content)
        assertEquals("user", list[1]["key"]?.jsonPrimitive?.content)
    }
}
