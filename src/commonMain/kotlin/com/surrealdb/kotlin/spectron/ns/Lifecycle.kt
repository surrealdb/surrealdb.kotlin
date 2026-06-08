package com.surrealdb.kotlin.spectron.ns

import com.surrealdb.kotlin.spectron.SpectronTransport
import com.surrealdb.kotlin.spectron.model.FsckReportJson
import com.surrealdb.kotlin.spectron.model.LifecycleResponseJson
import com.surrealdb.kotlin.spectron.onBehalfOfHeader
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

public class SpectronLifecycle internal constructor(
    private val transport: SpectronTransport,
    contextId: String,
) {
    private val base = "${enduserBase(contextId)}/lifecycle"
    private val ctxBase = enduserBase(contextId)

    public suspend fun expire(onBehalfOf: String? = null): LifecycleResponseJson {
        val body = transport.post("$base/expire", buildJsonObject {}, onBehalfOfHeader(onBehalfOf))
        return transport.json.decodeFromJsonElement(LifecycleResponseJson.serializer(), body!!)
    }

    public suspend fun decay(onBehalfOf: String? = null): LifecycleResponseJson {
        val body = transport.post("$base/decay", buildJsonObject {}, onBehalfOfHeader(onBehalfOf))
        return transport.json.decodeFromJsonElement(LifecycleResponseJson.serializer(), body!!)
    }

    public suspend fun fsck(
        check: String? = null,
        duplicateThreshold: Double? = null,
        maxResults: Int? = null,
        onBehalfOf: String? = null,
    ): FsckReportJson {
        val payload = buildJsonObject {
            check?.let { put("check", it) }
            duplicateThreshold?.let { put("duplicateThreshold", it) }
            maxResults?.let { put("maxResults", it) }
        }
        val body = transport.post("$ctxBase/fsck", payload, onBehalfOfHeader(onBehalfOf))
        return transport.json.decodeFromJsonElement(FsckReportJson.serializer(), body!!)
    }
}
