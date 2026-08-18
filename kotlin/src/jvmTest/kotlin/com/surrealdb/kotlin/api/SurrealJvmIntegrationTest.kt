package com.surrealdb.kotlin.api

import com.surrealdb.kotlin.api.SurrealConnectionEvent
import com.surrealdb.kotlin.api.SurrealFeature
import com.surrealdb.kotlin.api.query.RecordId
import com.surrealdb.kotlin.api.query.Table
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test

class SurrealJvmIntegrationTest {
    @Test
    fun `runs full rpc and live integration flow`(): Unit = runBlocking {
        assumeTrue(System.getenv("SURREAL_RUN_INTEGRATION") == "true")
        val endpoint = System.getenv("SURREAL_JVM_ENDPOINT") ?: "http://127.0.0.1:8000"

        val client = SurrealClient(SurrealClientConfig(url = endpoint))
        try {
            // Engine capabilities
            assertTrue(SurrealFeature.ExportImport in client.features)

            client.signin(
                buildJsonObject {
                    put("user", JsonPrimitive("root"))
                    put("pass", JsonPrimitive("root"))
                },
            )
            client.use("main", "main")
            client.ping()
            client.version()
            assertNotNull(client.auth())

            client.query("DEFINE TABLE person SCHEMALESS")
            client.query("DEFINE TABLE likes SCHEMALESS")

            // Each CRUD builder compiles to a `query` RPC carrying SurrealQL.
            client.create(RecordId("person", "chiru"))
                .content(buildJsonObject {
                    put("name", JsonPrimitive("Chiru"))
                    put("age", JsonPrimitive(30))
                })
                .await()

            client.insert(
                Table("person"),
                buildJsonArray {
                    add(buildJsonObject {
                        put("id", JsonPrimitive("person:ada"))
                        put("name", JsonPrimitive("Ada"))
                    })
                },
            ).await()

            client.upsert(RecordId("person", "chiru"))
                .content(buildJsonObject {
                    put("name", JsonPrimitive("Chiru B"))
                    put("age", JsonPrimitive(31))
                })
                .await()

            client.update(RecordId("person", "chiru"))
                .content(buildJsonObject { put("name", JsonPrimitive("Chiru C")) })
                .await()

            client.merge(
                RecordId("person", "chiru"),
                buildJsonObject { put("active", JsonPrimitive(true)) },
            ).await()

            client.patch(
                RecordId("person", "chiru"),
                JsonArray(
                    listOf(
                        buildJsonObject {
                            put("op", JsonPrimitive("replace"))
                            put("path", JsonPrimitive("/name"))
                            put("value", JsonPrimitive("Chiru D"))
                        },
                    ),
                ),
            ).await()

            client.relate(
                RecordId("person", "chiru"),
                Table("likes"),
                RecordId("person", "ada"),
            )
                .content(buildJsonObject { put("strength", JsonPrimitive("high")) })
                .await()

            client.`let`("tb", JsonPrimitive("person"))
            val queryResult = client.query("SELECT * FROM type::table(\$tb)")
            assertTrue(queryResult.jsonArray.isNotEmpty())
            client.unset("tb")

            // Multi-session — sessionB shares the connection but has independent state
            val sessionB = client.newSession()
            sessionB.signin(
                buildJsonObject {
                    put("user", JsonPrimitive("root"))
                    put("pass", JsonPrimitive("root"))
                },
            )
            sessionB.use("main", "main")
            // Both sessions see the same data — compare the inner result, not the
            // outer envelope (which includes per-call timing).
            val countA = client.query("SELECT count() FROM person GROUP ALL")
                .jsonArray[0].jsonObject["result"]
            val countB = sessionB.query("SELECT count() FROM person GROUP ALL")
                .jsonArray[0].jsonObject["result"]
            assertEquals(countA.toString(), countB.toString())
            client.closeSession(sessionB)

            client.delete(RecordId("person", "chiru")).await()
            client.invalidate()
        } finally {
            client.close()
        }
    }

    @Test
    fun `client side transactions over websocket commit and cancel`(): Unit = runBlocking {
        assumeTrue(System.getenv("SURREAL_RUN_INTEGRATION") == "true")
        val httpEndpoint = System.getenv("SURREAL_JVM_ENDPOINT") ?: "http://127.0.0.1:8000"
        val wsEndpoint = httpEndpoint.replace("http://", "ws://").replace("https://", "wss://")

        val client = SurrealClient(SurrealClientConfig(url = wsEndpoint, autoConnect = true))
        try {
            assertTrue(SurrealFeature.Transactions in client.features)

            client.signin(
                buildJsonObject {
                    put("user", JsonPrimitive("root"))
                    put("pass", JsonPrimitive("root"))
                },
            )
            client.use("main", "main")
            client.query("DEFINE TABLE tx_person SCHEMALESS")
            client.query("DELETE tx_person")

            // Commit path
            client.transaction {
                create(RecordId("tx_person", "alice"))
                    .content(buildJsonObject { put("name", JsonPrimitive("Alice")) })
                    .await()
            }
            val afterCommit = client.query("SELECT * FROM tx_person")
                .jsonArray[0].jsonObject["result"]!!.jsonArray
            assertEquals(1, afterCommit.size)

            // Cancel path — the inner exception cancels the transaction.
            val cancelled = runCatching {
                client.transaction {
                    create(RecordId("tx_person", "bob"))
                        .content(buildJsonObject { put("name", JsonPrimitive("Bob")) })
                        .await()
                    error("bail out")
                }
            }
            assertTrue(cancelled.isFailure)
            val afterCancel = client.query("SELECT * FROM tx_person")
                .jsonArray[0].jsonObject["result"]!!.jsonArray
            assertEquals(1, afterCancel.size, "cancel should have rolled back bob")

            // Explicit handle form
            val tx = client.beginTransaction()
            tx.create(RecordId("tx_person", "carol"))
                .content(buildJsonObject { put("name", JsonPrimitive("Carol")) })
                .await()
            tx.commit()
            val afterExplicit = client.query("SELECT * FROM tx_person")
                .jsonArray[0].jsonObject["result"]!!.jsonArray
            assertEquals(2, afterExplicit.size)
        } finally {
            client.close()
        }
    }

    @Test
    fun `live queries and connection events over websocket`(): Unit = runBlocking {
        assumeTrue(System.getenv("SURREAL_RUN_INTEGRATION") == "true")
        val httpEndpoint = System.getenv("SURREAL_JVM_ENDPOINT") ?: "http://127.0.0.1:8000"
        val wsEndpoint = httpEndpoint.replace("http://", "ws://").replace("https://", "wss://")

        val client = SurrealClient(SurrealClientConfig(url = wsEndpoint, autoConnect = true))
        try {
            assertTrue(SurrealFeature.LiveQueries in client.features)

            // Wait for the engine to publish Connected
            val firstEvent = withTimeoutOrNull(5_000) {
                client.connectionEvents.firstOrNull { it is SurrealConnectionEvent.Connected }
            }
            assertNotNull(firstEvent)

            client.signin(
                buildJsonObject {
                    put("user", JsonPrimitive("root"))
                    put("pass", JsonPrimitive("root"))
                },
            )
            client.use("main", "main")
            client.query("DEFINE TABLE live_person SCHEMALESS")

            val subscription = client.live("live_person")
            client.create(RecordId("live_person", "one"))
                .content(buildJsonObject { put("name", JsonPrimitive("Live")) })
                .await()

            val event = withTimeout(10_000) { subscription.events.first() }
            assertEquals("CREATE", event.action)

            client.kill(subscription.id)
            subscription.cancel()
            client.delete(RecordId("live_person", "one")).await()
        } finally {
            client.close()
        }
    }
}
