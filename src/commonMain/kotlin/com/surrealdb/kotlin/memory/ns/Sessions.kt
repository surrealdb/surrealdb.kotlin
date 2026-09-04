package com.surrealdb.kotlin.memory.ns

import com.surrealdb.kotlin.memory.AgentMemoryTransport
import com.surrealdb.kotlin.memory.model.ChatResponseJson
import com.surrealdb.kotlin.memory.model.FactsResponseJson
import com.surrealdb.kotlin.memory.model.InferMode
import com.surrealdb.kotlin.memory.model.MemoryCategory
import com.surrealdb.kotlin.memory.model.SessionContextResponseJson
import com.surrealdb.kotlin.memory.model.SessionResponseJson
import com.surrealdb.kotlin.memory.model.Triple
import com.surrealdb.kotlin.memory.model.TurnListResponseJson
import com.surrealdb.kotlin.memory.model.TurnResponseJson
import com.surrealdb.kotlin.memory.model.TurnRole
import com.surrealdb.kotlin.memory.onBehalfOfHeader
import com.surrealdb.kotlin.memory.quotePath
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

public class AgentMemorySession internal constructor(
    private val transport: AgentMemoryTransport,
    private val contextId: String,
    public val info: SessionResponseJson,
) {
    private val ctxBase = enduserBase(contextId)
    private val base = "$ctxBase/sessions/${quotePath(info.id)}"

    public val id: String get() = info.id

    public suspend fun close(onBehalfOf: String? = null) {
        transport.delete(base, headers = onBehalfOfHeader(onBehalfOf))
    }

    public suspend fun turns(
        limit: Int? = null,
        offset: Int? = null,
        onBehalfOf: String? = null,
    ): List<TurnResponseJson> {
        val body = transport.get(
            "$base/turns",
            mapOf("limit" to limit, "offset" to offset),
            onBehalfOfHeader(onBehalfOf),
        ) ?: return emptyList()
        return transport.json
            .decodeFromJsonElement(TurnListResponseJson.serializer(), body)
            .turns
    }

    public suspend fun context(query: String, onBehalfOf: String? = null): SessionContextResponseJson {
        val payload = buildJsonObject { put("query", query) }
        val body = transport.post("$base/context", payload, onBehalfOfHeader(onBehalfOf))
        return transport.json.decodeFromJsonElement(SessionContextResponseJson.serializer(), body!!)
    }

    /** Persist a turn against this session. Maps to `POST /{ctx}/facts` with this session id. */
    public suspend fun remember(
        text: String? = null,
        infer: InferMode? = null,
        role: TurnRole? = null,
        memoryCategory: MemoryCategory? = null,
        triples: List<Triple>? = null,
        labels: List<String>? = null,
        onBehalfOf: String? = null,
    ): FactsResponseJson =
        MemoryNamespace(transport, contextId).createFact(
            text = text,
            infer = infer,
            role = role,
            memoryCategory = memoryCategory,
            triples = triples,
            labels = labels,
            sessionId = id,
            onBehalfOf = onBehalfOf,
        )

    /** Server-driven turn against this session. Maps to `POST /{ctx}/chat` with this session id. */
    public suspend fun chat(
        message: String,
        labels: List<String>? = null,
        model: String? = null,
        bypassCache: Boolean = false,
        onBehalfOf: String? = null,
    ): ChatResponseJson =
        MemoryNamespace(transport, contextId).chat(
            message = message,
            sessionId = id,
            labels = labels,
            model = model,
            bypassCache = bypassCache,
            onBehalfOf = onBehalfOf,
        )
}

public class SessionsNamespace internal constructor(
    private val transport: AgentMemoryTransport,
    private val contextId: String,
) {
    private val base = "${enduserBase(contextId)}/sessions"

    public suspend fun create(
        scopes: List<List<String>>? = null,
        metadata: JsonObject? = null,
        onBehalfOf: String? = null,
    ): AgentMemorySession {
        val payload = buildJsonObject {
            putScopeSets("scopes", scopes)
            metadata?.let { put("metadata", it) }
        }
        val body = transport.post(base, payload, onBehalfOfHeader(onBehalfOf))
            ?: error("Expected JSON object from session create, got null")
        val info = transport.json.decodeFromJsonElement(SessionResponseJson.serializer(), body)
        return AgentMemorySession(transport, contextId, info)
    }

    /** Delete a session by id. Maps to `DELETE /{ctx}/sessions/{id}`. */
    public suspend fun delete(sessionId: String, onBehalfOf: String? = null) {
        transport.delete("$base/${quotePath(sessionId)}", headers = onBehalfOfHeader(onBehalfOf))
    }

    /** Assemble a context window scoped to a session. Maps to `POST /{ctx}/sessions/{id}/context`. */
    public suspend fun context(
        sessionId: String,
        query: String,
        onBehalfOf: String? = null,
    ): SessionContextResponseJson {
        val payload = buildJsonObject { put("query", query) }
        val body = transport.post("$base/${quotePath(sessionId)}/context", payload, onBehalfOfHeader(onBehalfOf))
        return transport.json.decodeFromJsonElement(SessionContextResponseJson.serializer(), body!!)
    }

    /** List the turns recorded against a session. Maps to `GET /{ctx}/sessions/{id}/turns`. */
    public suspend fun turns(
        sessionId: String,
        limit: Int? = null,
        offset: Int? = null,
        onBehalfOf: String? = null,
    ): List<TurnResponseJson> {
        val body = transport.get(
            "$base/${quotePath(sessionId)}/turns",
            mapOf("limit" to limit, "offset" to offset),
            onBehalfOfHeader(onBehalfOf),
        ) ?: return emptyList()
        return transport.json
            .decodeFromJsonElement(TurnListResponseJson.serializer(), body)
            .turns
    }
}
