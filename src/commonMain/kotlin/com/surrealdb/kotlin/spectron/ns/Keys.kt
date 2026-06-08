package com.surrealdb.kotlin.spectron.ns

import com.surrealdb.kotlin.spectron.SpectronTransport
import com.surrealdb.kotlin.spectron.model.KeyDetail
import com.surrealdb.kotlin.spectron.model.MintedKey
import com.surrealdb.kotlin.spectron.onBehalfOfHeader
import com.surrealdb.kotlin.spectron.quotePath
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Self-service API key management. Maps to `/{ctx}/keys`. */
public class SpectronKeys internal constructor(
    private val transport: SpectronTransport,
    contextId: String,
) {
    private val base = "${enduserBase(contextId)}/keys"

    public suspend fun create(
        name: String? = null,
        grants: JsonObject? = null,
        ttlSeconds: Int? = null,
        onBehalfOf: String? = null,
    ): MintedKey {
        val payload = buildJsonObject {
            name?.let { put("name", it) }
            grants?.let { put("grants", it) }
        }
        val body = transport.post(
            base,
            body = payload.takeIf { it.isNotEmpty() },
            params = mapOf("ttlSeconds" to ttlSeconds),
            headers = onBehalfOfHeader(onBehalfOf),
        )
        return transport.json.decodeFromJsonElement(MintedKey.serializer(), body!!)
    }

    public suspend fun list(onBehalfOf: String? = null): List<KeyDetail> {
        val body = transport.get(base, headers = onBehalfOfHeader(onBehalfOf)) ?: return emptyList()
        return transport.json.decodeFromJsonElement(ListSerializer(KeyDetail.serializer()), body)
    }

    public suspend fun delete(keyName: String, onBehalfOf: String? = null) {
        transport.delete("$base/${quotePath(keyName)}", headers = onBehalfOfHeader(onBehalfOf))
    }

    public suspend fun rotate(
        keyName: String,
        ttlSeconds: Int? = null,
        onBehalfOf: String? = null,
    ): MintedKey {
        val body = transport.post(
            "$base/${quotePath(keyName)}/rotate",
            params = mapOf("ttlSeconds" to ttlSeconds),
            headers = onBehalfOfHeader(onBehalfOf),
        )
        return transport.json.decodeFromJsonElement(MintedKey.serializer(), body!!)
    }
}
