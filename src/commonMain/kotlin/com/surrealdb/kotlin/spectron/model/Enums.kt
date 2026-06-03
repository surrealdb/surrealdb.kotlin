package com.surrealdb.kotlin.spectron.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
public enum class QueryMode(public val wire: String) {
    @SerialName("hybrid") HYBRID("hybrid"),
    @SerialName("vector") VECTOR("vector"),
    @SerialName("bm25") BM25("bm25"),
    @SerialName("hybrid_graph") HYBRID_GRAPH("hybrid_graph"),
}

@Serializable
public enum class QueryKind(public val wire: String) {
    @SerialName("direct_lookup") DIRECT_LOOKUP("direct_lookup"),
    @SerialName("hybrid") HYBRID("hybrid"),
    @SerialName("full_context") FULL_CONTEXT("full_context"),
}

@Serializable
public enum class Tier(public val wire: String) {
    @SerialName("direct") DIRECT("direct"),
    @SerialName("cache") CACHE("cache"),
    @SerialName("hybrid") HYBRID("hybrid"),
    @SerialName("full_context") FULL_CONTEXT("full_context"),
}

@Serializable
public enum class ResultKind(public val wire: String) {
    @SerialName("attribute") ATTRIBUTE("attribute"),
    @SerialName("entity") ENTITY("entity"),
    @SerialName("chunk") CHUNK("chunk"),
    @SerialName("memory_chunk") MEMORY_CHUNK("memory_chunk"),
    @SerialName("section") SECTION("section"),
}

@Serializable
public enum class GraphEdgeKind(public val wire: String) {
    @SerialName("knowledge_has_keyword") KNOWLEDGE_HAS_KEYWORD("knowledge_has_keyword"),
    @SerialName("section_match") SECTION_MATCH("section_match"),
    @SerialName("document_link") DOCUMENT_LINK("document_link"),
    @SerialName("document_summary") DOCUMENT_SUMMARY("document_summary"),
    @SerialName("keyword_cooccurrence") KEYWORD_COOCCURRENCE("keyword_cooccurrence"),
    @SerialName("hybrid_graph") HYBRID_GRAPH("hybrid_graph"),
}

@Serializable
public enum class DocumentStatus(public val wire: String) {
    @SerialName("queued") QUEUED("queued"),
    @SerialName("extracting") EXTRACTING("extracting"),
    @SerialName("chunking") CHUNKING("chunking"),
    @SerialName("embedding") EMBEDDING("embedding"),
    @SerialName("keywording") KEYWORDING("keywording"),
    @SerialName("extracting_nodes") EXTRACTING_NODES("extracting_nodes"),
    @SerialName("ready") READY("ready"),
    @SerialName("failed") FAILED("failed"),
}

@Serializable
public enum class TurnRole(public val wire: String) {
    @SerialName("user") USER("user"),
    @SerialName("assistant") ASSISTANT("assistant"),
    @SerialName("system") SYSTEM("system"),
    @SerialName("tool") TOOL("tool"),
}

@Serializable
public enum class MemoryCategory(public val wire: String) {
    @SerialName("identity") IDENTITY("identity"),
    @SerialName("knowledge") KNOWLEDGE("knowledge"),
    @SerialName("context") CONTEXT("context"),
}

@Serializable
public enum class InferMode(public val wire: String) {
    @SerialName("full") FULL("full"),
    @SerialName("triples") TRIPLES("triples"),
    @SerialName("preview") PREVIEW("preview"),
    @SerialName("none") NONE("none"),
}

@Serializable
public enum class BatchExtractionMode(public val wire: String) {
    @SerialName("per_message") PER_MESSAGE("per_message"),
    @SerialName("whole_conversation") WHOLE_CONVERSATION("whole_conversation"),
}

/** The error envelope every Spectron endpoint returns on a 4xx/5xx. */
@Serializable
public data class ApiErrorResponse(
    val message: String,
)
