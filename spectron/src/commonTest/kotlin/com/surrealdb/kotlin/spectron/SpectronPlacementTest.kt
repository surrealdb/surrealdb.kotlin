package com.surrealdb.kotlin.spectron

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

private fun engineOf(body: String, record: MutableList<HttpRequestData>): HttpClient {
    val engine = MockEngine { request ->
        record += request
        respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
    }
    return HttpClient(engine)
}

/** Verifies the client-level method placement matches the surrealdb.py Spectron client. */
class SpectronPlacementTest {

    @Test
    fun recallPostsToQuery() = runTest {
        val rec = mutableListOf<HttpRequestData>()
        val s = Spectron("ctx", "sk", "https://api.spectron.dev", httpClient = engineOf("""{"hits":[]}""", rec))
        val r = s.recall("who is tobie", k = 5)
        assertEquals(0, r.hits.size)
        val req = rec.single()
        assertEquals("POST", req.method.value)
        assertEquals("https://api.spectron.dev/api/v1/ctx/query", req.url.toString())
    }

    @Test
    fun queryContextPostsToContext() = runTest {
        val rec = mutableListOf<HttpRequestData>()
        val s = Spectron("ctx", "sk", "https://api.spectron.dev", httpClient = engineOf("""{"context":"c"}""", rec))
        val r = s.queryContext("brief", k = 3)
        assertEquals("c", r.context)
        assertEquals("https://api.spectron.dev/api/v1/ctx/context", rec.single().url.toString())
    }

    @Test
    fun consolidateIsClientLevel() = runTest {
        val rec = mutableListOf<HttpRequestData>()
        val body = """{"created":0,"updated":0,"superseded":0,"dryRun":true,"outcomes":[]}"""
        val s = Spectron("ctx", "sk", "https://api.spectron.dev", httpClient = engineOf(body, rec))
        val r = s.consolidate(dryRun = true)
        assertEquals(true, r.dryRun)
        assertEquals("https://api.spectron.dev/api/v1/ctx/consolidate", rec.single().url.toString())
    }

    @Test
    fun elaborateAndInspectAreClientLevel() = runTest {
        val rec = mutableListOf<HttpRequestData>()
        val s = Spectron("ctx", "sk", "https://api.spectron.dev", httpClient = engineOf("""{"kind":"entity"}""", rec))
        val ins = s.inspect("entity:Person/tobie")
        assertEquals("entity", ins.kind)
        assertEquals("https://api.spectron.dev/api/v1/ctx/inspect?ref=entity%3APerson%2Ftobie", rec.single().url.toString())
    }

    @Test
    fun auditIsClientLevelAndReturnsRows() = runTest {
        val rec = mutableListOf<HttpRequestData>()
        val body = """{"rows":[{"createdAt":"now","kind":"query","traceId":"t1","cost":0.1,"latencyMs":12,"rowsTouched":3}]}"""
        val s = Spectron("ctx", "sk", "https://api.spectron.dev", httpClient = engineOf(body, rec))
        val rows = s.audit(principal = "alpha-bot", limit = 10)
        assertEquals(1, rows.size)
        assertEquals("t1", rows[0].traceId)
        val req = rec.single()
        assertEquals("GET", req.method.value)
        assertEquals("alpha-bot", req.url.parameters["principal"])
    }

    @Test
    fun reprocessIsNoBodyPut() = runTest {
        val rec = mutableListOf<HttpRequestData>()
        val body = """{"contentHash":"h","deduplicated":false,"id":"doc:1","status":"queued"}"""
        val s = Spectron("ctx", "sk", "https://api.spectron.dev", httpClient = engineOf(body, rec))
        val r = s.documents.reprocess("doc:1")
        assertEquals("doc:1", r.id)
        val req = rec.single()
        assertEquals("PUT", req.method.value)
        assertEquals("https://api.spectron.dev/api/v1/ctx/documents/doc%3A1", req.url.toString())
        assertNull(req.headers[HttpHeaders.ContentType])
    }

    @Test
    fun sessionsContextAndTurnsAreFlatById() = runTest {
        val rec = mutableListOf<HttpRequestData>()
        val s = Spectron("ctx", "sk", "https://api.spectron.dev", httpClient = engineOf("""{"context":"c"}""", rec))
        s.sessions.context("sess-1", "what changed")
        assertEquals("https://api.spectron.dev/api/v1/ctx/sessions/sess-1/context", rec.single().url.toString())

        rec.clear()
        val s2 = Spectron("ctx", "sk", "https://api.spectron.dev", httpClient = engineOf("""{"turns":[]}""", rec))
        s2.sessions.turns("sess-1", limit = 10, offset = 5)
        val req = rec.single()
        assertEquals("GET", req.method.value)
        assertEquals("10", req.url.parameters["limit"])
        assertEquals("5", req.url.parameters["offset"])
    }
}
