package com.surrealdb.kotlin.spectron.model

import kotlinx.serialization.Serializable

@Serializable
public data class TraceRecordJson(
    val cached: Boolean = false,
    val createdAt: String,
    val id: String,
    val latencyMs: Int = 0,
    val queryText: String? = null,
    val resolutionTier: String? = null,
    val tierReason: String? = null,
)

@Serializable
public data class TraceListResponseJson(
    val traces: List<TraceRecordJson> = emptyList(),
)

@Serializable
public data class ContradictionStatsJson(
    val contradictionRate: Double = 0.0,
    val contradictions: Int = 0,
    val reconciliations: Int = 0,
)

@Serializable
public data class RetrievalStatsJson(
    val avgCandidateSet: Double = 0.0,
    val maxCandidateSet: Int = 0,
    val traces: Int = 0,
)

@Serializable
public data class SupersessionStatsJson(
    val churnPerEntity: Double = 0.0,
    val entitiesChurned: Int = 0,
    val supersessionEvents: Int = 0,
)

@Serializable
public data class TierCountsJson(
    val direct: Int = 0,
    val fullContext: Int = 0,
    val hybrid: Int = 0,
)

@Serializable
public data class SourceKindCountJson(
    val count: Int = 0,
    val kind: String,
)

@Serializable
public data class TraceStatsResponseJson(
    val avgLatencyMs: Double = 0.0,
    val cacheHitRate: Double = 0.0,
    val cacheHits: Int = 0,
    val contradiction: ContradictionStatsJson? = null,
    val responseTracesCached: Int = 0,
    val responseTracesTotal: Int = 0,
    val retrieval: RetrievalStatsJson? = null,
    val sourceKindDistribution: List<SourceKindCountJson> = emptyList(),
    val supersession: SupersessionStatsJson? = null,
    val tierCounts: TierCountsJson? = null,
    val totalQueries: Int = 0,
    val windowHours: Int = 0,
)
