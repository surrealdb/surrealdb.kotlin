package com.surrealdb.kotlin.spectron.ns

import com.surrealdb.kotlin.spectron.SpectronTransport
import com.surrealdb.kotlin.spectron.model.AuditResponseJson
import com.surrealdb.kotlin.spectron.model.AuditRowJson
import com.surrealdb.kotlin.spectron.model.EffectiveGrantsJson
import com.surrealdb.kotlin.spectron.model.ForgetScopeResponseJson
import com.surrealdb.kotlin.spectron.model.PrincipalJson
import com.surrealdb.kotlin.spectron.model.ScopeNodeJson
import com.surrealdb.kotlin.spectron.quotePath
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

public class SpectronPrincipals internal constructor(
    private val transport: SpectronTransport,
    contextId: String,
) {
    private val base = "${enduserBase(contextId)}/principals"

    public suspend fun list(): List<PrincipalJson> {
        val body = transport.get(base) ?: return emptyList()
        return transport.json.decodeFromJsonElement(ListSerializer(PrincipalJson.serializer()), body)
    }

    public suspend fun get(principalId: String): PrincipalJson {
        val body = transport.get("$base/${quotePath(principalId)}")
        return transport.json.decodeFromJsonElement(PrincipalJson.serializer(), body!!)
    }

    public suspend fun effective(
        principalId: String,
        path: String,
        asOf: String? = null,
    ): EffectiveGrantsJson {
        val body = transport.get(
            "$base/${quotePath(principalId)}/effective",
            mapOf("path" to path, "asOf" to asOf),
        )
        return transport.json.decodeFromJsonElement(EffectiveGrantsJson.serializer(), body!!)
    }

    public suspend fun grant(
        principalId: String,
        path: String,
        verbs: List<String>,
    ): PrincipalJson {
        val body = transport.post("$base/${quotePath(principalId)}/grants", grantBody(path, verbs))
        return transport.json.decodeFromJsonElement(PrincipalJson.serializer(), body!!)
    }

    public suspend fun revoke(
        principalId: String,
        path: String,
        verbs: List<String>,
    ): PrincipalJson {
        val body = transport.delete("$base/${quotePath(principalId)}/grants", grantBody(path, verbs))
        return transport.json.decodeFromJsonElement(PrincipalJson.serializer(), body!!)
    }

    private fun grantBody(path: String, verbs: List<String>) = buildJsonObject {
        put("path", path)
        put("verbs", buildJsonArray { verbs.forEach { add(it) } })
    }
}

public class SpectronScopes internal constructor(
    private val transport: SpectronTransport,
    private val contextId: String,
) {
    private val base = "${enduserBase(contextId)}/scopes"

    public suspend fun list(): List<ScopeNodeJson> {
        val body = transport.get(base) ?: return emptyList()
        return transport.json.decodeFromJsonElement(ListSerializer(ScopeNodeJson.serializer()), body)
    }

    public suspend fun register(
        path: String,
        displayName: String? = null,
        description: String? = null,
    ): ScopeNodeJson {
        val payload = buildJsonObject {
            put("path", path)
            displayName?.let { put("displayName", it) }
            description?.let { put("description", it) }
        }
        val body = transport.post(base, payload)
        return transport.json.decodeFromJsonElement(ScopeNodeJson.serializer(), body!!)
    }

    public suspend fun delete(path: String) {
        transport.delete(base, params = mapOf("path" to path))
    }

    public suspend fun forget(path: String? = null): ForgetScopeResponseJson {
        val payload = buildJsonObject { path?.let { put("path", it) } }
        val body = transport.post("$base/forget", payload)
        return transport.json.decodeFromJsonElement(ForgetScopeResponseJson.serializer(), body!!)
    }

    /**
     * Locks the grant-request shape against the `POST /scope-grants` stub.
     * The endpoint currently returns 501; this exists for forward compatibility.
     */
    public suspend fun grantsStub(grants: Map<String, List<String>>, subject: String? = null) {
        val payload = buildJsonObject {
            put("grants", buildJsonObject {
                grants.forEach { (verb, patterns) ->
                    put(verb, buildJsonArray { patterns.forEach { add(it) } })
                }
            })
            subject?.let { put("subject", it) }
        }
        transport.post("${enduserBase(contextId)}/scope-grants", payload)
    }
}

public class SpectronAudit internal constructor(
    private val transport: SpectronTransport,
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
        ) ?: return emptyList()
        return transport.json.decodeFromJsonElement(AuditResponseJson.serializer(), body).rows
    }
}
