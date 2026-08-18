package com.surrealdb.kotlin.api

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlinx.cinterop.toKString
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import platform.posix.getenv

class SurrealIosIntegrationTest {
    @Test
    fun pingAndQuery() = runBlocking {
        val enabled = getenv("SURREAL_RUN_INTEGRATION")?.toKString() == "true"
        if (!enabled) {
            return@runBlocking
        }

        val endpoint = getenv("SURREAL_IOS_ENDPOINT")?.toKString() ?: "http://127.0.0.1:8000"

        val client = SurrealClient(
            SurrealClientConfig(url = endpoint),
        )

        client.signin(
            buildJsonObject {
                put("user", JsonPrimitive("root"))
                put("pass", JsonPrimitive("root"))
            },
        )
        client.use("main", "main")
        val result = client.query("SELECT * FROM person LIMIT 1")
        assertTrue(result.toString().isNotEmpty())
        client.close()
    }
}
