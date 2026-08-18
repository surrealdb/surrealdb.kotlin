package com.surrealdb.kotlin.spectron.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * A freshly minted (or rotated) self-service key. [key] is the full bearer
 * secret (`sp-{id}-{secret}`) and is only ever returned once, at creation.
 */
@Serializable
public data class MintedKey(
    val id: String,
    val key: String,
    val validUntil: String? = null,
)

@Serializable
public data class KeyDetail(
    val id: String,
    val name: String = "",
    val createdAt: String = "",
    val grants: JsonObject? = null,
    val lastUsedAt: String? = null,
    val validUntil: String? = null,
)
