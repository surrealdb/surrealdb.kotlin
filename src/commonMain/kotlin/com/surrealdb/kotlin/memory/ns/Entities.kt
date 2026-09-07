package com.surrealdb.kotlin.memory.ns

import com.surrealdb.kotlin.memory.AgentMemoryTransport
import com.surrealdb.kotlin.memory.model.AttributeDetailJson
import com.surrealdb.kotlin.memory.model.EntityDetailJson
import com.surrealdb.kotlin.memory.model.EntityHistoryResponseJson
import com.surrealdb.kotlin.memory.model.EntityListResponseJson
import com.surrealdb.kotlin.memory.model.EntityResponseJson
import com.surrealdb.kotlin.memory.onBehalfOfHeader
import com.surrealdb.kotlin.memory.quotePath

public class EntitiesNamespace internal constructor(
    private val transport: AgentMemoryTransport,
    contextId: String,
) {
    private val base = "${enduserBase(contextId)}/entities"

    public suspend fun list(type: String? = null, onBehalfOf: String? = null): List<EntityDetailJson> {
        val body = transport.get(base, mapOf("type" to type), onBehalfOfHeader(onBehalfOf)) ?: return emptyList()
        return transport.json
            .decodeFromJsonElement(EntityListResponseJson.serializer(), body)
            .entities
    }

    public suspend fun get(type: String, name: String, onBehalfOf: String? = null): EntityResponseJson {
        val body = transport.get(
            "$base/${quotePath(type)}/${quotePath(name)}",
            headers = onBehalfOfHeader(onBehalfOf),
        )
        return transport.json.decodeFromJsonElement(EntityResponseJson.serializer(), body!!)
    }

    public suspend fun history(
        type: String,
        name: String,
        key: String,
        onBehalfOf: String? = null,
    ): List<AttributeDetailJson> {
        val body = transport.get(
            "$base/${quotePath(type)}/${quotePath(name)}/history/${quotePath(key)}",
            headers = onBehalfOfHeader(onBehalfOf),
        ) ?: return emptyList()
        return transport.json
            .decodeFromJsonElement(EntityHistoryResponseJson.serializer(), body)
            .history
    }

    public suspend fun delete(type: String, name: String, onBehalfOf: String? = null) {
        transport.delete(
            "$base/${quotePath(type)}/${quotePath(name)}",
            headers = onBehalfOfHeader(onBehalfOf),
        )
    }
}
