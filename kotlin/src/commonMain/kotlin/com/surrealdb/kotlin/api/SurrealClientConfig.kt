package com.surrealdb.kotlin.api

import com.surrealdb.kotlin.api.ReconnectConfig
import io.ktor.client.HttpClient
import kotlinx.serialization.json.Json

public data class SurrealClientConfig(
    val url: String,
    val json: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = true
    },
    val autoAuthenticate: Boolean = false,
    val credentialProvider: (suspend () -> SurrealAuthInput?)? = null,
    val httpClientFactory: ((SurrealClientConfig) -> HttpClient)? = null,
    val requestTimeoutMillis: Long = 30_000,
    val reconnect: ReconnectConfig = ReconnectConfig(),
    /**
     * Schedule automatic JWT renewal this many milliseconds before the access
     * token's `exp` claim. Ignored when no refresh token is available.
     */
    val tokenRenewalLeadMillis: Long = 60_000,
    /**
     * Connect to the engine eagerly during `SurrealClient` construction.
     * Default is true to mirror surrealdb.js behavior.
     */
    val autoConnect: Boolean = true,
)
