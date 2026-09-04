package com.surrealdb.kotlin.memory

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

public sealed class AgentMemoryException(
    public val status: Int,
    public val title: String,
    public val detail: String? = null,
    public val typeUri: String? = null,
    public val instance: String? = null,
    public val extensions: Map<String, JsonElement> = emptyMap(),
    cause: Throwable? = null,
) : RuntimeException(buildMessage(status, title, detail), cause)

private fun buildMessage(status: Int, title: String, detail: String?): String =
    if (detail.isNullOrEmpty()) "[$status] $title" else "[$status] $title: $detail"

public class AgentMemoryAuthException(
    status: Int,
    title: String,
    detail: String? = null,
    typeUri: String? = null,
    instance: String? = null,
    extensions: Map<String, JsonElement> = emptyMap(),
) : AgentMemoryException(status, title, detail, typeUri, instance, extensions)

public class AgentMemoryScopeException(
    status: Int,
    title: String,
    detail: String? = null,
    typeUri: String? = null,
    instance: String? = null,
    extensions: Map<String, JsonElement> = emptyMap(),
) : AgentMemoryException(status, title, detail, typeUri, instance, extensions)

public class AgentMemoryNotFoundException(
    status: Int,
    title: String,
    detail: String? = null,
    typeUri: String? = null,
    instance: String? = null,
    extensions: Map<String, JsonElement> = emptyMap(),
) : AgentMemoryException(status, title, detail, typeUri, instance, extensions)

public class AgentMemoryValidationException(
    status: Int,
    title: String,
    detail: String? = null,
    typeUri: String? = null,
    instance: String? = null,
    extensions: Map<String, JsonElement> = emptyMap(),
) : AgentMemoryException(status, title, detail, typeUri, instance, extensions)

public class AgentMemoryRateLimitException(
    status: Int,
    title: String,
    detail: String? = null,
    typeUri: String? = null,
    instance: String? = null,
    extensions: Map<String, JsonElement> = emptyMap(),
    public val retryAfter: Duration? = null,
) : AgentMemoryException(status, title, detail, typeUri, instance, extensions)

public class AgentMemoryServerException(
    status: Int,
    title: String,
    detail: String? = null,
    typeUri: String? = null,
    instance: String? = null,
    extensions: Map<String, JsonElement> = emptyMap(),
    cause: Throwable? = null,
) : AgentMemoryException(status, title, detail, typeUri, instance, extensions, cause)

public class AgentMemoryTransportException(
    status: Int = 0,
    title: String = "Connection failed",
    detail: String? = null,
    cause: Throwable? = null,
) : AgentMemoryException(status, title, detail, cause = cause)

internal fun errorFromResponse(
    status: Int,
    body: JsonElement?,
    headers: Map<String, String>,
): AgentMemoryException {
    var title = "AgentMemory request failed"
    var detail: String? = null
    var typeUri: String? = null
    var instance: String? = null
    val extensions = mutableMapOf<String, JsonElement>()

    if (body is JsonObject) {
        (body["title"] as? JsonPrimitive)?.contentOrNull?.let { title = it }
            ?: (body["message"] as? JsonPrimitive)?.contentOrNull?.let { title = it }
        detail = (body["detail"] as? JsonPrimitive)?.contentOrNull
        typeUri = (body["type"] as? JsonPrimitive)?.contentOrNull
        instance = (body["instance"] as? JsonPrimitive)?.contentOrNull
        for ((key, value) in body) {
            if (key !in reservedErrorKeys) extensions[key] = value
        }
    } else if (body is JsonPrimitive) {
        detail = body.contentOrNull
    }

    return when {
        status == 401 -> AgentMemoryAuthException(status, title, detail, typeUri, instance, extensions)
        status == 403 -> AgentMemoryScopeException(status, title, detail, typeUri, instance, extensions)
        status == 404 -> AgentMemoryNotFoundException(status, title, detail, typeUri, instance, extensions)
        status == 400 || status == 422 ->
            AgentMemoryValidationException(status, title, detail, typeUri, instance, extensions)
        status == 429 -> AgentMemoryRateLimitException(
            status, title, detail, typeUri, instance, extensions,
            retryAfter = parseRetryAfter(headers),
        )
        status >= 500 -> AgentMemoryServerException(status, title, detail, typeUri, instance, extensions)
        else -> AgentMemoryServerException(status, title, detail, typeUri, instance, extensions)
    }
}

private val reservedErrorKeys = setOf("status", "title", "detail", "type", "instance", "message")

private fun parseRetryAfter(headers: Map<String, String>): Duration? {
    val raw = headers["Retry-After"] ?: headers["retry-after"] ?: return null
    val seconds = raw.toDoubleOrNull() ?: return null
    return seconds.seconds
}
