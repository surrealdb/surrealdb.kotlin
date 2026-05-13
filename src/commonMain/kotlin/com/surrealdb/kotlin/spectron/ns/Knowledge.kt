package com.surrealdb.kotlin.spectron.ns

import com.surrealdb.kotlin.spectron.ScopeEntry
import com.surrealdb.kotlin.spectron.SpectronTransport
import com.surrealdb.kotlin.spectron.model.ChunkPageJson
import com.surrealdb.kotlin.spectron.model.DocumentJson
import com.surrealdb.kotlin.spectron.model.DocumentKeywordsResponse
import com.surrealdb.kotlin.spectron.model.DocumentPageJson
import com.surrealdb.kotlin.spectron.model.KeywordDetailJson
import com.surrealdb.kotlin.spectron.model.KeywordJson
import com.surrealdb.kotlin.spectron.model.KeywordPageJson
import com.surrealdb.kotlin.spectron.model.KeywordSearchResponseJson
import com.surrealdb.kotlin.spectron.model.KnowledgeLinkUpsert
import com.surrealdb.kotlin.spectron.model.KnowledgeNodeFullJson
import com.surrealdb.kotlin.spectron.model.KnowledgeNodePageJson
import com.surrealdb.kotlin.spectron.model.KnowledgeNodeSearchResponseJson
import com.surrealdb.kotlin.spectron.model.KnowledgeNodeUpsertRow
import com.surrealdb.kotlin.spectron.model.QueryFilter
import com.surrealdb.kotlin.spectron.model.QueryMode
import com.surrealdb.kotlin.spectron.model.QueryResponseJson
import com.surrealdb.kotlin.spectron.model.TraverseApiResponse
import com.surrealdb.kotlin.spectron.model.TraverseStartJson
import com.surrealdb.kotlin.spectron.model.UploadResponse
import com.surrealdb.kotlin.spectron.quotePath
import com.surrealdb.kotlin.spectron.serialiseScope
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

internal fun buildQueryRequestPayload(
    query: String,
    mode: QueryMode?,
    k: Int?,
    threshold: Double?,
    vectorWeight: Double?,
    rrfK: Double?,
    graphAlpha: Double?,
    graphEdges: List<String>?,
    graphDepth: Int?,
    expandGraph: Boolean?,
    filter: QueryFilter?,
    transport: SpectronTransport,
): JsonObject = buildJsonObject {
    put("query", query)
    mode?.let { put("mode", it.wire) }
    k?.let { put("k", it) }
    threshold?.let { put("threshold", it) }
    vectorWeight?.let { put("vectorWeight", it) }
    rrfK?.let { put("rrfK", it) }
    graphAlpha?.let { put("graphAlpha", it) }
    graphEdges?.let { put("graphEdges", buildJsonArray { it.forEach { e -> add(e) } }) }
    graphDepth?.let { put("graphDepth", it) }
    expandGraph?.let { put("expandGraph", it) }
    filter?.let { put("filter", transport.json.encodeToJsonElement(QueryFilter.serializer(), it)) }
}

internal fun buildTraverseRequestPayload(
    start: List<TraverseStartJson>,
    edges: List<String>,
    direction: String?,
    labels: List<String>?,
    maxDepth: Int?,
    limitPerHop: Int?,
    minScore: Double?,
    transport: SpectronTransport,
): JsonObject = buildJsonObject {
    put("start", buildJsonArray {
        start.forEach { add(transport.json.encodeToJsonElement(TraverseStartJson.serializer(), it)) }
    })
    put("edges", buildJsonArray { edges.forEach { add(it) } })
    direction?.let { put("direction", it) }
    labels?.let { put("labels", buildJsonArray { it.forEach { l -> add(l) } }) }
    maxDepth?.let { put("maxDepth", it) }
    limitPerHop?.let { put("limitPerHop", it) }
    minScore?.let { put("minScore", it) }
}

private fun uploadFields(
    title: String?,
    profile: String?,
    scope: Map<String, String>?,
    transport: SpectronTransport,
): Map<String, String> = buildMap {
    title?.let { put("title", it) }
    profile?.let { put("profile", it) }
    serialiseScope(scope)?.let {
        put(
            "scope",
            transport.json.encodeToString(
                ListSerializer(ScopeEntry.serializer()),
                it,
            ),
        )
    }
}

public class SpectronKeywords internal constructor(
    private val transport: SpectronTransport,
    contextId: String,
) {
    private val base = "${enduserBase(contextId)}/knowledge/keywords"
    private val knowledgeBase = "${enduserBase(contextId)}/knowledge"

    public suspend fun list(
        q: String? = null,
        minDocumentCount: Int? = null,
        sort: String? = null,
        page: Int? = null,
        pageSize: Int? = null,
    ): KeywordPageJson {
        val body = transport.get(
            base,
            mapOf(
                "q" to q,
                "minDocumentCount" to minDocumentCount,
                "sort" to sort,
                "page" to page,
                "pageSize" to pageSize,
            ),
        )
        return transport.json.decodeFromJsonElement(KeywordPageJson.serializer(), body!!)
    }

    public suspend fun search(
        query: String,
        k: Int? = null,
        threshold: Double? = null,
    ): KeywordSearchResponseJson {
        val payload = buildJsonObject {
            put("query", query)
            k?.let { put("k", it) }
            threshold?.let { put("threshold", it) }
        }
        val body = transport.post("$base/search", payload)
        return transport.json.decodeFromJsonElement(KeywordSearchResponseJson.serializer(), body!!)
    }

    public suspend fun get(normalised: String): KeywordDetailJson {
        val body = transport.get("$base/${quotePath(normalised)}")
        return transport.json.decodeFromJsonElement(KeywordDetailJson.serializer(), body!!)
    }

    public suspend fun related(normalised: String): TraverseApiResponse {
        val body = transport.get("$base/${quotePath(normalised)}/related")
        return transport.json.decodeFromJsonElement(TraverseApiResponse.serializer(), body!!)
    }

    public suspend fun forDocument(documentId: String): List<KeywordJson> {
        val body = transport.get("$knowledgeBase/${quotePath(documentId)}/keywords")
            ?: return emptyList()
        return transport.json
            .decodeFromJsonElement(DocumentKeywordsResponse.serializer(), body)
            .keywords
            .map { dk ->
                KeywordJson(
                    documentCount = 0,
                    id = dk.id,
                    normalised = dk.normalised,
                    text = dk.text,
                )
            }
    }
}

public class SpectronNodes internal constructor(
    private val transport: SpectronTransport,
    contextId: String,
) {
    private val base = "${enduserBase(contextId)}/knowledge/nodes"

    public suspend fun list(
        kind: String? = null,
        q: String? = null,
        page: Int? = null,
        pageSize: Int? = null,
    ): KnowledgeNodePageJson {
        val body = transport.get(
            base,
            mapOf("kind" to kind, "q" to q, "page" to page, "pageSize" to pageSize),
        )
        return transport.json.decodeFromJsonElement(KnowledgeNodePageJson.serializer(), body!!)
    }

    public suspend fun upsert(
        nodes: List<KnowledgeNodeUpsertRow>,
        relations: List<KnowledgeLinkUpsert>? = null,
        scope: Map<String, String>? = null,
    ) {
        val payload = buildJsonObject {
            put("nodes", buildJsonArray {
                nodes.forEach {
                    add(transport.json.encodeToJsonElement(KnowledgeNodeUpsertRow.serializer(), it))
                }
            })
            relations?.let {
                put("relations", buildJsonArray {
                    it.forEach { r ->
                        add(transport.json.encodeToJsonElement(KnowledgeLinkUpsert.serializer(), r))
                    }
                })
            }
            serialiseScope(scope)?.let {
                put(
                    "scope",
                    transport.json.encodeToJsonElement(
                        ListSerializer(ScopeEntry.serializer()),
                        it,
                    ),
                )
            }
        }
        transport.post("$base/batch", payload)
    }

    public suspend fun search(
        query: String,
        k: Int = 10,
        threshold: Double = 0.0,
        rrfK: Int? = null,
        vectorWeight: Double? = null,
        kindFilter: String? = null,
    ): KnowledgeNodeSearchResponseJson {
        val payload = buildJsonObject {
            put("query", query)
            put("k", k)
            put("threshold", threshold)
            rrfK?.let { put("rrfK", it) }
            vectorWeight?.let { put("vectorWeight", it) }
            kindFilter?.let { put("kindFilter", it) }
        }
        val body = transport.post("$base/search", payload)
        return transport.json.decodeFromJsonElement(KnowledgeNodeSearchResponseJson.serializer(), body!!)
    }

    public suspend fun get(kind: String, slug: String): KnowledgeNodeFullJson {
        val body = transport.get("$base/${quotePath(kind)}/${quotePath(slug)}")
        return transport.json.decodeFromJsonElement(KnowledgeNodeFullJson.serializer(), body!!)
    }

    public suspend fun related(
        kind: String,
        slug: String,
        label: String? = null,
        depth: Int? = null,
    ): TraverseApiResponse {
        val body = transport.get(
            "$base/${quotePath(kind)}/${quotePath(slug)}/related",
            mapOf("label" to label, "depth" to depth),
        )
        return transport.json.decodeFromJsonElement(TraverseApiResponse.serializer(), body!!)
    }

    public suspend fun delete(kind: String, slug: String) {
        transport.delete("$base/${quotePath(kind)}/${quotePath(slug)}")
    }
}

public class SpectronKnowledge internal constructor(
    private val transport: SpectronTransport,
    private val contextId: String,
) {
    private val base = "${enduserBase(contextId)}/knowledge"
    private val traverseBase = "$base/traverse"

    public val keywords: SpectronKeywords = SpectronKeywords(transport, contextId)
    public val nodes: SpectronNodes = SpectronNodes(transport, contextId)

    public suspend fun upload(
        file: ByteArray,
        filename: String,
        title: String? = null,
        profile: String? = null,
        scope: Map<String, String>? = null,
        mimeType: String? = null,
    ): UploadResponse {
        val body = transport.postMultipart(
            base,
            file = file,
            filename = filename,
            mimeType = mimeType,
            fields = uploadFields(title, profile, scope, transport),
        )
        return transport.json.decodeFromJsonElement(UploadResponse.serializer(), body!!)
    }

    public suspend fun replace(
        documentId: String,
        file: ByteArray,
        filename: String,
        title: String? = null,
        profile: String? = null,
        mimeType: String? = null,
    ): UploadResponse {
        val body = transport.putMultipart(
            "$base/${quotePath(documentId)}",
            file = file,
            filename = filename,
            mimeType = mimeType,
            fields = uploadFields(title, profile, null, transport),
        )
        return body?.let {
            transport.json.decodeFromJsonElement(UploadResponse.serializer(), it)
        } ?: UploadResponse(
            contentHash = "",
            deduplicated = false,
            id = documentId,
            status = "queued",
        )
    }

    public suspend fun get(documentId: String): DocumentJson {
        val body = transport.get("$base/${quotePath(documentId)}")
        return transport.json.decodeFromJsonElement(DocumentJson.serializer(), body!!)
    }

    public suspend fun raw(documentId: String): ByteArray =
        transport.getRawBytes("$base/${quotePath(documentId)}/raw")

    public suspend fun chunks(
        documentId: String,
        page: Int? = null,
        pageSize: Int? = null,
    ): ChunkPageJson {
        val body = transport.get(
            "$base/${quotePath(documentId)}/chunks",
            mapOf("page" to page, "page_size" to pageSize),
        )
        return transport.json.decodeFromJsonElement(ChunkPageJson.serializer(), body!!)
    }

    public suspend fun list(
        status: String? = null,
        mimeType: String? = null,
        page: Int? = null,
        pageSize: Int? = null,
    ): DocumentPageJson {
        val body = transport.get(
            base,
            mapOf(
                "status" to status,
                "mime_type" to mimeType,
                "page" to page,
                "page_size" to pageSize,
            ),
        )
        return transport.json.decodeFromJsonElement(DocumentPageJson.serializer(), body!!)
    }

    public suspend fun related(documentId: String): TraverseApiResponse {
        val body = transport.get("$base/${quotePath(documentId)}/related")
        return transport.json.decodeFromJsonElement(TraverseApiResponse.serializer(), body!!)
    }

    public suspend fun delete(documentId: String) {
        transport.delete("$base/${quotePath(documentId)}")
    }

    public suspend fun query(
        query: String,
        mode: QueryMode? = null,
        k: Int? = null,
        threshold: Double? = null,
        vectorWeight: Double? = null,
        rrfK: Double? = null,
        graphAlpha: Double? = null,
        graphEdges: List<String>? = null,
        graphDepth: Int? = null,
        expandGraph: Boolean? = null,
        filter: QueryFilter? = null,
    ): QueryResponseJson {
        val payload = buildQueryRequestPayload(
            query, mode, k, threshold, vectorWeight, rrfK,
            graphAlpha, graphEdges, graphDepth, expandGraph, filter, transport,
        )
        val body = transport.post("$base/query", payload)
        return transport.json.decodeFromJsonElement(QueryResponseJson.serializer(), body!!)
    }

    public suspend fun traverse(
        start: List<TraverseStartJson>,
        edges: List<String>,
        direction: String? = null,
        labels: List<String>? = null,
        maxDepth: Int? = null,
        limitPerHop: Int? = null,
        minScore: Double? = null,
    ): TraverseApiResponse {
        val payload = buildTraverseRequestPayload(
            start, edges, direction, labels, maxDepth, limitPerHop, minScore, transport,
        )
        val body = transport.post(traverseBase, payload)
        return transport.json.decodeFromJsonElement(TraverseApiResponse.serializer(), body!!)
    }

    public suspend fun traverseRecursive(
        start: TraverseStartJson,
        edge: String,
        maxDepth: Int = 3,
        direction: String? = null,
    ): TraverseApiResponse {
        val payload = buildJsonObject {
            put("start", transport.json.encodeToJsonElement(TraverseStartJson.serializer(), start))
            put("edge", edge)
            put("maxDepth", maxDepth)
            direction?.let { put("direction", it) }
        }
        val body = transport.post("$traverseBase/recursive", payload)
        return transport.json.decodeFromJsonElement(TraverseApiResponse.serializer(), body!!)
    }

    public suspend fun traverseSiblings(
        start: TraverseStartJson,
        edge: String,
    ): TraverseApiResponse {
        val payload = buildJsonObject {
            put("start", transport.json.encodeToJsonElement(TraverseStartJson.serializer(), start))
            put("edge", edge)
        }
        val body = transport.post("$traverseBase/siblings", payload)
        return transport.json.decodeFromJsonElement(TraverseApiResponse.serializer(), body!!)
    }
}
