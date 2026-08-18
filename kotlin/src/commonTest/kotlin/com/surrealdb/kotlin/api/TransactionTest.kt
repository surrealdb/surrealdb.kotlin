package com.surrealdb.kotlin.api

import com.surrealdb.kotlin.api.query.Table
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject

/**
 * Common-tier tests for client-side transactions: verify the HTTP engine
 * rejects transactions with a clear feature-not-supported error. The
 * happy-path round-trip (begin → query-with-txn → commit/cancel) lives in
 * `jvmTest/SurrealJvmIntegrationTest.kt` because it requires a real
 * SurrealDB WebSocket.
 */
class TransactionTest {

    private fun httpClient(): SurrealClient {
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
                autoConnect = false,
                httpClientFactory = { _ -> HttpClient(engine) },
            ),
        )
    }

    @Test
    fun `beginTransaction on http engine reports feature not supported`() = runTest {
        assertFailsWith<com.surrealdb.kotlin.api.error.SurrealFeatureNotSupportedException> {
            httpClient().beginTransaction()
        }
    }

    @Test
    fun `transaction block on http engine surfaces the feature error`() = runTest {
        val client = httpClient()
        assertFailsWith<com.surrealdb.kotlin.api.error.SurrealFeatureNotSupportedException> {
            client.transaction {
                create(Table("person")).content(buildJsonObject {}).await()
            }
        }
    }
}
