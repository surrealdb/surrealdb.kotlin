package com.surrealdb.kotlin.spectron.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
public enum class QueryMode(public val wire: String) {
    @SerialName("vector") VECTOR("vector"),
    @SerialName("bm25") BM25("bm25"),
    @SerialName("hybrid") HYBRID("hybrid"),
    @SerialName("hybrid_graph") HYBRID_GRAPH("hybrid_graph"),
}

@Serializable
public enum class DocumentStatus(public val wire: String) {
    @SerialName("queued") QUEUED("queued"),
    @SerialName("extracting") EXTRACTING("extracting"),
    @SerialName("chunking") CHUNKING("chunking"),
    @SerialName("embedding") EMBEDDING("embedding"),
    @SerialName("rendering") RENDERING("rendering"),
    @SerialName("transcribing") TRANSCRIBING("transcribing"),
    @SerialName("captioning") CAPTIONING("captioning"),
    @SerialName("keywording") KEYWORDING("keywording"),
    @SerialName("ready") READY("ready"),
    @SerialName("failed") FAILED("failed"),
}

@Serializable
public enum class IngestProfile(public val wire: String) {
    @SerialName("text_only") TEXT_ONLY("text_only"),
    @SerialName("text_plus_ocr") TEXT_PLUS_OCR("text_plus_ocr"),
    @SerialName("multimodal_balanced") MULTIMODAL_BALANCED("multimodal_balanced"),
    @SerialName("multimodal_full") MULTIMODAL_FULL("multimodal_full"),
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
    @SerialName("instruction") INSTRUCTION("instruction"),
    @SerialName("uncertainty") UNCERTAINTY("uncertainty"),
}
