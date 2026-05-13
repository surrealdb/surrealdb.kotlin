package com.surrealdb.kotlin.spectron.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

@Serializable
public data class SessionInfo(
    val id: String,
    val scope: JsonElement? = null,
    val metadata: JsonObject? = null,
    @SerialName("created_at") val createdAt: String? = null,
)

@Serializable
public data class Turn(
    val role: TurnRole,
    val content: String,
    val id: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    val metadata: JsonObject? = null,
)

@Serializable
public data class EntityRef(
    val type: String,
    val name: String,
)

@Serializable
public data class AttributeUpdate(
    val entity: EntityRef,
    val key: String,
    val value: JsonElement,
    val category: MemoryCategory? = null,
    val confidence: Double? = null,
)

@Serializable
public data class RelationUpdate(
    val label: String,
    val source: EntityRef,
    val target: EntityRef,
    val confidence: Double? = null,
)

@Serializable
public data class ExtractionResult(
    val entities: List<EntityRef>? = null,
    val attributes: List<AttributeUpdate>? = null,
    val relations: List<RelationUpdate>? = null,
    val instructions: List<String>? = null,
    val uncertainties: List<String>? = null,
    val corrections: List<JsonObject>? = null,
    @SerialName("turn_id") val turnId: String? = null,
)

@Serializable
public data class ChatReply(
    val reply: String,
    @SerialName("memory_updates") val memoryUpdates: ExtractionResult? = null,
    @SerialName("turn_id") val turnId: String? = null,
)

@Serializable
public data class ContextResult(
    val context: String,
    val tier: String? = null,
    @SerialName("query_ms") val queryMs: Int? = null,
)

@Serializable
public data class MemoryHit(
    val source: String,
    val score: Double,
    val text: String? = null,
    val id: String? = null,
    val metadata: JsonObject? = null,
)

@Serializable
public data class MemoryQueryResponse(
    val hits: List<MemoryHit>,
    val tier: String? = null,
    @SerialName("query_ms") val queryMs: Int? = null,
    val trace: JsonObject? = null,
)

@Serializable
public data class StructuredState(
    val identity: JsonObject? = null,
    val knowledge: JsonObject? = null,
    val context: JsonObject? = null,
    val instructions: List<String>? = null,
    val unknowns: List<String>? = null,
)

@Serializable
public data class ProfileResponse(
    val static: JsonObject? = null,
    val dynamic: JsonObject? = null,
    val preferences: JsonObject? = null,
    val instructions: List<String>? = null,
)

@Serializable
public data class Entity(
    val type: String,
    val name: String,
    val attributes: JsonObject? = null,
    val scope: JsonElement? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
)

@Serializable
public data class EntityHistoryEntry(
    val value: JsonElement,
    @SerialName("valid_from") val validFrom: String? = null,
    @SerialName("valid_until") val validUntil: String? = null,
    @SerialName("source_turn") val sourceTurn: String? = null,
)

@Serializable
public data class ReflectionResult(
    val reflection: String,
    val evidence: List<JsonObject>? = null,
    @SerialName("persisted_attributes") val persistedAttributes: List<AttributeUpdate>? = null,
)

@Serializable
public data class ForgetResult(
    val deleted: Int,
)
