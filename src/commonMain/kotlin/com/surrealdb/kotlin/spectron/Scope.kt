package com.surrealdb.kotlin.spectron

import kotlinx.serialization.Serializable

@Serializable
public data class ScopeEntry(val key: String, val value: String)

public fun serialiseScope(scope: Map<String, String>?): List<ScopeEntry>? {
    if (scope == null) return null
    return scope.map { (k, v) -> ScopeEntry(k, v) }
}

public fun deserialiseScope(wire: List<ScopeEntry>?): Map<String, String> {
    if (wire.isNullOrEmpty()) return emptyMap()
    return wire.associate { it.key to it.value }
}
