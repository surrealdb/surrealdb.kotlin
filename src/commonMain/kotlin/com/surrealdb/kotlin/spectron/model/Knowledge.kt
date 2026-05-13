package com.surrealdb.kotlin.spectron.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

@Serializable
public data class ChunkJson(
    @SerialName("char_end") val charEnd: Int,
    @SerialName("char_start") val charStart: Int,
    val document: String,
    val id: String,
    val position: Int,
    val text: String,
    val section: String? = null,
    @SerialName("token_count") val tokenCount: Int? = null,
)

@Serializable
public data class ChunkPageJson(
    val chunks: List<ChunkJson>,
    val page: Int,
    @SerialName("page_size") val pageSize: Int,
    val total: Int,
)

@Serializable
public data class DocumentJson(
    @SerialName("content_hash") val contentHash: String,
    @SerialName("created_at") val createdAt: String,
    val id: String,
    @SerialName("mime_type") val mimeType: String,
    @SerialName("size_bytes") val sizeBytes: Long,
    val source: String,
    val status: String,
    val title: String,
    @SerialName("updated_at") val updatedAt: String,
    val version: Int,
    @SerialName("chunk_count") val chunkCount: Int? = null,
    val error: String? = null,
    @SerialName("keyword_count") val keywordCount: Int? = null,
    val language: String? = null,
    @SerialName("processing_completed_at") val processingCompletedAt: String? = null,
    @SerialName("processing_started_at") val processingStartedAt: String? = null,
)

@Serializable
public data class DocumentPageJson(
    val documents: List<DocumentJson>,
    val page: Int,
    @SerialName("page_size") val pageSize: Int,
    val total: Int,
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
    val keywords: List<DocumentKeywordJson>,
)

@Serializable
public data class KeywordJson(
    @SerialName("document_count") val documentCount: Int,
    val id: String,
    val normalised: String,
    val text: String,
)

@Serializable
public data class KeywordPageJson(
    val keywords: List<KeywordJson>,
    val page: Int,
    @SerialName("page_size") val pageSize: Int,
    val total: Int,
)

@Serializable
public data class KeywordDocumentJson(
    val id: String,
    val score: Double,
    val title: String,
)

@Serializable
public data class KeywordDetailJson(
    @SerialName("document_count") val documentCount: Int,
    val documents: List<KeywordDocumentJson>,
    val id: String,
    val normalised: String,
    val text: String,
)

@Serializable
public data class KeywordSearchHitJson(
    @SerialName("document_count") val documentCount: Int,
    val id: String,
    val normalised: String,
    val score: Double,
    val text: String,
)

@Serializable
public data class KeywordSearchResponseJson(
    @SerialName("query_ms") val queryMs: Int,
    val results: List<KeywordSearchHitJson>,
)

@Serializable
public data class KnowledgeLinkTarget(
    val kind: String,
    val slug: String,
)

@Serializable
public data class KnowledgeLinkUpsert(
    val label: String,
    val to: KnowledgeLinkTarget,
)

@Serializable
public data class KnowledgeNodeUpsertRow(
    val kind: String,
    val slug: String,
    val title: String,
    val content: JsonObject? = null,
    val links: List<KnowledgeLinkUpsert>? = null,
    @SerialName("source_document") val sourceDocument: String? = null,
)

@Serializable
public data class KnowledgeSummaryJson(
    val id: String,
    val kind: String,
    val title: String,
)

@Serializable
public data class KnowledgeNodeListedJson(
    val content: JsonObject,
    @SerialName("created_at") val createdAt: String,
    val id: String,
    val kind: String,
    val scope: List<String>,
    val title: String,
    @SerialName("updated_at") val updatedAt: String,
    @SerialName("source_document") val sourceDocument: String? = null,
)

@Serializable
public data class KnowledgeNodeFullJson(
    val content: JsonObject,
    @SerialName("created_at") val createdAt: String,
    val embedding: List<Double>,
    val id: String,
    val kind: String,
    val scope: List<String>,
    val title: String,
    @SerialName("updated_at") val updatedAt: String,
    @SerialName("source_document") val sourceDocument: String? = null,
)

@Serializable
public data class KnowledgeNodePageJson(
    val nodes: List<KnowledgeNodeListedJson>,
    val page: Int,
    @SerialName("page_size") val pageSize: Int,
    val total: Int,
)

@Serializable
public data class KnowledgeNodeSearchHitJson(
    val node: KnowledgeSummaryJson,
    val score: Double,
)

@Serializable
public data class KnowledgeNodeSearchResponseJson(
    @SerialName("query_ms") val queryMs: Int,
    val results: List<KnowledgeNodeSearchHitJson>,
)

@Serializable
public data class QueryFilter(
    @SerialName("document_ids") val documentIds: List<String>? = null,
    @SerialName("mime_type") val mimeType: List<String>? = null,
)

@Serializable
public data class QueryHitChunkJson(
    @SerialName("char_end") val charEnd: Int,
    @SerialName("char_start") val charStart: Int,
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
    @SerialName("edge_kind") val edgeKind: String,
    @SerialName("neighbour_label") val neighbourLabel: String,
    val weight: Double,
)

@Serializable
public data class QueryHitJson(
    val chunk: QueryHitChunkJson,
    val document: QueryHitDocumentJson,
    val score: Double,
    @SerialName("graph_evidence") val graphEvidence: List<GraphEvidenceJson>? = null,
    @SerialName("graph_expansion") val graphExpansion: JsonObject? = null,
)

@Serializable
public data class QueryResponseJson(
    @SerialName("query_ms") val queryMs: Int,
    val results: List<QueryHitJson>,
)

@Serializable
public data class TraverseStartJson(
    val type: String,
    val id: String? = null,
    val normalised: String? = null,
    val kind: String? = null,
    val slug: String? = null,
)

@Serializable
public data class TraverseNodeJson(
    val depth: Int,
    val id: String,
    val type: String,
    val kind: String? = null,
    val normalised: String? = null,
    val title: String? = null,
)

@Serializable
public data class TraverseEdgeJson(
    val kind: String,
    val label: String? = null,
    val score: Double? = null,
    @SerialName("from") val from: String = "",
    val to: String = "",
)

@Serializable
public data class TraverseApiResponse(
    val edges: List<TraverseEdgeJson>,
    val nodes: List<TraverseNodeJson>,
)

@Serializable
public data class UploadResponse(
    @SerialName("content_hash") val contentHash: String,
    val deduplicated: Boolean,
    val id: String,
    val status: String,
)
