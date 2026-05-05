package com.surrealdb.kotlin

import com.surrealdb.kotlin.engine.SurrealConnectionEvent
import com.surrealdb.kotlin.engine.SurrealFeature
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

            // create with specific record ID returns a single object in SurrealDB v2+
            val created = client.create(
                thing = "person:chiru",
                data = buildJsonObject {
                    put("name", JsonPrimitive("Chiru"))
                    put("age", JsonPrimitive(30))
                },
            )
            assertNotNull(created.jsonObject["id"])

            client.insert(
                thing = "person",
                data = buildJsonArray {
                    add(
                        buildJsonObject {
                            put("id", JsonPrimitive("person:ada"))
                            put("name", JsonPrimitive("Ada"))
                        },
                    )
                },
            )

            client.upsert(
                thing = "person:chiru",
                data = buildJsonObject {
                    put("name", JsonPrimitive("Chiru B"))
                    put("age", JsonPrimitive(31))
                },
            )

            client.update(
                thing = "person:chiru",
                data = buildJsonObject { put("name", JsonPrimitive("Chiru C")) },
            )

            client.merge(
                thing = "person:chiru",
                data = buildJsonObject { put("active", JsonPrimitive(true)) },
            )

            client.patch(
                thing = "person:chiru",
                patches = JsonArray(
                    listOf(
                        buildJsonObject {
                            put("op", JsonPrimitive("replace"))
                            put("path", JsonPrimitive("/name"))
                            put("value", JsonPrimitive("Chiru D"))
                        }
                    ),
                ),
            )

            client.relate(
                inRecord = "person:chiru",
                relation = "likes",
                outRecord = "person:ada",
                data = buildJsonObject { put("strength", JsonPrimitive("high")) },
            )

            client.`let`("tb", JsonPrimitive("person"))
            val queryResult = client.query("SELECT * FROM type::table(\$tb)")
            assertTrue(queryResult.jsonArray.isNotEmpty())
            client.unset("tb")

            // Transaction DSL
            client.transaction {
                query("CREATE person:tx SET name = 'Tx'")
            }

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

            client.delete("person:tx")
            client.invalidate()
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
            client.create(
                thing = "live_person:one",
                data = buildJsonObject { put("name", JsonPrimitive("Live")) },
            )

            val event = withTimeout(10_000) { subscription.events.first() }
            assertEquals("CREATE", event.action)

            client.kill(subscription.id)
            subscription.cancel()
            client.delete("live_person:one")
        } finally {
            client.close()
        }
    }
}
