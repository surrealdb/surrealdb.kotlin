package com.surrealdb.kotlin.spectron

import com.surrealdb.kotlin.spectron.model.GraphEdgeKind
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
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class SpectronTransportTest {

    @Test
    fun documentQueryBuildsCorrectRequest() = runTest {
        val recorded = mutableListOf<HttpRequestData>()
        val engine = MockEngine { request ->
            recorded += request
            respond(
                """{"queryMs":42,"results":[]}""",
                HttpStatusCode.OK,
                headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val s = Spectron("acme-prod", "sk-test", "https://api.spectron.dev", httpClient = HttpClient(engine))

        val resp = s.documents.query(
            "return window?",
            mode = QueryMode.HYBRID_GRAPH,
            k = 10,
            threshold = 0.5,
            graphEdges = listOf(GraphEdgeKind.KNOWLEDGE_HAS_KEYWORD),
        )

        assertEquals(42, resp.queryMs)
        assertEquals(1, recorded.size)
        val req = recorded.single()
        assertEquals("POST", req.method.value)
        assertEquals(
            "https://api.spectron.dev/api/v1/acme-prod/documents/query",
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
        assertEquals("knowledge_has_keyword", body["graphEdges"]?.jsonArray?.single()?.jsonPrimitive?.content)
    }

    @Test
    fun pathComponentsAreEscaped() = runTest {
        val recorded = mutableListOf<HttpRequestData>()
        val engine = MockEngine { request ->
            recorded += request
            respond(
                """{"contentHash":"h","createdAt":"now","id":"doc:abc","mimeType":"application/pdf","sizeBytes":1,"source":"src","status":"ready","title":"t","updatedAt":"now","version":1}""",
                HttpStatusCode.OK,
                headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val s = Spectron("acme-prod", "sk-test", "https://api.spectron.dev", httpClient = HttpClient(engine))
        s.documents.get("doc:with spaces & symbols")
        val req = recorded.single()
        val urlStr = req.url.toString()
        assertTrue("doc:with spaces & symbols" !in urlStr, "raw chars must be escaped: $urlStr")
        assertTrue("doc%3Awith%20spaces" in urlStr, "expected escaped form in $urlStr")
    }

    @Test
    fun notFoundMapsToTypedException() = runTest {
        val engine = MockEngine {
            respond(
                """{"message":"doc:xyz missing"}""",
                HttpStatusCode.NotFound,
                headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val s = Spectron("ctx", "sk", "https://api.spectron.dev", httpClient = HttpClient(engine))
        val ex = assertFailsWith<SpectronNotFoundException> {
            s.documents.get("doc:xyz")
        }
        assertEquals(404, ex.status)
        assertEquals("doc:xyz missing", ex.title)
    }

    @Test
    fun rateLimitParsesRetryAfter() = runTest {
        val engine = MockEngine {
            respond(
                """{"message":"Too many"}""",
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
            s.documents.query("x")
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
        s.documents.delete("doc:42")
        val req = recorded.single()
        assertEquals("DELETE", req.method.value)
        assertNull(req.headers[HttpHeaders.ContentType])
    }

    @Test
    fun forgetDecodesDeletedCount() = runTest {
        val engine = MockEngine {
            respond(
                """{"deleted":7}""",
                HttpStatusCode.OK,
                headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val s = Spectron("ctx", "sk", "https://api.spectron.dev", httpClient = HttpClient(engine))
        val r = s.forget("old job")
        assertEquals(7, r.deleted)
    }

    @Test
    fun endpointAndApiKeyAreMutable() = runTest {
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
        s.endpoint = "https://other.spectron.test/"
        s.state()
        assertEquals("Bearer sk-1", recorded[0].headers[HttpHeaders.Authorization])
        assertEquals("Bearer sk-2", recorded[1].headers[HttpHeaders.Authorization])
        assertTrue(recorded[1].url.toString().startsWith("https://other.spectron.test/api/v1/"))
    }

    @Test
    fun scopesAreSentAsNestedDnfArray() = runTest {
        val recorded = mutableListOf<HttpRequestData>()
        val engine = MockEngine { req ->
            recorded += req
            respond(
                """{"id":"sess-1","scopes":[["org=anneal/"]],"createdAt":"now"}""",
                HttpStatusCode.OK,
                headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val s = Spectron("ctx", "sk", "https://api.spectron.dev", httpClient = HttpClient(engine))
        // OR of two singleton clauses.
        s.sessions.create(scopes = scopeSets(listOf("org=anneal/"), listOf("user=tobie/")))
        val bodyText = (recorded.single().body as io.ktor.http.content.OutgoingContent.ByteArrayContent)
            .bytes().decodeToString()
        val body = Json.parseToJsonElement(bodyText).jsonObject
        val scopes = body["scopes"] as JsonArray
        assertEquals(listOf("org=anneal/"), scopes[0].jsonArray.map { it.jsonPrimitive.content })
        assertEquals(listOf("user=tobie/"), scopes[1].jsonArray.map { it.jsonPrimitive.content })
    }
}
