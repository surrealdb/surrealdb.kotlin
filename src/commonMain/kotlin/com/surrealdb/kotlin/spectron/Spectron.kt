package com.surrealdb.kotlin.spectron

import com.surrealdb.kotlin.spectron.model.ContextResult
import com.surrealdb.kotlin.spectron.model.ForgetResult
import com.surrealdb.kotlin.spectron.model.MemoryQueryResponse
import com.surrealdb.kotlin.spectron.model.ProfileResponse
import com.surrealdb.kotlin.spectron.model.ReflectionResult
import com.surrealdb.kotlin.spectron.model.StructuredState
import com.surrealdb.kotlin.spectron.ns.SpectronEntities
import com.surrealdb.kotlin.spectron.ns.SpectronKnowledge
import com.surrealdb.kotlin.spectron.ns.SpectronLifecycle
import com.surrealdb.kotlin.spectron.ns.SpectronMemory
import com.surrealdb.kotlin.spectron.ns.SpectronSessions
import com.surrealdb.kotlin.spectron.ns.SpectronTraces
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import kotlinx.serialization.json.Json
import kotlin.time.Duration

public class Spectron(
    public val contextId: String,
    apiKey: String,
    baseUrl: String = DEFAULT_BASE_URL,
    timeout: Duration = DEFAULT_TIMEOUT,
    maxRetries: Int = DEFAULT_MAX_RETRIES,
    httpClient: HttpClient? = null,
    json: Json = defaultSpectronJson,
) {
    private val transport: SpectronTransport
    public val knowledge: SpectronKnowledge
    public val memory: SpectronMemory
    public val sessions: SpectronSessions get() = memory.sessions
    public val entities: SpectronEntities get() = memory.entities
    public val lifecycle: SpectronLifecycle get() = memory.lifecycle
    public val traces: SpectronTraces get() = memory.traces

    init {
        require(apiKey.isNotEmpty()) { "Spectron API key is required" }
        val client = httpClient ?: HttpClient {
            install(HttpTimeout) {
                requestTimeoutMillis = timeout.inWholeMilliseconds
            }
        }
        transport = SpectronTransport(
            baseUrl = baseUrl.trimEnd('/'),
            apiKey = apiKey,
            httpClient = client,
            json = json,
            maxRetries = maxRetries,
            ownsClient = httpClient == null,
        )
        knowledge = SpectronKnowledge(transport, contextId)
        memory = SpectronMemory(transport, contextId)
    }

    public var baseUrl: String
        get() = transport.baseUrl
        set(value) {
            transport.baseUrl = value.trimEnd('/')
        }

    public var apiKey: String
        get() = transport.apiKey
        set(value) {
            require(value.isNotEmpty()) { "api_key must be a non-empty string" }
            transport.apiKey = value
        }

    public suspend fun query(
        query: String,
        k: Int? = null,
        sessionId: String? = null,
    ): MemoryQueryResponse = memory.query(query, k, sessionId)

    public suspend fun context(query: String, k: Int? = null): ContextResult =
        memory.context(query, k)

    public suspend fun state(): StructuredState = memory.state()

    public suspend fun profile(): ProfileResponse = memory.profile()

    public suspend fun reflect(query: String, persist: Boolean = false): ReflectionResult =
        memory.reflect(query, persist)

    public suspend fun forget(query: String): ForgetResult = memory.forget(query)

    public fun close() {
        transport.close()
    }
}

internal val defaultSpectronJson: Json = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
}
