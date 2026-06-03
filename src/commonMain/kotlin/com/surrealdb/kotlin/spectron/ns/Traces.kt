package com.surrealdb.kotlin.spectron.ns

import com.surrealdb.kotlin.spectron.SpectronTransport
import com.surrealdb.kotlin.spectron.model.TraceListResponseJson
import com.surrealdb.kotlin.spectron.model.TraceRecordJson
import com.surrealdb.kotlin.spectron.model.TraceStatsResponseJson
import com.surrealdb.kotlin.spectron.quotePath

public class SpectronTraces internal constructor(
    private val transport: SpectronTransport,
    contextId: String,
) {
    private val base = "${enduserBase(contextId)}/traces"

    public suspend fun list(limit: Int? = null): List<TraceRecordJson> {
        val body = transport.get(base, mapOf("limit" to limit)) ?: return emptyList()
        return transport.json
            .decodeFromJsonElement(TraceListResponseJson.serializer(), body)
            .traces
    }

    public suspend fun get(traceId: String): TraceRecordJson {
        val body = transport.get("$base/${quotePath(traceId)}")
        return transport.json.decodeFromJsonElement(TraceRecordJson.serializer(), body!!)
    }

    public suspend fun stats(): TraceStatsResponseJson {
        val body = transport.get("$base/stats")
        return transport.json.decodeFromJsonElement(TraceStatsResponseJson.serializer(), body!!)
    }
}
