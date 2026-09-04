package com.surrealdb.kotlin.memory.ns

import com.surrealdb.kotlin.memory.AgentMemoryTransport
import com.surrealdb.kotlin.memory.model.AuditResponseJson
import com.surrealdb.kotlin.memory.model.AuditRowJson
import com.surrealdb.kotlin.memory.model.EffectiveGrantsJson
import com.surrealdb.kotlin.memory.model.ForgetScopeResponseJson
import com.surrealdb.kotlin.memory.model.PrincipalJson
import com.surrealdb.kotlin.memory.model.ScopeNodeJson
import com.surrealdb.kotlin.memory.onBehalfOfHeader
import com.surrealdb.kotlin.memory.quotePath
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

public class PrincipalsNamespace internal constructor(
    private val transport: AgentMemoryTransport,
    contextId: String,
) {
    private val base = "${enduserBase(contextId)}/principals"

    public suspend fun list(onBehalfOf: String? = null): List<PrincipalJson> {
        val body = transport.get(base, headers = onBehalfOfHeader(onBehalfOf)) ?: return emptyList()
        return transport.json.decodeFromJsonElement(ListSerializer(PrincipalJson.serializer()), body)
    }

    public suspend fun get(principalId: String, onBehalfOf: String? = null): PrincipalJson {
        val body = transport.get("$base/${quotePath(principalId)}", headers = onBehalfOfHeader(onBehalfOf))
        return transport.json.decodeFromJsonElement(PrincipalJson.serializer(), body!!)
    }

    public suspend fun effective(
        principalId: String,
        path: String,
        asOf: String? = null,
        onBehalfOf: String? = null,
    ): EffectiveGrantsJson {
        val body = transport.get(
            "$base/${quotePath(principalId)}/effective",
            mapOf("path" to path, "asOf" to asOf),
            onBehalfOfHeader(onBehalfOf),
        )
        return transport.json.decodeFromJsonElement(EffectiveGrantsJson.serializer(), body!!)
    }

    public suspend fun grant(
        principalId: String,
        path: String,
        verbs: List<String>,
        onBehalfOf: String? = null,
    ): PrincipalJson {
        val body = transport.post(
            "$base/${quotePath(principalId)}/grants",
            grantBody(path, verbs),
            onBehalfOfHeader(onBehalfOf),
        )
        return transport.json.decodeFromJsonElement(PrincipalJson.serializer(), body!!)
    }

    public suspend fun revoke(
        principalId: String,
        path: String,
        verbs: List<String>,
        onBehalfOf: String? = null,
    ): PrincipalJson {
        val body = transport.delete(
            "$base/${quotePath(principalId)}/grants",
            grantBody(path, verbs),
            headers = onBehalfOfHeader(onBehalfOf),
        )
        return transport.json.decodeFromJsonElement(PrincipalJson.serializer(), body!!)
    }

    private fun grantBody(path: String, verbs: List<String>) = buildJsonObject {
        put("path", path)
        put("verbs", buildJsonArray { verbs.forEach { add(it) } })
    }
}

public class ScopesNamespace internal constructor(
    private val transport: AgentMemoryTransport,
    private val contextId: String,
) {
    private val base = "${enduserBase(contextId)}/scopes"

    public suspend fun list(onBehalfOf: String? = null): List<ScopeNodeJson> {
        val body = transport.get(base, headers = onBehalfOfHeader(onBehalfOf)) ?: return emptyList()
        return transport.json.decodeFromJsonElement(ListSerializer(ScopeNodeJson.serializer()), body)
    }

    public suspend fun register(
        path: String,
        displayName: String? = null,
        description: String? = null,
        onBehalfOf: String? = null,
    ): ScopeNodeJson {
        val payload = buildJsonObject {
            put("path", path)
            displayName?.let { put("displayName", it) }
            description?.let { put("description", it) }
        }
        val body = transport.post(base, payload, onBehalfOfHeader(onBehalfOf))
        return transport.json.decodeFromJsonElement(ScopeNodeJson.serializer(), body!!)
    }

    public suspend fun delete(path: String, onBehalfOf: String? = null) {
        transport.delete(base, params = mapOf("path" to path), headers = onBehalfOfHeader(onBehalfOf))
    }

    public suspend fun forget(path: String? = null, onBehalfOf: String? = null): ForgetScopeResponseJson {
        val payload = buildJsonObject { path?.let { put("path", it) } }
        val body = transport.post("$base/forget", payload, onBehalfOfHeader(onBehalfOf))
        return transport.json.decodeFromJsonElement(ForgetScopeResponseJson.serializer(), body!!)
    }

    /**
     * Locks the grant-request shape against the `POST /scope-grants` stub.
     * The endpoint currently returns 501; this exists for forward compatibility.
     */
    public suspend fun grantsStub(
        grants: Map<String, List<String>>,
        subject: String? = null,
        onBehalfOf: String? = null,
    ) {
        val payload = buildJsonObject {
            put("grants", buildJsonObject {
                grants.forEach { (verb, patterns) ->
                    put(verb, buildJsonArray { patterns.forEach { add(it) } })
                }
            })
            subject?.let { put("subject", it) }
        }
        transport.post("${enduserBase(contextId)}/scope-grants", payload, onBehalfOfHeader(onBehalfOf))
    }
}

public class AuditNamespace internal constructor(
    private val transport: AgentMemoryTransport,
    contextId: String,
) {
    private val base = "${enduserBase(contextId)}/audit"

    public suspend fun list(
        principal: String? = null,
        key: String? = null,
        kind: String? = null,
        since: String? = null,
        until: String? = null,
        limit: Int? = null,
        onBehalfOf: String? = null,
    ): List<AuditRowJson> {
        val body = transport.get(
            base,
            mapOf(
                "principal" to principal,
                "key" to key,
                "kind" to kind,
                "since" to since,
                "until" to until,
                "limit" to limit,
            ),
            onBehalfOfHeader(onBehalfOf),
        ) ?: return emptyList()
        return transport.json.decodeFromJsonElement(AuditResponseJson.serializer(), body).rows
    }
}
