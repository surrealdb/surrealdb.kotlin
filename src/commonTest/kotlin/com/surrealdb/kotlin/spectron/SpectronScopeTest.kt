package com.surrealdb.kotlin.spectron

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals

class SpectronScopeTest {

    @Test
    fun noneAndEmpty() {
        assertEquals(emptyList(), scopePaths(emptyList()))
        assertEquals(emptyList(), scopePaths(emptyMap()))
        assertEquals(emptyList(), normaliseScopePaths(null))
    }

    @Test
    fun stringPathsPassThrough() {
        assertEquals(listOf("team/eng"), scopePaths(listOf("team/eng")))
    }

    @Test
    fun mappingBecomesSlashPaths() {
        assertEquals(listOf("user/alex"), scopePaths(mapOf("user" to "alex")))
        assertEquals(
            listOf("team/eng", "org/acme"),
            scopePaths(linkedMapOf("team" to "eng", "org" to "acme")),
        )
    }

    @Test
    fun pairsBecomeSlashPaths() {
        assertEquals(
            listOf("team/eng", "org/acme"),
            scopePaths("team" to "eng", "org" to "acme"),
        )
    }

    @Test
    fun dedupPreservesOrderAndDropsEmpty() {
        assertEquals(
            listOf("org/acme", "team/eng"),
            scopePaths(listOf("org/acme", "team/eng", "org/acme")),
        )
        assertEquals(listOf("org/acme"), scopePaths(listOf("", "org/acme", "")))
    }

    @Test
    fun scopeIsSentAsNormalisedSlashPathList() = runTest {
        val recorded = mutableListOf<HttpRequestData>()
        val engine = MockEngine { req ->
            recorded += req
            respond(
                """{"id":"sess-1","scope":["org/acme"],"createdAt":"now"}""",
                HttpStatusCode.OK,
                headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val s = Spectron("ctx", "sk", "https://api.spectron.dev", httpClient = HttpClient(engine))
        s.sessions.create(scope = listOf("org/acme", "team/eng", "org/acme", ""))
        val bodyText = (recorded.single().body as io.ktor.http.content.OutgoingContent.ByteArrayContent)
            .bytes().decodeToString()
        val scope = Json.parseToJsonElement(bodyText).jsonObject["scope"] as JsonArray
        assertEquals(2, scope.size)
        assertEquals("org/acme", scope[0].jsonPrimitive.content)
        assertEquals("team/eng", scope[1].jsonPrimitive.content)
    }
}
