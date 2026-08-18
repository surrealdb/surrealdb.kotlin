package com.surrealdb.kotlin.api

import com.surrealdb.kotlin.api.SurrealConnectionEvent
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

class ConnectionEventsTest {

    private fun client(autoConnect: Boolean = false): SurrealClient {
        val engine = MockEngine { _ ->
            respond(
                content = """{"id":"1","result":null}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }
        return SurrealClient(
            SurrealClientConfig(
                url = "http://localhost:8000",
                autoConnect = autoConnect,
                httpClientFactory = { _ -> HttpClient(engine) },
            ),
        )
    }

    @Test
    fun `http engine publishes Connected when start is called`() = runBlocking {
        val c = client(autoConnect = false)

        // Start a collector before triggering connect so we don't miss the event.
        val received = mutableListOf<SurrealConnectionEvent>()
        val job = launch {
            c.connectionEvents.collect { received += it }
        }

        c.connect()

        // Give the SharedFlow a moment to deliver. We poll the list rather than
        // sleep to keep this test deterministic in virtual time.
        withTimeout(2_000) {
            while (received.isEmpty()) kotlinx.coroutines.yield()
        }

        assertTrue(received.first() is SurrealConnectionEvent.Connected, "got ${received.first()}")
        job.cancel()
    }

    @Test
    fun `http engine start is idempotent and does not double-emit Connected`() = runBlocking {
        val c = client(autoConnect = false)

        val received = mutableListOf<SurrealConnectionEvent>()
        val job = launch {
            c.connectionEvents.collect { received += it }
        }

        c.connect()
        c.connect() // second call should be a no-op

        withTimeout(2_000) {
            while (received.isEmpty()) kotlinx.coroutines.yield()
        }
        // Drain a small grace window in case a duplicate is in flight.
        repeat(8) { kotlinx.coroutines.yield() }

        val connectedCount = received.count { it is SurrealConnectionEvent.Connected }
        assertEquals(1, connectedCount, "expected exactly 1 Connected event, got $connectedCount (${received})")
        job.cancel()
    }

    @Test
    fun `http engine publishes Disconnected on close`() = runBlocking {
        val c = client(autoConnect = false)
        val received = mutableListOf<SurrealConnectionEvent>()
        val job = launch {
            c.connectionEvents.collect { received += it }
        }
        c.connect()
        c.close()

        withTimeout(2_000) {
            while (received.none { it is SurrealConnectionEvent.Disconnected }) {
                kotlinx.coroutines.yield()
            }
        }
        assertTrue(received.any { it is SurrealConnectionEvent.Disconnected })
        job.cancel()
    }
}
