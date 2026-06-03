package com.surrealdb.kotlin.spectron.model

import kotlinx.serialization.Serializable

@Serializable
public data class TripleEntity(
    val name: String,
    val type: String,
)

/**
 * A structured triple supplied directly by the caller (no LLM).
 *
 * `key` + `value` create an attribute; if `value` is null and `target` is set,
 * the triple is a relation edge from `entity` to `target` labelled `key`.
 */
@Serializable
public data class Triple(
    val entity: TripleEntity,
    val key: String,
    val value: String? = null,
    val target: TripleEntity? = null,
    val memoryCategory: MemoryCategory? = null,
)

@Serializable
public data class FactsResponseJson(
    val mode: InferMode,
    val sessionId: String,
    val chunkId: String? = null,
    val extraction: ExtractionResultJson? = null,
    val preview: Boolean? = null,
    val turnId: String? = null,
)

@Serializable
public data class BatchMessage(
    val content: String,
    val role: TurnRole,
    val ts: String? = null,
)

@Serializable
public data class FactsBatchResponseJson(
    val extractions: List<ExtractionResultJson> = emptyList(),
    val sessionId: String,
    val turnIds: List<String> = emptyList(),
)
