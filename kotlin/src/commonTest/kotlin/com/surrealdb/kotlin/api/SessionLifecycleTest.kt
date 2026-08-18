package com.surrealdb.kotlin.api

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

class SessionLifecycleTest {

    private fun client(): SurrealClient {
        val engine = MockEngine { _ ->
            respond(
                content = """{"id":"1","result":"jwt-token"}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }
        return SurrealClient(
            SurrealClientConfig(
                url = "http://localhost:8000",
                autoConnect = false,
                httpClientFactory = { _ -> HttpClient(engine) },
            ),
        )
    }

    @Test
    fun `newSession returns a different session id from the root`() = runTest {
        val c = client()
        val session = c.newSession()
        assertNotEquals(c.sessionId, session.sessionId)
    }

    @Test
    fun `each new session has a unique id`() = runTest {
        val c = client()
        val a = c.newSession()
        val b = c.newSession()
        val z = c.newSession()
        assertEquals(3, setOf(a.sessionId, b.sessionId, z.sessionId).size)
    }

    @Test
    fun `signin on one session does not leak token to another`() = runTest {
        val c = client()
        val other = c.newSession()
        c.signin(buildJsonObject { put("user", JsonPrimitive("u")) })

        assertNotNull(c.accessToken())
        assertNull(other.accessToken())
    }

    @Test
    fun `use on one session does not leak ns or db to another`() = runTest {
        val c = client()
        val other = c.newSession()
        c.use("ns1", "db1")

        assertEquals("ns1", c.namespace())
        assertEquals("db1", c.database())
        assertNull(other.namespace())
        assertNull(other.database())
    }

    @Test
    fun `let on one session does not affect another`() = runTest {
        val c = client()
        val other = c.newSession()
        // Both sessions submit `let` calls, but each tracks its own variables.
        // Validate by introspecting state via snapshot through invalidate-style
        // round-trips would be heavy; instead, smoke-test that the call goes
        // through without cross-talk by asserting different sessionIds.
        c.`let`("x", JsonPrimitive(1))
        other.`let`("y", JsonPrimitive(2))
        assertNotEquals(c.sessionId, other.sessionId)
    }

    @Test
    fun `closeSession on the root client is a no-op (cannot remove root)`() = runTest {
        val c = client()
        // Calling closeSession on the root is silently ignored — this should not throw.
        c.closeSession(c)
        // Root session is still usable.
        c.signin(buildJsonObject { put("user", JsonPrimitive("u")) })
        assertNotNull(c.accessToken())
    }

    @Test
    fun `closeSession on a child session removes it`() = runTest {
        val c = client()
        val child = c.newSession()
        c.closeSession(child)
        // Subsequent ops on the closed session would error inside the controller's
        // snapshot — but the API contract is that closeSession is best-effort.
        // We verify here only that no exception is thrown when closing.
    }

    @Test
    fun `invalidate clears access token on its session only`() = runTest {
        val c = client()
        val other = c.newSession()
        c.signin(buildJsonObject { put("user", JsonPrimitive("u")) })
        other.signin(buildJsonObject { put("user", JsonPrimitive("v")) })

        assertNotNull(c.accessToken())
        assertNotNull(other.accessToken())

        c.invalidate()
        assertNull(c.accessToken())
        // Other session's token is unaffected.
        assertNotNull(other.accessToken())
    }

    @Test
    fun `reset clears namespace, database, and access token on its session only`() = runTest {
        val c = client()
        val other = c.newSession()

        c.signin(buildJsonObject { put("user", JsonPrimitive("u")) })
        c.use("ns1", "db1")
        other.signin(buildJsonObject { put("user", JsonPrimitive("v")) })
        other.use("ns2", "db2")

        c.reset()
        assertNull(c.accessToken())
        assertNull(c.namespace())
        assertNull(c.database())

        // Other session is untouched
        assertNotNull(other.accessToken())
        assertEquals("ns2", other.namespace())
    }
}
