package com.surrealdb.kotlin.spectron.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ---------------------------------------------------------------- geo filters

@Serializable
public data class GeoNearJson(
    val lat: Double,
    val lng: Double,
    val radiusKm: Double,
)

@Serializable
public data class GeoFilterJson(
    val near: GeoNearJson? = null,
    val within: String? = null,
)

// ----------------------------------------------------------- extraction shapes

@Serializable
public data class EntitySummaryJson(
    val entityType: String,
    val id: String,
    val isNew: Boolean,
    val memoryCategory: MemoryCategory,
    val name: String,
)

@Serializable
public data class AttributeSummaryJson(
    val entityId: String,
    val id: String,
    val key: String,
    val memoryCategory: MemoryCategory,
    val value: String,
)

@Serializable
public data class RelationSummaryJson(
    val label: String,
    val memoryCategory: MemoryCategory,
    @SerialName("object") val obj: String,
    val subject: String,
)

@Serializable
public data class InstructionSummaryJson(
    val description: String,
    val id: String,
    val label: String,
)

@Serializable
public data class UncertaintySummaryJson(
    val about: String,
    val reason: String,
)

@Serializable
public data class CorrectionSummaryJson(
    val entityId: String,
    val key: String,
    val newValue: String,
    val oldValue: String,
)

@Serializable
public data class ExtractionResultJson(
    val attributes: List<AttributeSummaryJson> = emptyList(),
    val corrections: List<CorrectionSummaryJson> = emptyList(),
    val entities: List<EntitySummaryJson> = emptyList(),
    val instructions: List<InstructionSummaryJson> = emptyList(),
    val relations: List<RelationSummaryJson> = emptyList(),
    val turnId: String? = null,
    val uncertainties: List<UncertaintySummaryJson> = emptyList(),
)

// --------------------------------------------------------------- detail shapes

@Serializable
public data class AttributeDetailJson(
    val createdAt: String,
    val entity: String,
    val id: String,
    val importance: Double,
    val key: String,
    val memoryCategory: MemoryCategory,
    val value: String,
    val supersededBy: String? = null,
    val supersedes: String? = null,
    val validFrom: String? = null,
    val validUntil: String? = null,
)

@Serializable
public data class EntityDetailJson(
    val createdAt: String,
    val entityType: String,
    val id: String,
    val importance: Double,
    val memoryCategory: MemoryCategory,
    val name: String,
    val updatedAt: String,
)

@Serializable
public data class RelationDetailJson(
    val createdAt: String,
    val id: String,
    val label: String,
    val memoryCategory: MemoryCategory,
    @SerialName("object") val obj: String,
    val subject: String,
    val validFrom: String? = null,
    val validUntil: String? = null,
)

// ------------------------------------------------------------------- responses

@Serializable
public data class ChatResponseJson(
    val reply: String,
    val sessionId: String,
    val traceId: String,
    val memoryUpdates: ExtractionResultJson? = null,
)

@Serializable
public data class ContextQueryResponseJson(
    val context: String,
    val queryMs: Int = 0,
    val tier: String? = null,
)

@Serializable
public data class MemoryHitJson(
    val id: String,
    val score: Double,
    val source: ResultKind,
    val text: String,
)

@Serializable
public data class QueryTraceJson(
    val latencyMs: Int = 0,
    val resolutionTier: String? = null,
    val retrievedCount: Int = 0,
    val tierReason: String? = null,
    val topScores: List<Double> = emptyList(),
    val traceId: String? = null,
)

@Serializable
public data class QueryMemoryResponseJson(
    val hits: List<MemoryHitJson> = emptyList(),
    val classificationKind: QueryKind? = null,
    val queryMs: Int = 0,
    val seedEntities: List<String> = emptyList(),
    val tier: Tier? = null,
    val trace: QueryTraceJson? = null,
)

@Serializable
public data class ReflectResponseJson(
    val reflection: String,
    val evidence: List<String> = emptyList(),
    val persistedAttributes: List<AttributeSummaryJson> = emptyList(),
    val traceId: String? = null,
)

@Serializable
public data class ForgetResponseJson(
    val deleted: Int = 0,
)

@Serializable
public data class CategoryStateJson(
    val attributes: List<AttributeDetailJson> = emptyList(),
    val entities: List<EntityDetailJson> = emptyList(),
    val relations: List<RelationDetailJson> = emptyList(),
)

@Serializable
public data class StateResponseJson(
    val context: CategoryStateJson? = null,
    val identity: CategoryStateJson? = null,
    val knowledge: CategoryStateJson? = null,
    val instructions: List<InstructionSummaryJson> = emptyList(),
    val unknowns: List<UncertaintySummaryJson> = emptyList(),
)

@Serializable
public data class ProfileEntryJson(
    val key: String,
    val value: String,
)

@Serializable
public data class ProfileResponseJson(
    val dynamic: List<ProfileEntryJson> = emptyList(),
    val instructions: List<InstructionSummaryJson> = emptyList(),
    val preferences: List<ProfileEntryJson> = emptyList(),
    val static: List<ProfileEntryJson> = emptyList(),
)

// -------------------------------------------------------------------- entities

@Serializable
public data class EntityListResponseJson(
    val entities: List<EntityDetailJson> = emptyList(),
)

@Serializable
public data class EntityResponseJson(
    val entity: EntityDetailJson,
    val attributes: List<AttributeDetailJson> = emptyList(),
    val relations: List<RelationDetailJson> = emptyList(),
)

@Serializable
public data class EntityHistoryResponseJson(
    val history: List<AttributeDetailJson> = emptyList(),
)

// -------------------------------------------------------------------- sessions

@Serializable
public data class SessionResponseJson(
    val createdAt: String,
    val id: String,
    val scope: List<String> = emptyList(),
)

@Serializable
public data class TurnResponseJson(
    val content: String,
    val createdAt: String,
    val id: String,
    val role: TurnRole,
    val seq: Int,
    val session: String,
)

@Serializable
public data class TurnListResponseJson(
    val turns: List<TurnResponseJson> = emptyList(),
)

@Serializable
public data class SessionContextResponseJson(
    val context: String,
)

// ------------------------------------------------------------------- lifecycle

@Serializable
public data class LifecycleResponseJson(
    val affected: Int = 0,
)
