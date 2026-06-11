package com.surrealdb.kotlin.spectron

import com.surrealdb.kotlin.spectron.model.AuditRowJson
import com.surrealdb.kotlin.spectron.model.BatchMessage
import com.surrealdb.kotlin.spectron.model.ChatResponseJson
import com.surrealdb.kotlin.spectron.model.ConsolidateResponseJson
import com.surrealdb.kotlin.spectron.model.ContextQueryResponseJson
import com.surrealdb.kotlin.spectron.model.ElaborateResponseJson
import com.surrealdb.kotlin.spectron.model.FactsBatchResponseJson
import com.surrealdb.kotlin.spectron.model.FactsResponseJson
import com.surrealdb.kotlin.spectron.model.ForgetResponseJson
import com.surrealdb.kotlin.spectron.model.GeoFilterJson
import com.surrealdb.kotlin.spectron.model.InferMode
import com.surrealdb.kotlin.spectron.model.InspectResponseJson
import com.surrealdb.kotlin.spectron.model.MemoryCategory
import com.surrealdb.kotlin.spectron.model.ProfileResponseJson
import com.surrealdb.kotlin.spectron.model.QueryMemoryResponseJson
import com.surrealdb.kotlin.spectron.model.ReflectResponseJson
import com.surrealdb.kotlin.spectron.model.StateResponseJson
import com.surrealdb.kotlin.spectron.model.Triple
import com.surrealdb.kotlin.spectron.model.TurnRole
import com.surrealdb.kotlin.spectron.model.WhoamiResponse
import com.surrealdb.kotlin.spectron.ns.SpectronAudit
import com.surrealdb.kotlin.spectron.ns.SpectronDocuments
import com.surrealdb.kotlin.spectron.ns.SpectronEntities
import com.surrealdb.kotlin.spectron.ns.SpectronKeys
import com.surrealdb.kotlin.spectron.ns.SpectronLifecycle
import com.surrealdb.kotlin.spectron.ns.SpectronMemory
import com.surrealdb.kotlin.spectron.ns.SpectronPrincipals
import com.surrealdb.kotlin.spectron.ns.SpectronScopes
import com.surrealdb.kotlin.spectron.ns.SpectronSessions
import com.surrealdb.kotlin.spectron.ns.SpectronTraces
import com.surrealdb.kotlin.spectron.ns.enduserBase
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import kotlinx.serialization.json.JsonObject
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
    private val base: String = enduserBase(contextId)
    private val mem: SpectronMemory
    private val auditApi: SpectronAudit

    public val documents: SpectronDocuments
    public val sessions: SpectronSessions
    public val entities: SpectronEntities
    public val lifecycle: SpectronLifecycle
    public val traces: SpectronTraces
    public val principals: SpectronPrincipals
    public val scopes: SpectronScopes
    public val keys: SpectronKeys

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
        mem = SpectronMemory(transport, contextId)
        auditApi = SpectronAudit(transport, contextId)
        documents = SpectronDocuments(transport, contextId)
        sessions = SpectronSessions(transport, contextId)
        entities = SpectronEntities(transport, contextId)
        lifecycle = SpectronLifecycle(transport, contextId)
        traces = SpectronTraces(transport, contextId)
        principals = SpectronPrincipals(transport, contextId)
        scopes = SpectronScopes(transport, contextId)
        keys = SpectronKeys(transport, contextId)
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

    // ----------------------------------------------------------- write verbs

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
        onBehalfOf: String? = null,
    ): FactsResponseJson = mem.createFact(
        text, infer, role, memoryCategory, triples, labels, scope, sessionId, onBehalfOf,
    )

    /** Batch-write facts. Maps to `POST /{ctx}/facts/batch`. */
    public suspend fun rememberMany(
        messages: List<BatchMessage>,
        scope: List<String>? = null,
        sessionId: String? = null,
        onBehalfOf: String? = null,
    ): FactsBatchResponseJson =
        mem.createFactsBatch(messages, scope = scope, sessionId = sessionId, onBehalfOf = onBehalfOf)

    // ----------------------------------------------------------- read verbs

    /** Hybrid retrieval over facts and document passages. Maps to `POST /{ctx}/query`. */
    public suspend fun recall(
        query: String,
        k: Int? = null,
        mode: String? = null,
        sessionId: String? = null,
        include: List<String>? = null,
        labels: List<String>? = null,
        lens: List<String>? = null,
        scopeView: String? = null,
        source: String? = null,
        asOf: String? = null,
        atInstant: String? = null,
        validFrom: String? = null,
        validUntil: String? = null,
        location: GeoFilterJson? = null,
        onBehalfOf: String? = null,
    ): QueryMemoryResponseJson = mem.query(
        query, k, mode, sessionId, include, labels, lens, scopeView, source,
        asOf, atInstant, validFrom, validUntil, location, onBehalfOf,
    )

    /** Assemble a context window for an agent prompt. Maps to `POST /{ctx}/context`. */
    public suspend fun queryContext(
        query: String,
        k: Int? = null,
        labels: List<String>? = null,
        lens: List<String>? = null,
        scopeView: String? = null,
        onBehalfOf: String? = null,
    ): ContextQueryResponseJson = mem.context(query, k, labels, lens, scopeView, onBehalfOf)

    public suspend fun state(onBehalfOf: String? = null): StateResponseJson = mem.state(onBehalfOf)

    public suspend fun profile(onBehalfOf: String? = null): ProfileResponseJson = mem.profile(onBehalfOf)

    public suspend fun reflect(
        query: String,
        persist: Boolean = false,
        onBehalfOf: String? = null,
    ): ReflectResponseJson = mem.reflect(query, persist, onBehalfOf)

    public suspend fun forget(
        query: String,
        purge: Boolean = false,
        onBehalfOf: String? = null,
    ): ForgetResponseJson = mem.forget(query, purge, onBehalfOf)

    /** Server-driven turn: retrieve, generate, persist memory updates. Maps to `POST /{ctx}/chat`. */
    public suspend fun chat(
        message: String,
        sessionId: String? = null,
        scope: List<String>? = null,
        model: String? = null,
        bypassCache: Boolean = false,
        onBehalfOf: String? = null,
    ): ChatResponseJson =
        mem.chat(message, sessionId, scope, model = model, bypassCache = bypassCache, onBehalfOf = onBehalfOf)

    // ----------------------------------------------------------- maintenance

    public suspend fun consolidate(
        dryRun: Boolean = false,
        factLimit: Int? = null,
        observationLimit: Int? = null,
        onBehalfOf: String? = null,
    ): ConsolidateResponseJson = mem.consolidate(dryRun, factLimit, observationLimit, onBehalfOf)

    public suspend fun elaborate(
        entityRef: String? = null,
        budget: Int? = null,
        dryRun: Boolean = false,
        sweep: Boolean = false,
        onBehalfOf: String? = null,
    ): ElaborateResponseJson = mem.elaborate(entityRef, budget, dryRun, sweep, onBehalfOf)

    public suspend fun inspect(
        ref: String,
        asOf: String? = null,
        atInstant: String? = null,
        validFrom: String? = null,
        validUntil: String? = null,
        onBehalfOf: String? = null,
    ): InspectResponseJson = mem.inspect(ref, asOf, atInstant, validFrom, validUntil, onBehalfOf)

    /** Recent governance audit rows. Maps to `GET /{ctx}/audit`. */
    public suspend fun audit(
        principal: String? = null,
        key: String? = null,
        kind: String? = null,
        since: String? = null,
        until: String? = null,
        limit: Int? = null,
        onBehalfOf: String? = null,
    ): List<AuditRowJson> = auditApi.list(principal, key, kind, since, until, limit, onBehalfOf)

    // ----------------------------------------------------------- introspection

    /** Caller identity and resolved grants. Maps to `GET /{ctx}/me`. */
    public suspend fun whoami(onBehalfOf: String? = null): WhoamiResponse {
        val body = transport.get("$base/me", headers = onBehalfOfHeader(onBehalfOf))
        return transport.json.decodeFromJsonElement(WhoamiResponse.serializer(), body!!)
    }

    /** Liveness probe. Not context-scoped, hits `GET /api/v1/health`. */
    public suspend fun health(): JsonObject {
        val body = transport.get("/api/v1/health")
        return body as? JsonObject ?: JsonObject(emptyMap())
    }

    public fun close() {
        transport.close()
    }
}

internal val defaultSpectronJson: Json = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
}
