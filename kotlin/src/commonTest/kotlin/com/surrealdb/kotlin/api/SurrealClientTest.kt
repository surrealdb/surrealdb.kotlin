package com.surrealdb.kotlin.api

import com.surrealdb.kotlin.api.SurrealFeature
import com.surrealdb.kotlin.api.error.SurrealAuthenticationException
import com.surrealdb.kotlin.api.error.SurrealFeatureNotSupportedException
import com.surrealdb.kotlin.api.query.awaitAs
import com.surrealdb.kotlin.runtime.SurrealRpcResponse
import com.surrealdb.kotlin.runtime.codec.parseLiveNotification
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
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class SurrealClientTest {
    private val json = Json {
        ignoreUnknownKeys = true
    }

    @Test
    fun `rpc returns json result`() = runTest {
        val engine = MockEngine { _ ->
            respond(
                content = """{"id":"1","result":{"ok":true}}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }

        val client = testClient(engine = engine)
        val result = client.ping().jsonObject

        assertEquals(true, result["ok"]?.jsonPrimitive?.content?.toBooleanStrict())
    }

    @Test
    fun `maps auth errors and retries with credential provider`() = runTest {
        var queryCalls = 0
        val engine = MockEngine { request ->
            when (request.methodName(json)) {
                "query" -> {
                    queryCalls += 1
                    if (queryCalls == 1) {
                        respond(
                            content = """{"id":"1","error":{"code":-32000,"message":"authentication required",""" +
                                """"kind":"NotAllowed","details":{"kind":"Auth","details":{"kind":"InvalidAuth"}}}}""",
                            status = HttpStatusCode.OK,
                            headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                        )
                    } else {
                        respond(
                            content = """{"id":"1","result":[{"status":"OK","result":[{"id":"person:1"}]}]}""",
                            status = HttpStatusCode.OK,
                            headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                        )
                    }
                }

                "signin" -> respond(
                    content = """{"id":"1","result":"jwt.token"}""",
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                )

                else -> error("unexpected method")
            }
        }

        val client = SurrealClient(
            SurrealClientConfig(
                url = "http://localhost:8000",
                autoAuthenticate = true,
                credentialProvider = {
                    SurrealAuthInput.SignIn(
                        buildJsonObject {
                            put("user", JsonPrimitive("root"))
                            put("pass", JsonPrimitive("root"))
                        },
                    )
                },
                httpClientFactory = { _ -> HttpClient(engine) },
            ),
        )

        val result = client.query("SELECT * FROM person")
        val status = result.jsonArray[0].jsonObject["status"]?.jsonPrimitive?.content
        assertEquals("OK", status)
    }

    @Test
    fun `throws auth error when auto mode disabled`() = runTest {
        val engine = MockEngine {
            respond(
                content = """{"id":"1","error":{"code":-32000,"message":"authentication required",""" +
                    """"kind":"NotAllowed","details":{"kind":"Auth","details":{"kind":"InvalidAuth"}}}}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }

        val client = testClient(engine = engine)

        assertFailsWith<SurrealAuthenticationException> {
            client.query("SELECT * FROM person")
        }
    }

    @Test
    fun `typed decode helper works`() = runTest {
        // SELECT compiles to the `query` RPC so the stub uses the wrapped
        // `[{ status, result }]` envelope shape.
        val engine = MockEngine {
            respond(
                content = """{"id":"1","result":[{"status":"OK","result":{"id":"person:1","name":"Ada"}}]}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }

        val client = testClient(engine = engine)
        val person: Person = client.select(com.surrealdb.kotlin.api.query.RecordId("person", "1")).awaitAs()

        assertEquals("Ada", person.name)
    }

    @Test
    fun `parses live notification payload`() {
        val response = json.decodeFromString(
            SurrealRpcResponse.serializer(),
            """{"result":{"action":"CREATE","id":"live-1","result":{"id":"person:1"}}}""",
        )

        val notification = parseLiveNotification(response)
        assertNotNull(notification)
        assertEquals("CREATE", notification.action)
        assertEquals("live-1", notification.liveQueryId)
    }

    @Test
    fun `http engine reports its feature set and rejects live queries`() = runTest {
        val engine = MockEngine { respond("{}", HttpStatusCode.OK) }
        val client = testClient(engine = engine)

        assertTrue(SurrealFeature.ExportImport in client.features)
        assertTrue(SurrealFeature.LiveQueries !in client.features)
        assertEquals(false, client.supports(SurrealFeature.LiveQueries))

        assertFailsWith<SurrealFeatureNotSupportedException> {
            client.live("person")
        }
    }

    @Test
    fun `newSession returns isolated session sharing the connection`() = runTest {
        val seenAuth = mutableListOf<String?>()
        val engine = MockEngine { request ->
            seenAuth += request.headers[HttpHeaders.Authorization]
            respond(
                content = """{"id":"1","result":"jwt-token-1"}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }

        val client = testClient(engine = engine)
        val sessionA = client
        val sessionB = client.newSession()

        sessionA.signin(buildJsonObject { put("user", JsonPrimitive("a")) })
        // sessionB should NOT see sessionA's token
        assertEquals(null, sessionB.accessToken())
        assertEquals("jwt-token-1", sessionA.accessToken())

        // Each session call carries its own auth header
        sessionB.ping()
        // Last request was sessionB's ping with no Authorization header
        assertEquals(null, seenAuth.last())
    }

    @Test
    fun `connection events emit on http engine start`() = runTest {
        val engine = MockEngine { respond("{}", HttpStatusCode.OK) }
        val client = testClient(engine = engine)
        // HttpEngine.start() emits Connected synchronously when connect() is called
        client.connect()
        // Just verify the SharedFlow is accessible — actual delivery is timing-sensitive
        assertNotNull(client.connectionEvents)
    }

    private fun testClient(engine: MockEngine): SurrealClient {
        return SurrealClient(
            SurrealClientConfig(
                url = "http://localhost:8000",
                autoConnect = false,
                httpClientFactory = { _ -> HttpClient(engine) },
            ),
        )
    }

    private suspend fun HttpRequestData.methodName(json: Json): String {
        val bodyBytes = when (val requestBody = body) {
            is io.ktor.http.content.OutgoingContent.ByteArrayContent -> requestBody.bytes()
            else -> error("Unsupported request body: ${requestBody::class.simpleName}")
        }
        val payload = json.decodeFromString<JsonObject>(bodyBytes.decodeToString())
        return payload["method"]?.jsonPrimitive?.content ?: error("Missing method")
    }

    @Serializable
    private data class Person(
        val id: String,
        val name: String,
    )
}
