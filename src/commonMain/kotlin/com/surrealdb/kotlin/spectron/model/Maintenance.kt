package com.surrealdb.kotlin.spectron.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ----------------------------------------------------------------- consolidate

@Serializable
public data class ConsolidateOutcomeJson(
    val entityName: String,
    val key: String,
    val kind: String,
    val proofCount: Int,
    val value: String,
    val observationId: String? = null,
    val rationale: String? = null,
)

@Serializable
public data class ConsolidateResponseJson(
    val created: Int = 0,
    val dryRun: Boolean = false,
    val outcomes: List<ConsolidateOutcomeJson> = emptyList(),
    val superseded: Int = 0,
    val traceId: String? = null,
    val updated: Int = 0,
)

// ------------------------------------------------------------------- elaborate

@Serializable
public data class ElaborateProposedRelationJson(
    val label: String,
    @SerialName("object") val obj: String,
    val subject: String,
)

@Serializable
public data class ElaborateOutcomeJson(
    val dryRun: Boolean = false,
    val entityName: String,
    val entityType: String,
    val proposedRelations: List<ElaborateProposedRelationJson> = emptyList(),
    val relationsEmitted: Int = 0,
    val traceId: String? = null,
)

@Serializable
public data class ElaborateResponseJson(
    val outcomes: List<ElaborateOutcomeJson> = emptyList(),
    val relationsEmitted: Int = 0,
)

// ------------------------------------------------------------------------ fsck

@Serializable
public data class ContradictionFindingJson(
    val entity: String,
    val key: String,
    val values: List<String> = emptyList(),
)

@Serializable
public data class DuplicateFindingJson(
    val entityA: String,
    val entityB: String,
    val similarity: Double,
)

@Serializable
public data class InjectionFindingJson(
    val kind: String,
    val rowId: String,
    val snippet: String,
)

@Serializable
public data class FsckReportJson(
    val contradictions: List<ContradictionFindingJson> = emptyList(),
    val duplicates: List<DuplicateFindingJson> = emptyList(),
    val injection: List<InjectionFindingJson> = emptyList(),
    val total: Int = 0,
)

// ---------------------------------------------------------------------- inspect

/**
 * The polymorphic `GET /inspect` response. The populated fields depend on
 * [kind]: `entity` fills [entity]/[attributes]/[relations]; `attribute` fills
 * [current]/[history]; `relation` fills [matches]; `trace` fills [trace].
 */
@Serializable
public data class InspectResponseJson(
    val kind: String,
    val entity: EntityDetailJson? = null,
    val attributes: List<AttributeDetailJson>? = null,
    val relations: List<RelationDetailJson>? = null,
    val current: AttributeDetailJson? = null,
    val history: List<AttributeDetailJson>? = null,
    val matches: List<RelationDetailJson>? = null,
    val trace: TraceRecordJson? = null,
)
