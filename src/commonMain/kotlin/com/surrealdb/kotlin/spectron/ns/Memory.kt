package com.surrealdb.kotlin.spectron.ns

import com.surrealdb.kotlin.spectron.ScopeEntry
import com.surrealdb.kotlin.spectron.SpectronTransport
import com.surrealdb.kotlin.spectron.model.ChatReply
import com.surrealdb.kotlin.spectron.model.ContextResult
import com.surrealdb.kotlin.spectron.model.Entity
import com.surrealdb.kotlin.spectron.model.EntityHistoryEntry
import com.surrealdb.kotlin.spectron.model.ExtractionResult
import com.surrealdb.kotlin.spectron.model.ForgetResult
import com.surrealdb.kotlin.spectron.model.MemoryQueryResponse
import com.surrealdb.kotlin.spectron.model.ProfileResponse
import com.surrealdb.kotlin.spectron.model.ReflectionResult
import com.surrealdb.kotlin.spectron.model.SessionInfo
import com.surrealdb.kotlin.spectron.model.StructuredState
import com.surrealdb.kotlin.spectron.model.TraceListResponse
import com.surrealdb.kotlin.spectron.model.TraceRecord
import com.surrealdb.kotlin.spectron.model.TraceStats
import com.surrealdb.kotlin.spectron.model.Turn
import com.surrealdb.kotlin.spectron.model.TurnRole
import com.surrealdb.kotlin.spectron.quotePath
import com.surrealdb.kotlin.spectron.serialiseScope
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put

internal fun buildSessionCreatePayload(
    scope: Map<String, String>?,
    metadata: JsonObject?,
    transport: SpectronTransport,
): JsonObject = buildJsonObject {
    serialiseScope(scope)?.let {
        put(
            "scope",
            transport.json.encodeToJsonElement(
                ListSerializer(ScopeEntry.serializer()),
                it,
            ),
        )
    }
    metadata?.let { put("metadata", it) }
}

internal fun buildTurnPayload(role: TurnRole, content: String): JsonObject = buildJsonObject {
    put("role", role.wire)
    put("content", content)
}

internal fun buildContextPayload(query: String, k: Int?): JsonObject = buildJsonObject {
    put("query", query)
    k?.let { put("k", it) }
}

public class SpectronSession internal constructor(
    private val transport: SpectronTransport,
    contextId: String,
    public val info: SessionInfo,
) {
    private val base = "${enduserBase(contextId)}/sessions/${quotePath(info.id)}"

    public val id: String get() = info.id

    public suspend fun close() {
        transport.delete(base)
    }

    public suspend fun turn(role: TurnRole, content: String): ExtractionResult {
        val body = transport.post("$base/turns", buildTurnPayload(role, content))
        return transport.json.decodeFromJsonElement(ExtractionResult.serializer(), body!!)
    }

    public suspend fun turns(): List<Turn> {
        val body = transport.get("$base/turns") ?: return emptyList()
        val arr = when (body) {
            is JsonObject -> body["turns"] as? JsonArray ?: return emptyList()
            is JsonArray -> body
            else -> return emptyList()
        }
        return arr.map { transport.json.decodeFromJsonElement(Turn.serializer(), it) }
    }

    public suspend fun context(query: String, k: Int? = null): ContextResult {
        val body = transport.post("$base/context", buildContextPayload(query, k))
        return transport.json.decodeFromJsonElement(ContextResult.serializer(), body!!)
    }

    public suspend fun chat(message: String): ChatReply {
        val payload = buildJsonObject { put("message", message) }
        val body = transport.post("$base/chat", payload)
        return transport.json.decodeFromJsonElement(ChatReply.serializer(), body!!)
    }
}

public class SpectronSessions internal constructor(
    private val transport: SpectronTransport,
    private val contextId: String,
) {
    private val base = "${enduserBase(contextId)}/sessions"

    public suspend fun create(
        scope: Map<String, String>? = null,
        metadata: JsonObject? = null,
    ): SpectronSession {
        val body = transport.post(base, buildSessionCreatePayload(scope, metadata, transport))
            ?: error("Expected JSON object from session create, got null")
        val info = transport.json.decodeFromJsonElement(SessionInfo.serializer(), body)
        return SpectronSession(transport, contextId, info)
    }
}

public class SpectronEntities internal constructor(
    private val transport: SpectronTransport,
    contextId: String,
) {
    private val base = "${enduserBase(contextId)}/entities"

    public suspend fun list(type: String? = null): List<Entity> {
        val body = transport.get(base, mapOf("type" to type)) ?: return emptyList()
        val arr = when (body) {
            is JsonObject -> body["entities"] as? JsonArray ?: return emptyList()
            is JsonArray -> body
            else -> return emptyList()
        }
        return arr.map { transport.json.decodeFromJsonElement(Entity.serializer(), it) }
    }

    public suspend fun get(type: String, name: String): Entity {
        val body = transport.get("$base/${quotePath(type)}/${quotePath(name)}")
        return transport.json.decodeFromJsonElement(Entity.serializer(), body!!)
    }

    public suspend fun history(type: String, name: String, key: String): List<EntityHistoryEntry> {
        val body = transport.get(
            "$base/${quotePath(type)}/${quotePath(name)}/history/${quotePath(key)}",
        ) ?: return emptyList()
        val arr = when (body) {
            is JsonObject -> body["history"] as? JsonArray ?: return emptyList()
            is JsonArray -> body
            else -> return emptyList()
        }
        return arr.map { transport.json.decodeFromJsonElement(EntityHistoryEntry.serializer(), it) }
    }

    public suspend fun delete(type: String, name: String) {
        transport.delete("$base/${quotePath(type)}/${quotePath(name)}")
    }
}

public class SpectronLifecycle internal constructor(
    private val transport: SpectronTransport,
    contextId: String,
) {
    private val base = "${enduserBase(contextId)}/lifecycle"

    public suspend fun expire() {
        transport.post("$base/expire", buildJsonObject {})
    }

    public suspend fun decay() {
        transport.post("$base/decay", buildJsonObject {})
    }
}

public class SpectronTraces internal constructor(
    private val transport: SpectronTransport,
    contextId: String,
) {
    private val base = "${enduserBase(contextId)}/traces"

    public suspend fun list(limit: Int? = null): List<TraceRecord> {
        val body = transport.get(base, mapOf("limit" to limit)) ?: return emptyList()
        return when (body) {
            is JsonObject -> if (body.containsKey("traces"))
                transport.json.decodeFromJsonElement(TraceListResponse.serializer(), body).traces
            else emptyList()
            is JsonArray -> body.map {
                transport.json.decodeFromJsonElement(TraceRecord.serializer(), it)
            }
            else -> emptyList()
        }
    }

    public suspend fun get(traceId: String): TraceRecord {
        val body = transport.get("$base/${quotePath(traceId)}")
        return transport.json.decodeFromJsonElement(TraceRecord.serializer(), body!!)
    }

    public suspend fun stats(): TraceStats {
        val body = transport.get("$base/stats")
        return transport.json.decodeFromJsonElement(TraceStats.serializer(), body!!)
    }
}

public class SpectronMemory internal constructor(
    private val transport: SpectronTransport,
    private val contextId: String,
) {
    private val base = enduserBase(contextId)

    public val sessions: SpectronSessions = SpectronSessions(transport, contextId)
    public val entities: SpectronEntities = SpectronEntities(transport, contextId)
    public val lifecycle: SpectronLifecycle = SpectronLifecycle(transport, contextId)
    public val traces: SpectronTraces = SpectronTraces(transport, contextId)

    public suspend fun query(
        query: String,
        k: Int? = null,
        sessionId: String? = null,
    ): MemoryQueryResponse {
        val payload = buildJsonObject {
            put("query", query)
            k?.let { put("k", it) }
            sessionId?.let { put("sessionId", it) }
        }
        val body = transport.post("$base/query", payload)
        return transport.json.decodeFromJsonElement(MemoryQueryResponse.serializer(), body!!)
    }

    public suspend fun context(query: String, k: Int? = null): ContextResult {
        val body = transport.post("$base/context", buildContextPayload(query, k))
        return transport.json.decodeFromJsonElement(ContextResult.serializer(), body!!)
    }

    public suspend fun state(): StructuredState {
        val body = transport.get("$base/state")
        return transport.json.decodeFromJsonElement(StructuredState.serializer(), body!!)
    }

    public suspend fun profile(): ProfileResponse {
        val body = transport.get("$base/profile")
        return transport.json.decodeFromJsonElement(ProfileResponse.serializer(), body!!)
    }

    public suspend fun reflect(query: String, persist: Boolean = false): ReflectionResult {
        val payload = buildJsonObject {
            put("query", query)
            put("persist", persist)
        }
        val body = transport.post("$base/reflect", payload)
        return transport.json.decodeFromJsonElement(ReflectionResult.serializer(), body!!)
    }

    public suspend fun forget(query: String): ForgetResult {
        val payload = buildJsonObject { put("query", query) }
        val body = transport.post("$base/forget", payload) ?: return ForgetResult(deleted = 0)
        if (body is JsonPrimitive) {
            return ForgetResult(deleted = body.intOrNull ?: 0)
        }
        return transport.json.decodeFromJsonElement(ForgetResult.serializer(), body)
    }
}
