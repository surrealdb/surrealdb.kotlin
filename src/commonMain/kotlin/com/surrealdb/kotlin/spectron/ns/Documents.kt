package com.surrealdb.kotlin.spectron.ns

import com.surrealdb.kotlin.spectron.SpectronTransport
import com.surrealdb.kotlin.spectron.model.ChunkPageJson
import com.surrealdb.kotlin.spectron.model.DocGeoFilterJson
import com.surrealdb.kotlin.spectron.model.DocumentJson
import com.surrealdb.kotlin.spectron.model.DocumentKeywordJson
import com.surrealdb.kotlin.spectron.model.DocumentKeywordsResponse
import com.surrealdb.kotlin.spectron.model.DocumentPageJson
import com.surrealdb.kotlin.spectron.model.GraphEdgeKind
import com.surrealdb.kotlin.spectron.model.KeywordDetailJson
import com.surrealdb.kotlin.spectron.model.KeywordPageJson
import com.surrealdb.kotlin.spectron.model.KeywordSearchResponseJson
import com.surrealdb.kotlin.spectron.model.QueryFilter
import com.surrealdb.kotlin.spectron.model.QueryMode
import com.surrealdb.kotlin.spectron.model.QueryResponseJson
import com.surrealdb.kotlin.spectron.model.RecomputeLinksResponse
import com.surrealdb.kotlin.spectron.model.UploadResponse
import com.surrealdb.kotlin.spectron.quotePath
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
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

private fun SpectronTransport.uploadFields(
    title: String?,
    profile: String?,
    scope: List<String>?,
): Map<String, String> = buildMap {
    title?.let { put("title", it) }
    profile?.let { put("profile", it) }
    if (!scope.isNullOrEmpty()) {
        put("scope", json.encodeToString(ListSerializer(String.serializer()), scope))
    }
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

    public suspend fun forDocument(documentId: String): List<DocumentKeywordJson> {
        val body = transport.get("$documentsBase/${quotePath(documentId)}/keywords")
            ?: return emptyList()
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
        title: String? = null,
        profile: String? = null,
        scope: List<String>? = null,
        mimeType: String? = null,
    ): UploadResponse {
        val body = transport.postMultipart(
            base,
            file = file,
            filename = filename,
            mimeType = mimeType,
            fields = transport.uploadFields(title, profile, scope),
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
            fields = transport.uploadFields(title, profile, null),
        )
        return body?.let {
            transport.json.decodeFromJsonElement(UploadResponse.serializer(), it)
        } ?: UploadResponse(
            contentHash = "",
            deduplicated = false,
            id = documentId,
            status = com.surrealdb.kotlin.spectron.model.DocumentStatus.QUEUED,
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
        graphEdges: List<GraphEdgeKind>? = null,
        graphDepth: Int? = null,
        expandGraph: Boolean? = null,
        decomposeQuery: Boolean? = null,
        useHyde: Boolean? = null,
        useReranker: Boolean? = null,
        filter: QueryFilter? = null,
        location: DocGeoFilterJson? = null,
    ): QueryResponseJson {
        val payload = buildDocumentQueryPayload(
            query, mode, k, threshold, vectorWeight, rrfK, graphAlpha, graphEdges,
            graphDepth, expandGraph, decomposeQuery, useHyde, useReranker, filter, location, transport,
        )
        val body = transport.post("$base/query", payload)
        return transport.json.decodeFromJsonElement(QueryResponseJson.serializer(), body!!)
    }

    public suspend fun recomputeLinks(): RecomputeLinksResponse {
        val body = transport.post("$base/recompute-links", buildJsonObject {})
        return transport.json.decodeFromJsonElement(RecomputeLinksResponse.serializer(), body!!)
    }
}
