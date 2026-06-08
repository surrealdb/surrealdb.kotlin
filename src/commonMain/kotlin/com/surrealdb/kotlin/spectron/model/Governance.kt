package com.surrealdb.kotlin.spectron.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

// ---------------------------------------------------------------------- audit

@Serializable
public data class AuditRowJson(
    val cost: Double = 0.0,
    val createdAt: String,
    val kind: String,
    val latencyMs: Int = 0,
    val rowsTouched: Int = 0,
    val traceId: String,
    val model: String? = null,
    val principal: String? = null,
)

@Serializable
public data class AuditResponseJson(
    val rows: List<AuditRowJson> = emptyList(),
)

// ------------------------------------------------------------------ principals

/**
 * Per-verb scope-pattern map. Keys are grant verbs (`read`, `write`,
 * `create_scope`, `delete_scope`, `grant`, `manage`, `forget`); values are the
 * scope patterns granted for that verb.
 */
public typealias Grants = Map<String, List<String>>

@Serializable
public data class PrincipalJson(
    val displayName: String,
    val grants: Grants = emptyMap(),
    val id: String,
    val kind: String,
)

@Serializable
public data class EffectiveGrantsJson(
    val path: String,
    val verbs: List<String> = emptyList(),
    val asOf: String? = null,
)

/** The `GET /{ctx}/me` response: the caller's identity and resolved grants. */
@Serializable
public data class WhoamiResponse(
    val principalId: String = "",
    val displayName: String = "",
    val kind: String = "",
    val enforce: Boolean = false,
    val grants: JsonObject? = null,
    val effectiveGrants: JsonObject? = null,
    val delegatedPrincipalId: String? = null,
    val tokenGrants: JsonObject? = null,
)

// ---------------------------------------------------------------------- scopes

@Serializable
public data class ScopeNodeJson(
    val createdAt: String,
    val path: String,
    val tombstonedAt: String? = null,
)

@Serializable
public data class ForgetScopeResponseJson(
    val forgotten: Int = 0,
)
