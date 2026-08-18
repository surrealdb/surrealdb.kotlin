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
import kotlinx.serialization.json.jsonArray
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
        assertEquals(emptyList(), normaliseScopeSets(null))
        assertEquals(emptyList(), scopeSets())
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
    fun scopeSetIsOneAndClause() {
        // Paths filed together become a single AND-clause: [["org/acme","team/eng"]].
        assertEquals(
            listOf(listOf("org/acme", "team/eng")),
            scopeSet(listOf("org/acme", "team/eng")),
        )
        assertEquals(listOf(listOf("team/eng")), scopeSet(listOf("team/eng")))
        assertEquals(listOf(listOf("org/acme")), scopeSet(mapOf("org" to "acme")))
        assertEquals(
            listOf(listOf("team/eng", "org/acme")),
            scopeSet("team" to "eng", "org" to "acme"),
        )
    }

    @Test
    fun scopeSetsAreOrOfClauses() {
        // Independent owners become separate clauses joined by OR.
        assertEquals(
            listOf(listOf("org/apple"), listOf("org/beta", "region/eu")),
            scopeSets(listOf("org/apple"), listOf("org/beta", "region/eu")),
        )
    }

    @Test
    fun scopeSetsNormaliseAndDropEmptyClauses() {
        assertEquals(
            listOf(listOf("org/acme")),
            scopeSets(listOf("org/acme", "org/acme"), listOf("", "")),
        )
        assertEquals(emptyList(), normaliseScopeSets(listOf(emptyList(), listOf(""))))
    }

    @Test
    fun scopesAreSentAsNestedDnfArray() = runTest {
        val recorded = mutableListOf<HttpRequestData>()
        val engine = MockEngine { req ->
            recorded += req
            respond(
                """{"id":"sess-1","scopes":[["org/acme"]],"createdAt":"now"}""",
                HttpStatusCode.OK,
                headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val s = Spectron("ctx", "sk", "https://api.spectron.dev", httpClient = HttpClient(engine))
        // One AND-clause across two paths, with a duplicate and an empty dropped.
        val session = s.sessions.create(scopes = scopeSet(listOf("org/acme", "team/eng", "org/acme", "")))
        val bodyText = (recorded.single().body as io.ktor.http.content.OutgoingContent.ByteArrayContent)
            .bytes().decodeToString()
        val scopes = Json.parseToJsonElement(bodyText).jsonObject["scopes"] as JsonArray
        assertEquals(1, scopes.size)
        val clause = scopes[0].jsonArray
        assertEquals(2, clause.size)
        assertEquals("org/acme", clause[0].jsonPrimitive.content)
        assertEquals("team/eng", clause[1].jsonPrimitive.content)
        // The response decodes the nested shape onto the session info.
        assertEquals(listOf(listOf("org/acme")), session.info.scopes)
    }

    @Test
    fun lensIsSentAsNestedDnfArray() = runTest {
        val recorded = mutableListOf<HttpRequestData>()
        val engine = MockEngine { req ->
            recorded += req
            respond(
                """{"hits":[]}""",
                HttpStatusCode.OK,
                headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val s = Spectron("ctx", "sk", "https://api.spectron.dev", httpClient = HttpClient(engine))
        // OR of two clauses: org/apple OR (org/beta AND region/eu).
        s.recall("incidents", lens = scopeSets(listOf("org/apple"), listOf("org/beta", "region/eu")))
        val bodyText = (recorded.single().body as io.ktor.http.content.OutgoingContent.ByteArrayContent)
            .bytes().decodeToString()
        val lens = Json.parseToJsonElement(bodyText).jsonObject["lens"] as JsonArray
        assertEquals(2, lens.size)
        assertEquals(listOf("org/apple"), lens[0].jsonArray.map { it.jsonPrimitive.content })
        assertEquals(listOf("org/beta", "region/eu"), lens[1].jsonArray.map { it.jsonPrimitive.content })
    }
}
