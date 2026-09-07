package com.surrealdb.kotlin.memory.ns

import com.surrealdb.kotlin.memory.AgentMemoryTransport
import com.surrealdb.kotlin.memory.model.TraceListResponseJson
import com.surrealdb.kotlin.memory.model.TraceRecordJson
import com.surrealdb.kotlin.memory.model.TraceStatsResponseJson
import com.surrealdb.kotlin.memory.onBehalfOfHeader
import com.surrealdb.kotlin.memory.quotePath

public class TracesNamespace internal constructor(
    private val transport: AgentMemoryTransport,
    contextId: String,
) {
    private val base = "${enduserBase(contextId)}/traces"

    public suspend fun list(limit: Int? = null, onBehalfOf: String? = null): List<TraceRecordJson> {
        val body = transport.get(base, mapOf("limit" to limit), onBehalfOfHeader(onBehalfOf)) ?: return emptyList()
        return transport.json
            .decodeFromJsonElement(TraceListResponseJson.serializer(), body)
            .traces
    }

    public suspend fun get(traceId: String, onBehalfOf: String? = null): TraceRecordJson {
        val body = transport.get("$base/${quotePath(traceId)}", headers = onBehalfOfHeader(onBehalfOf))
        return transport.json.decodeFromJsonElement(TraceRecordJson.serializer(), body!!)
    }

    public suspend fun stats(onBehalfOf: String? = null): TraceStatsResponseJson {
        val body = transport.get("$base/stats", headers = onBehalfOfHeader(onBehalfOf))
        return transport.json.decodeFromJsonElement(TraceStatsResponseJson.serializer(), body!!)
    }
}
