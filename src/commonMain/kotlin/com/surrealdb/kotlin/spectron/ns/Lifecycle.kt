package com.surrealdb.kotlin.spectron.ns

import com.surrealdb.kotlin.spectron.SpectronTransport
import com.surrealdb.kotlin.spectron.model.LifecycleResponseJson
import kotlinx.serialization.json.buildJsonObject

public class SpectronLifecycle internal constructor(
    private val transport: SpectronTransport,
    contextId: String,
) {
    private val base = "${enduserBase(contextId)}/lifecycle"

    public suspend fun expire(): LifecycleResponseJson {
        val body = transport.post("$base/expire", buildJsonObject {})
        return transport.json.decodeFromJsonElement(LifecycleResponseJson.serializer(), body!!)
    }

    public suspend fun decay(): LifecycleResponseJson {
        val body = transport.post("$base/decay", buildJsonObject {})
        return transport.json.decodeFromJsonElement(LifecycleResponseJson.serializer(), body!!)
    }
}
