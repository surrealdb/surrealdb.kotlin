package com.surrealdb.kotlin.spectron.ns

import com.surrealdb.kotlin.spectron.SpectronTransport
import com.surrealdb.kotlin.spectron.model.AttributeDetailJson
import com.surrealdb.kotlin.spectron.model.EntityDetailJson
import com.surrealdb.kotlin.spectron.model.EntityHistoryResponseJson
import com.surrealdb.kotlin.spectron.model.EntityListResponseJson
import com.surrealdb.kotlin.spectron.model.EntityResponseJson
import com.surrealdb.kotlin.spectron.quotePath

public class SpectronEntities internal constructor(
    private val transport: SpectronTransport,
    contextId: String,
) {
    private val base = "${enduserBase(contextId)}/entities"

    public suspend fun list(type: String? = null): List<EntityDetailJson> {
        val body = transport.get(base, mapOf("type" to type)) ?: return emptyList()
        return transport.json
            .decodeFromJsonElement(EntityListResponseJson.serializer(), body)
            .entities
    }

    public suspend fun get(type: String, name: String): EntityResponseJson {
        val body = transport.get("$base/${quotePath(type)}/${quotePath(name)}")
        return transport.json.decodeFromJsonElement(EntityResponseJson.serializer(), body!!)
    }

    public suspend fun history(type: String, name: String, key: String): List<AttributeDetailJson> {
        val body = transport.get(
            "$base/${quotePath(type)}/${quotePath(name)}/history/${quotePath(key)}",
        ) ?: return emptyList()
        return transport.json
            .decodeFromJsonElement(EntityHistoryResponseJson.serializer(), body)
            .history
    }

    public suspend fun delete(type: String, name: String) {
        transport.delete("$base/${quotePath(type)}/${quotePath(name)}")
    }
}
