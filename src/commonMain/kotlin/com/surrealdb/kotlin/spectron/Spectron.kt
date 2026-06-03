package com.surrealdb.kotlin.spectron

import com.surrealdb.kotlin.spectron.model.BatchMessage
import com.surrealdb.kotlin.spectron.model.ChatResponseJson
import com.surrealdb.kotlin.spectron.model.ContextQueryResponseJson
import com.surrealdb.kotlin.spectron.model.FactsBatchResponseJson
import com.surrealdb.kotlin.spectron.model.FactsResponseJson
import com.surrealdb.kotlin.spectron.model.ForgetResponseJson
import com.surrealdb.kotlin.spectron.model.InferMode
import com.surrealdb.kotlin.spectron.model.MemoryCategory
import com.surrealdb.kotlin.spectron.model.ProfileResponseJson
import com.surrealdb.kotlin.spectron.model.QueryMemoryResponseJson
import com.surrealdb.kotlin.spectron.model.ReflectResponseJson
import com.surrealdb.kotlin.spectron.model.StateResponseJson
import com.surrealdb.kotlin.spectron.model.Triple
import com.surrealdb.kotlin.spectron.model.TurnRole
import com.surrealdb.kotlin.spectron.ns.SpectronAudit
import com.surrealdb.kotlin.spectron.ns.SpectronDocuments
import com.surrealdb.kotlin.spectron.ns.SpectronEntities
import com.surrealdb.kotlin.spectron.ns.SpectronLifecycle
import com.surrealdb.kotlin.spectron.ns.SpectronMemory
import com.surrealdb.kotlin.spectron.ns.SpectronPrincipals
import com.surrealdb.kotlin.spectron.ns.SpectronScopes
import com.surrealdb.kotlin.spectron.ns.SpectronSessions
import com.surrealdb.kotlin.spectron.ns.SpectronTraces
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import kotlinx.serialization.json.Json
import kotlin.time.Duration

public class Spectron(
    public val contextId: String,
    apiKey: String,
    endpoint: String,
    timeout: Duration = DEFAULT_TIMEOUT,
    maxRetries: Int = DEFAULT_MAX_RETRIES,
    httpClient: HttpClient? = null,
    json: Json = defaultSpectronJson,
) {
    private val transport: SpectronTransport
    public val documents: SpectronDocuments
    public val memory: SpectronMemory
    public val sessions: SpectronSessions
    public val entities: SpectronEntities
    public val lifecycle: SpectronLifecycle
    public val traces: SpectronTraces
    public val principals: SpectronPrincipals
    public val scopes: SpectronScopes
    public val audit: SpectronAudit

    init {
        require(apiKey.isNotEmpty()) { "Spectron API key is required" }
        require(endpoint.isNotEmpty()) { "Spectron endpoint is required" }
        val client = httpClient ?: HttpClient {
            install(HttpTimeout) {
                requestTimeoutMillis = timeout.inWholeMilliseconds
            }
        }
        transport = SpectronTransport(
            endpoint = endpoint.trimEnd('/'),
            apiKey = apiKey,
            httpClient = client,
            json = json,
            maxRetries = maxRetries,
            ownsClient = httpClient == null,
        )
        documents = SpectronDocuments(transport, contextId)
        memory = SpectronMemory(transport, contextId)
        sessions = SpectronSessions(transport, contextId)
        entities = SpectronEntities(transport, contextId)
        lifecycle = SpectronLifecycle(transport, contextId)
        traces = SpectronTraces(transport, contextId)
        principals = SpectronPrincipals(transport, contextId)
        scopes = SpectronScopes(transport, contextId)
        audit = SpectronAudit(transport, contextId)
    }

    public var endpoint: String
        get() = transport.endpoint
        set(value) {
            transport.endpoint = value.trimEnd('/')
        }

    public var apiKey: String
        get() = transport.apiKey
        set(value) {
            require(value.isNotEmpty()) { "api_key must be a non-empty string" }
            transport.apiKey = value
        }

    // ----------------------------------------------------------- convenience verbs

    /** Write a fact or triples. Maps to `POST /{ctx}/facts`. */
    public suspend fun remember(
        text: String? = null,
        infer: InferMode? = null,
        role: TurnRole? = null,
        memoryCategory: MemoryCategory? = null,
        triples: List<Triple>? = null,
        labels: List<String>? = null,
        scope: List<String>? = null,
        sessionId: String? = null,
    ): FactsResponseJson = memory.createFact(
        text, infer, role, memoryCategory, triples, labels, scope, sessionId,
    )

    /** Batch-write facts. Maps to `POST /{ctx}/facts/batch`. */
    public suspend fun rememberMany(
        messages: List<BatchMessage>,
        scope: List<String>? = null,
        sessionId: String? = null,
    ): FactsBatchResponseJson = memory.createFactsBatch(messages, scope = scope, sessionId = sessionId)

    /** Hybrid retrieval over facts and document passages. Maps to `POST /{ctx}/query`. */
    public suspend fun query(
        query: String,
        k: Int? = null,
        mode: String? = null,
        sessionId: String? = null,
    ): QueryMemoryResponseJson = memory.query(query, k = k, mode = mode, sessionId = sessionId)

    /** Assemble a context window for an agent prompt. Maps to `POST /{ctx}/context`. */
    public suspend fun context(query: String, k: Int? = null): ContextQueryResponseJson =
        memory.context(query, k)

    public suspend fun state(): StateResponseJson = memory.state()

    public suspend fun profile(): ProfileResponseJson = memory.profile()

    public suspend fun reflect(query: String, persist: Boolean = false): ReflectResponseJson =
        memory.reflect(query, persist)

    public suspend fun forget(query: String, purge: Boolean = false): ForgetResponseJson =
        memory.forget(query, purge)

    /** Server-driven turn: retrieve, generate, persist memory updates. Maps to `POST /{ctx}/chat`. */
    public suspend fun chat(
        message: String,
        sessionId: String? = null,
        scope: List<String>? = null,
        model: String? = null,
        bypassCache: Boolean = false,
    ): ChatResponseJson = memory.chat(message, sessionId, scope, model = model, bypassCache = bypassCache)

    public fun close() {
        transport.close()
    }
}

internal val defaultSpectronJson: Json = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
}
