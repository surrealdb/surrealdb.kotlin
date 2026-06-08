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
import kotlin.test.assertTrue

private fun jsonEngine(body: String, record: MutableList<HttpRequestData>): HttpClient {
    val engine = MockEngine { request ->
        record += request
        respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
    }
    return HttpClient(engine)
}

class SpectronParityTest {

    @Test
    fun onBehalfOfSetsDelegationHeader() = runTest {
        val recorded = mutableListOf<HttpRequestData>()
        val s = Spectron("ctx", "sk", "https://api.spectron.dev", httpClient = jsonEngine("{}", recorded))
        s.state(onBehalfOf = "alpha-bot")
        val req = recorded.single()
        assertEquals("alpha-bot", req.headers["X-Spectron-On-Behalf-Of"])
    }

    @Test
    fun noDelegationHeaderWhenAbsent() = runTest {
        val recorded = mutableListOf<HttpRequestData>()
        val s = Spectron("ctx", "sk", "https://api.spectron.dev", httpClient = jsonEngine("{}", recorded))
        s.state()
        assertNull(recorded.single().headers["X-Spectron-On-Behalf-Of"])
    }

    @Test
    fun whoamiHitsMeEndpoint() = runTest {
        val recorded = mutableListOf<HttpRequestData>()
        val body = """{"principalId":"alpha-bot","displayName":"Alpha","kind":"agent","enforce":true}"""
        val s = Spectron("acme", "sk", "https://api.spectron.dev", httpClient = jsonEngine(body, recorded))
        val me = s.whoami()
        assertEquals("alpha-bot", me.principalId)
        assertEquals("agent", me.kind)
        assertTrue(me.enforce)
        assertEquals("https://api.spectron.dev/api/v1/acme/me", recorded.single().url.toString())
    }

    @Test
    fun healthIsNotContextScoped() = runTest {
        val recorded = mutableListOf<HttpRequestData>()
        val s = Spectron("acme", "sk", "https://api.spectron.dev", httpClient = jsonEngine("""{"status":"ok"}""", recorded))
        val health = s.health()
        assertEquals("ok", health["status"]?.let { (it as kotlinx.serialization.json.JsonPrimitive).content })
        assertEquals("https://api.spectron.dev/api/v1/health", recorded.single().url.toString())
    }

    @Test
    fun keysCreateSendsTtlParamAndBody() = runTest {
        val recorded = mutableListOf<HttpRequestData>()
        val body = """{"id":"key-1","key":"sp-key-1-secret","validUntil":"2026-12-31T00:00:00Z"}"""
        val s = Spectron("acme", "sk", "https://api.spectron.dev", httpClient = jsonEngine(body, recorded))
        val minted = s.keys.create(name = "ci", ttlSeconds = 3600, onBehalfOf = "alpha-bot")
        assertEquals("key-1", minted.id)
        assertEquals("sp-key-1-secret", minted.key)
        val req = recorded.single()
        assertEquals("POST", req.method.value)
        assertEquals("https://api.spectron.dev/api/v1/acme/keys?ttlSeconds=3600", req.url.toString())
        assertEquals("alpha-bot", req.headers["X-Spectron-On-Behalf-Of"])
    }

    @Test
    fun keysListDecodesBareArray() = runTest {
        val recorded = mutableListOf<HttpRequestData>()
        val body = """[{"id":"key-1","name":"ci","createdAt":"now"},{"id":"key-2","name":"prod","createdAt":"now"}]"""
        val s = Spectron("acme", "sk", "https://api.spectron.dev", httpClient = jsonEngine(body, recorded))
        val keys = s.keys.list()
        assertEquals(2, keys.size)
        assertEquals("ci", keys[0].name)
        assertEquals("prod", keys[1].name)
    }

    @Test
    fun fsckLivesUnderLifecycle() = runTest {
        val recorded = mutableListOf<HttpRequestData>()
        val body = """{"total":0,"contradictions":[],"duplicates":[],"injection":[]}"""
        val s = Spectron("acme", "sk", "https://api.spectron.dev", httpClient = jsonEngine(body, recorded))
        s.lifecycle.fsck(check = "contradictions")
        assertEquals("https://api.spectron.dev/api/v1/acme/fsck", recorded.single().url.toString())
    }
}
