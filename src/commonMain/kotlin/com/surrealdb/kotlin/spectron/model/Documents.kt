package com.surrealdb.kotlin.spectron.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
public data class ChunkJson(
    val charEnd: Int,
    val charStart: Int,
    val document: String,
    val id: String,
    val position: Int,
    val text: String,
    val section: String? = null,
    val tokenCount: Int? = null,
)

@Serializable
public data class ChunkPageJson(
    val chunks: List<ChunkJson> = emptyList(),
    val page: Int = 0,
    val pageSize: Int = 0,
    val total: Int = 0,
)

@Serializable
public data class DocumentJson(
    val contentHash: String,
    val createdAt: String,
    val id: String,
    val mimeType: String,
    val sizeBytes: Long,
    val source: String,
    val status: DocumentStatus,
    val title: String,
    val updatedAt: String,
    val version: Int,
    val chunkCount: Int? = null,
    val error: String? = null,
    val keywordCount: Int? = null,
    val language: String? = null,
    val processingCompletedAt: String? = null,
    val processingStartedAt: String? = null,
)

@Serializable
public data class DocumentPageJson(
    val documents: List<DocumentJson> = emptyList(),
    val page: Int = 0,
    val pageSize: Int = 0,
    val total: Int = 0,
)

@Serializable
public data class DocumentKeywordJson(
    val id: String,
    val normalised: String,
    val score: Double,
    val text: String,
)

@Serializable
public data class DocumentKeywordsResponse(
    val keywords: List<DocumentKeywordJson> = emptyList(),
)

@Serializable
public data class KeywordJson(
    val documentCount: Int,
    val id: String,
    val normalised: String,
    val text: String,
)

@Serializable
public data class KeywordPageJson(
    val keywords: List<KeywordJson> = emptyList(),
    val page: Int = 0,
    val pageSize: Int = 0,
    val total: Int = 0,
)

@Serializable
public data class KeywordDocumentJson(
    val id: String,
    val score: Double,
    val title: String,
)

@Serializable
public data class KeywordDetailJson(
    val documentCount: Int,
    val documents: List<KeywordDocumentJson> = emptyList(),
    val id: String,
    val normalised: String,
    val text: String,
)

@Serializable
public data class KeywordSearchHitJson(
    val documentCount: Int,
    val id: String,
    val normalised: String,
    val score: Double,
    val text: String,
)

@Serializable
public data class KeywordSearchResponseJson(
    val queryMs: Int = 0,
    val results: List<KeywordSearchHitJson> = emptyList(),
)

@Serializable
public data class QueryFilter(
    val documentIds: List<String>? = null,
    val mimeType: List<String>? = null,
)

@Serializable
public data class DocGeoNearJson(
    val lat: Double,
    val lng: Double,
    val radiusKm: Double,
)

@Serializable
public data class DocGeoFilterJson(
    val near: DocGeoNearJson? = null,
    val within: String? = null,
)

@Serializable
public data class QueryHitChunkJson(
    val charEnd: Int,
    val charStart: Int,
    val document: String,
    val id: String,
    val position: Int,
    val text: String,
    val section: String? = null,
)

@Serializable
public data class QueryHitDocumentJson(
    val id: String,
    val source: String,
    val title: String,
)

@Serializable
public data class GraphEvidenceJson(
    val edgeKind: GraphEdgeKind,
    val neighbourLabel: String,
    val weight: Double,
)

@Serializable
public data class QueryHitJson(
    val chunk: QueryHitChunkJson,
    val document: QueryHitDocumentJson,
    val score: Double,
    val graphEvidence: List<GraphEvidenceJson>? = null,
    val graphExpansion: JsonElement? = null,
)

@Serializable
public data class QueryResponseJson(
    val queryMs: Int = 0,
    val results: List<QueryHitJson> = emptyList(),
)

@Serializable
public data class RecomputeLinksResponse(
    val linksEmitted: Int = 0,
)

@Serializable
public data class UploadResponse(
    val contentHash: String,
    val deduplicated: Boolean,
    val id: String,
    val status: DocumentStatus,
)
