package com.surrealdb.kotlin.spectron.ns

import com.surrealdb.kotlin.spectron.SpectronTransport
import com.surrealdb.kotlin.spectron.model.BatchExtractionMode
import com.surrealdb.kotlin.spectron.model.BatchMessage
import com.surrealdb.kotlin.spectron.model.ChatResponseJson
import com.surrealdb.kotlin.spectron.model.ConsolidateResponseJson
import com.surrealdb.kotlin.spectron.model.ContextQueryResponseJson
import com.surrealdb.kotlin.spectron.model.ElaborateResponseJson
import com.surrealdb.kotlin.spectron.model.FactsBatchResponseJson
import com.surrealdb.kotlin.spectron.model.FactsResponseJson
import com.surrealdb.kotlin.spectron.model.ForgetResponseJson
import com.surrealdb.kotlin.spectron.model.GeoFilterJson
import com.surrealdb.kotlin.spectron.model.InferMode
import com.surrealdb.kotlin.spectron.model.InspectResponseJson
import com.surrealdb.kotlin.spectron.model.MemoryCategory
import com.surrealdb.kotlin.spectron.model.ProfileResponseJson
import com.surrealdb.kotlin.spectron.model.QueryMemoryResponseJson
import com.surrealdb.kotlin.spectron.model.ReflectResponseJson
import com.surrealdb.kotlin.spectron.model.StateResponseJson
import com.surrealdb.kotlin.spectron.model.Triple
import com.surrealdb.kotlin.spectron.model.TurnRole
import com.surrealdb.kotlin.spectron.normaliseScopePaths
import com.surrealdb.kotlin.spectron.onBehalfOfHeader
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

internal fun JsonObjectBuilder.putStringList(key: String, values: List<String>?) {
    values?.let { list -> put(key, buildJsonArray { list.forEach { add(it) } }) }
}

/** Serialise the `scope` field as a normalised, de-duplicated slash-path list. */
internal fun JsonObjectBuilder.putScope(scope: List<String>?) {
    val paths = normaliseScopePaths(scope)
    if (paths.isNotEmpty()) put("scope", buildJsonArray { paths.forEach { add(it) } })
}

public class SpectronMemory internal constructor(
    private val transport: SpectronTransport,
    private val contextId: String,
) {
    private val base = enduserBase(contextId)

    public suspend fun query(
        query: String,
        k: Int? = null,
        mode: String? = null,
        sessionId: String? = null,
        include: List<String>? = null,
        labels: List<String>? = null,
        lens: List<String>? = null,
        scopeView: String? = null,
        source: String? = null,
        asOf: String? = null,
        atInstant: String? = null,
        validFrom: String? = null,
        validUntil: String? = null,
        location: GeoFilterJson? = null,
        onBehalfOf: String? = null,
    ): QueryMemoryResponseJson {
        val payload = buildJsonObject {
            put("query", query)
            k?.let { put("k", it) }
            mode?.let { put("mode", it) }
            sessionId?.let { put("sessionId", it) }
            putStringList("include", include)
            putStringList("labels", labels)
            putStringList("lens", lens)
            scopeView?.let { put("scopeView", it) }
            source?.let { put("source", it) }
            asOf?.let { put("asOf", it) }
            atInstant?.let { put("atInstant", it) }
            validFrom?.let { put("validFrom", it) }
            validUntil?.let { put("validUntil", it) }
            location?.let { put("location", transport.json.encodeToJsonElement(GeoFilterJson.serializer(), it)) }
        }
        val body = transport.post("$base/query", payload, onBehalfOfHeader(onBehalfOf))
        return transport.json.decodeFromJsonElement(QueryMemoryResponseJson.serializer(), body!!)
    }

    public suspend fun context(
        query: String,
        k: Int? = null,
        labels: List<String>? = null,
        lens: List<String>? = null,
        scopeView: String? = null,
        onBehalfOf: String? = null,
    ): ContextQueryResponseJson {
        val payload = buildJsonObject {
            put("query", query)
            k?.let { put("k", it) }
            putStringList("labels", labels)
            putStringList("lens", lens)
            scopeView?.let { put("scopeView", it) }
        }
        val body = transport.post("$base/context", payload, onBehalfOfHeader(onBehalfOf))
        return transport.json.decodeFromJsonElement(ContextQueryResponseJson.serializer(), body!!)
    }

    public suspend fun state(onBehalfOf: String? = null): StateResponseJson {
        val body = transport.get("$base/state", headers = onBehalfOfHeader(onBehalfOf))
        return transport.json.decodeFromJsonElement(StateResponseJson.serializer(), body!!)
    }

    public suspend fun profile(onBehalfOf: String? = null): ProfileResponseJson {
        val body = transport.get("$base/profile", headers = onBehalfOfHeader(onBehalfOf))
        return transport.json.decodeFromJsonElement(ProfileResponseJson.serializer(), body!!)
    }

    public suspend fun reflect(
        query: String,
        persist: Boolean = false,
        onBehalfOf: String? = null,
    ): ReflectResponseJson {
        val payload = buildJsonObject {
            put("query", query)
            if (persist) put("persist", true)
        }
        val body = transport.post("$base/reflect", payload, onBehalfOfHeader(onBehalfOf))
        return transport.json.decodeFromJsonElement(ReflectResponseJson.serializer(), body!!)
    }

    public suspend fun forget(
        query: String,
        purge: Boolean = false,
        onBehalfOf: String? = null,
    ): ForgetResponseJson {
        val payload = buildJsonObject {
            put("query", query)
            if (purge) put("purge", true)
        }
        val body = transport.post("$base/forget", payload, onBehalfOfHeader(onBehalfOf))
        return transport.json.decodeFromJsonElement(ForgetResponseJson.serializer(), body!!)
    }

    public suspend fun chat(
        message: String,
        sessionId: String? = null,
        scope: List<String>? = null,
        labels: List<String>? = null,
        model: String? = null,
        bypassCache: Boolean = false,
        onBehalfOf: String? = null,
    ): ChatResponseJson {
        val payload = buildJsonObject {
            put("message", message)
            sessionId?.let { put("sessionId", it) }
            putScope(scope)
            putStringList("labels", labels)
            model?.let { put("model", it) }
            if (bypassCache) put("bypassCache", true)
        }
        val body = transport.post("$base/chat", payload, onBehalfOfHeader(onBehalfOf))
        return transport.json.decodeFromJsonElement(ChatResponseJson.serializer(), body!!)
    }

    public suspend fun createFact(
        text: String? = null,
        infer: InferMode? = null,
        role: TurnRole? = null,
        memoryCategory: MemoryCategory? = null,
        triples: List<Triple>? = null,
        labels: List<String>? = null,
        scope: List<String>? = null,
        sessionId: String? = null,
        onBehalfOf: String? = null,
    ): FactsResponseJson {
        val payload = buildJsonObject {
            text?.let { put("text", it) }
            infer?.let { put("infer", it.wire) }
            role?.let { put("role", it.wire) }
            memoryCategory?.let { put("memory_category", it.wire) }
            triples?.let { list ->
                put("triples", buildJsonArray { list.forEach { add(transport.json.encodeToJsonElement(Triple.serializer(), it)) } })
            }
            putStringList("labels", labels)
            putScope(scope)
            sessionId?.let { put("session_id", it) }
        }
        val body = transport.post("$base/facts", payload, onBehalfOfHeader(onBehalfOf))
        return transport.json.decodeFromJsonElement(FactsResponseJson.serializer(), body!!)
    }

    public suspend fun createFactsBatch(
        messages: List<BatchMessage>,
        extract: BatchExtractionMode? = null,
        infer: InferMode? = null,
        labels: List<String>? = null,
        scope: List<String>? = null,
        sessionId: String? = null,
        onBehalfOf: String? = null,
    ): FactsBatchResponseJson {
        val payload = buildJsonObject {
            put("messages", buildJsonArray { messages.forEach { add(transport.json.encodeToJsonElement(BatchMessage.serializer(), it)) } })
            extract?.let { put("extract", it.wire) }
            infer?.let { put("infer", it.wire) }
            putStringList("labels", labels)
            putScope(scope)
            sessionId?.let { put("session_id", it) }
        }
        val body = transport.post("$base/facts/batch", payload, onBehalfOfHeader(onBehalfOf))
        return transport.json.decodeFromJsonElement(FactsBatchResponseJson.serializer(), body!!)
    }

    public suspend fun consolidate(
        dryRun: Boolean = false,
        factLimit: Int? = null,
        observationLimit: Int? = null,
        onBehalfOf: String? = null,
    ): ConsolidateResponseJson {
        val payload = buildJsonObject {
            if (dryRun) put("dryRun", true)
            factLimit?.let { put("factLimit", it) }
            observationLimit?.let { put("observationLimit", it) }
        }
        val body = transport.post("$base/consolidate", payload, onBehalfOfHeader(onBehalfOf))
        return transport.json.decodeFromJsonElement(ConsolidateResponseJson.serializer(), body!!)
    }

    public suspend fun elaborate(
        entityRef: String? = null,
        budget: Int? = null,
        dryRun: Boolean = false,
        sweep: Boolean = false,
        onBehalfOf: String? = null,
    ): ElaborateResponseJson {
        val payload = buildJsonObject {
            entityRef?.let { put("entityRef", it) }
            budget?.let { put("budget", it) }
            if (dryRun) put("dryRun", true)
            if (sweep) put("sweep", true)
        }
        val body = transport.post("$base/elaborate", payload, onBehalfOfHeader(onBehalfOf))
        return transport.json.decodeFromJsonElement(ElaborateResponseJson.serializer(), body!!)
    }

    public suspend fun inspect(
        ref: String,
        asOf: String? = null,
        atInstant: String? = null,
        validFrom: String? = null,
        validUntil: String? = null,
        onBehalfOf: String? = null,
    ): InspectResponseJson {
        val body = transport.get(
            "$base/inspect",
            mapOf(
                "ref" to ref,
                "asOf" to asOf,
                "atInstant" to atInstant,
                "validFrom" to validFrom,
                "validUntil" to validUntil,
            ),
            onBehalfOfHeader(onBehalfOf),
        )
        return transport.json.decodeFromJsonElement(InspectResponseJson.serializer(), body!!)
    }
}
