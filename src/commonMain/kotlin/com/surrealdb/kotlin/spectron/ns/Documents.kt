package com.surrealdb.kotlin.spectron.ns

import com.surrealdb.kotlin.spectron.SpectronTransport
import com.surrealdb.kotlin.spectron.model.ChunkPageJson
import com.surrealdb.kotlin.spectron.model.DocGeoFilterJson
import com.surrealdb.kotlin.spectron.model.DocumentJson
import com.surrealdb.kotlin.spectron.model.DocumentKeywordJson
import com.surrealdb.kotlin.spectron.model.DocumentKeywordsResponse
import com.surrealdb.kotlin.spectron.model.DocumentPageJson
import com.surrealdb.kotlin.spectron.model.DocumentStatus
import com.surrealdb.kotlin.spectron.model.GraphEdgeKind
import com.surrealdb.kotlin.spectron.model.KeywordDetailJson
import com.surrealdb.kotlin.spectron.model.KeywordPageJson
import com.surrealdb.kotlin.spectron.model.KeywordSearchResponseJson
import com.surrealdb.kotlin.spectron.model.QueryFilter
import com.surrealdb.kotlin.spectron.model.QueryMode
import com.surrealdb.kotlin.spectron.model.QueryResponseJson
import com.surrealdb.kotlin.spectron.model.RecomputeLinksResponse
import com.surrealdb.kotlin.spectron.model.UploadResponse
import com.surrealdb.kotlin.spectron.normaliseScopeSets
import com.surrealdb.kotlin.spectron.onBehalfOfHeader
import com.surrealdb.kotlin.spectron.quotePath
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

internal fun buildDocumentQueryPayload(
    query: String,
    mode: QueryMode?,
    k: Int?,
    threshold: Double?,
    vectorWeight: Double?,
    rrfK: Double?,
    graphAlpha: Double?,
    graphEdges: List<GraphEdgeKind>?,
    graphDepth: Int?,
    expandGraph: Boolean?,
    decomposeQuery: Boolean?,
    useHyde: Boolean?,
    useReranker: Boolean?,
    filter: QueryFilter?,
    location: DocGeoFilterJson?,
    transport: SpectronTransport,
): JsonObject = buildJsonObject {
    put("query", query)
    mode?.let { put("mode", it.wire) }
    k?.let { put("k", it) }
    threshold?.let { put("threshold", it) }
    vectorWeight?.let { put("vectorWeight", it) }
    rrfK?.let { put("rrfK", it) }
    graphAlpha?.let { put("graphAlpha", it) }
    graphEdges?.let { edges -> put("graphEdges", buildJsonArray { edges.forEach { add(it.wire) } }) }
    graphDepth?.let { put("graphDepth", it) }
    expandGraph?.let { put("expandGraph", it) }
    decomposeQuery?.let { put("decomposeQuery", it) }
    useHyde?.let { put("useHyde", it) }
    useReranker?.let { put("useReranker", it) }
    filter?.let { put("filter", transport.json.encodeToJsonElement(QueryFilter.serializer(), it)) }
    location?.let { put("location", transport.json.encodeToJsonElement(DocGeoFilterJson.serializer(), it)) }
}

/**
 * Build the `metadata` multipart part the upload handler reads: a JSON object
 * carrying optional `title` / `source` and a DNF scope selector. The file's
 * MIME type rides on the `file` part's Content-Type, so it is not duplicated
 * here.
 */
private fun SpectronTransport.uploadFields(
    title: String?,
    source: String?,
    scopes: List<List<String>>?,
): Map<String, String> {
    val clauses = normaliseScopeSets(scopes)
    val metadata = buildJsonObject {
        title?.let { put("title", it) }
        source?.let { put("source", it) }
        if (clauses.isNotEmpty()) {
            put("scopes", buildJsonArray { clauses.forEach { clause -> add(buildJsonArray { clause.forEach { add(it) } }) } })
        }
    }
    return if (metadata.isEmpty()) emptyMap() else mapOf("metadata" to json.encodeToString(JsonObject.serializer(), metadata))
}

public class SpectronKeywords internal constructor(
    private val transport: SpectronTransport,
    contextId: String,
) {
    private val base = "${enduserBase(contextId)}/documents/keywords"
    private val documentsBase = "${enduserBase(contextId)}/documents"

    public suspend fun list(
        q: String? = null,
        minDocumentCount: Int? = null,
        sort: String? = null,
        page: Int? = null,
        pageSize: Int? = null,
        onBehalfOf: String? = null,
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
            onBehalfOfHeader(onBehalfOf),
        )
        return transport.json.decodeFromJsonElement(KeywordPageJson.serializer(), body!!)
    }

    public suspend fun search(
        query: String,
        k: Int? = null,
        threshold: Double? = null,
        onBehalfOf: String? = null,
    ): KeywordSearchResponseJson {
        val payload = buildJsonObject {
            put("query", query)
            k?.let { put("k", it) }
            threshold?.let { put("threshold", it) }
        }
        val body = transport.post("$base/search", payload, onBehalfOfHeader(onBehalfOf))
        return transport.json.decodeFromJsonElement(KeywordSearchResponseJson.serializer(), body!!)
    }

    public suspend fun get(normalised: String, onBehalfOf: String? = null): KeywordDetailJson {
        val body = transport.get("$base/${quotePath(normalised)}", headers = onBehalfOfHeader(onBehalfOf))
        return transport.json.decodeFromJsonElement(KeywordDetailJson.serializer(), body!!)
    }

    public suspend fun forDocument(documentId: String, onBehalfOf: String? = null): List<DocumentKeywordJson> {
        val body = transport.get(
            "$documentsBase/${quotePath(documentId)}/keywords",
            headers = onBehalfOfHeader(onBehalfOf),
        ) ?: return emptyList()
        return transport.json
            .decodeFromJsonElement(DocumentKeywordsResponse.serializer(), body)
            .keywords
    }
}

public class SpectronDocuments internal constructor(
    private val transport: SpectronTransport,
    private val contextId: String,
) {
    private val base = "${enduserBase(contextId)}/documents"

    public val keywords: SpectronKeywords = SpectronKeywords(transport, contextId)

    public suspend fun upload(
        file: ByteArray,
        filename: String,
        contentType: String? = null,
        title: String? = null,
        source: String? = null,
        scopes: List<List<String>>? = null,
        onBehalfOf: String? = null,
    ): UploadResponse {
        val body = transport.postMultipart(
            base,
            file = file,
            filename = filename,
            mimeType = contentType,
            fields = transport.uploadFields(title, source, scopes),
            headers = onBehalfOfHeader(onBehalfOf),
        )
        return transport.json.decodeFromJsonElement(UploadResponse.serializer(), body!!)
    }

    /** Re-run the ingestion pipeline for an existing document. Maps to `PUT /{ctx}/documents/{id}`. */
    public suspend fun reprocess(documentId: String, onBehalfOf: String? = null): UploadResponse {
        val body = transport.put("$base/${quotePath(documentId)}", headers = onBehalfOfHeader(onBehalfOf))
        return body?.let {
            transport.json.decodeFromJsonElement(UploadResponse.serializer(), it)
        } ?: UploadResponse(
            contentHash = "",
            deduplicated = false,
            id = documentId,
            status = DocumentStatus.QUEUED,
        )
    }

    public suspend fun get(documentId: String, onBehalfOf: String? = null): DocumentJson {
        val body = transport.get("$base/${quotePath(documentId)}", headers = onBehalfOfHeader(onBehalfOf))
        return transport.json.decodeFromJsonElement(DocumentJson.serializer(), body!!)
    }

    public suspend fun fetchRaw(documentId: String, onBehalfOf: String? = null): ByteArray =
        transport.getRawBytes("$base/${quotePath(documentId)}/raw", onBehalfOfHeader(onBehalfOf))

    public suspend fun chunks(
        documentId: String,
        page: Int? = null,
        pageSize: Int? = null,
        onBehalfOf: String? = null,
    ): ChunkPageJson {
        val body = transport.get(
            "$base/${quotePath(documentId)}/chunks",
            mapOf("page" to page, "page_size" to pageSize),
            onBehalfOfHeader(onBehalfOf),
        )
        return transport.json.decodeFromJsonElement(ChunkPageJson.serializer(), body!!)
    }

    public suspend fun list(
        status: String? = null,
        mimeType: String? = null,
        page: Int? = null,
        pageSize: Int? = null,
        onBehalfOf: String? = null,
    ): DocumentPageJson {
        val body = transport.get(
            base,
            mapOf(
                "status" to status,
                "mime_type" to mimeType,
                "page" to page,
                "page_size" to pageSize,
            ),
            onBehalfOfHeader(onBehalfOf),
        )
        return transport.json.decodeFromJsonElement(DocumentPageJson.serializer(), body!!)
    }

    public suspend fun delete(documentId: String, onBehalfOf: String? = null) {
        transport.delete("$base/${quotePath(documentId)}", headers = onBehalfOfHeader(onBehalfOf))
    }

    public suspend fun query(
        query: String,
        mode: QueryMode? = null,
        k: Int? = null,
        threshold: Double? = null,
        vectorWeight: Double? = null,
        rrfK: Double? = null,
        graphAlpha: Double? = null,
        graphEdges: List<GraphEdgeKind>? = null,
        graphDepth: Int? = null,
        expandGraph: Boolean? = null,
        decomposeQuery: Boolean? = null,
        useHyde: Boolean? = null,
        useReranker: Boolean? = null,
        filter: QueryFilter? = null,
        location: DocGeoFilterJson? = null,
        onBehalfOf: String? = null,
    ): QueryResponseJson {
        val payload = buildDocumentQueryPayload(
            query, mode, k, threshold, vectorWeight, rrfK, graphAlpha, graphEdges,
            graphDepth, expandGraph, decomposeQuery, useHyde, useReranker, filter, location, transport,
        )
        val body = transport.post("$base/query", payload, onBehalfOfHeader(onBehalfOf))
        return transport.json.decodeFromJsonElement(QueryResponseJson.serializer(), body!!)
    }

    public suspend fun recomputeLinks(onBehalfOf: String? = null): RecomputeLinksResponse {
        val body = transport.post("$base/recompute-links", buildJsonObject {}, onBehalfOfHeader(onBehalfOf))
        return transport.json.decodeFromJsonElement(RecomputeLinksResponse.serializer(), body!!)
    }
}
