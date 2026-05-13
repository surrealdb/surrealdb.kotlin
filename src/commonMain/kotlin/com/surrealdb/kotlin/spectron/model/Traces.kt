package com.surrealdb.kotlin.spectron.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
public data class TraceRecord(
    val id: String,
    @SerialName("resolution_tier") val resolutionTier: String? = null,
    @SerialName("latency_ms") val latencyMs: Int? = null,
    val cached: Boolean? = null,
    @SerialName("retrieved_count") val retrievedCount: Int? = null,
    @SerialName("top_scores") val topScores: List<Double>? = null,
    val payload: JsonObject? = null,
)

@Serializable
public data class TraceListResponse(
    val traces: List<TraceRecord>,
)

@Serializable
public data class TraceStats(
    @SerialName("total_queries") val totalQueries: Int? = null,
    @SerialName("cache_hits") val cacheHits: Int? = null,
    @SerialName("avg_latency_ms") val avgLatencyMs: Double? = null,
    @SerialName("tier_counts") val tierCounts: Map<String, Int>? = null,
)
