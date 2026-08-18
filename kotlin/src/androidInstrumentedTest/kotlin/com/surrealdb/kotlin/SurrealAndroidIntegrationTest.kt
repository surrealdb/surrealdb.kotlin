package com.surrealdb.kotlin

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SurrealAndroidIntegrationTest {
    @Test
    fun pingAndQuery() = runBlocking {
        assumeTrue(System.getenv("SURREAL_RUN_INTEGRATION") == "true")
        val endpoint = System.getenv("SURREAL_ANDROID_ENDPOINT") ?: "http://10.0.2.2:8000"

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
        client.ping()
        client.query("SELECT * FROM person LIMIT 1")
        client.close()
    }
}
