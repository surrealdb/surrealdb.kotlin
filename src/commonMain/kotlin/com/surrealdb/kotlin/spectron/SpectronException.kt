package com.surrealdb.kotlin.spectron

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

public sealed class SpectronException(
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

public class SpectronAuthException(
    status: Int,
    title: String,
    detail: String? = null,
    typeUri: String? = null,
    instance: String? = null,
    extensions: Map<String, JsonElement> = emptyMap(),
) : SpectronException(status, title, detail, typeUri, instance, extensions)

public class SpectronScopeException(
    status: Int,
    title: String,
    detail: String? = null,
    typeUri: String? = null,
    instance: String? = null,
    extensions: Map<String, JsonElement> = emptyMap(),
) : SpectronException(status, title, detail, typeUri, instance, extensions)

public class SpectronNotFoundException(
    status: Int,
    title: String,
    detail: String? = null,
    typeUri: String? = null,
    instance: String? = null,
    extensions: Map<String, JsonElement> = emptyMap(),
) : SpectronException(status, title, detail, typeUri, instance, extensions)

public class SpectronValidationException(
    status: Int,
    title: String,
    detail: String? = null,
    typeUri: String? = null,
    instance: String? = null,
    extensions: Map<String, JsonElement> = emptyMap(),
) : SpectronException(status, title, detail, typeUri, instance, extensions)

public class SpectronRateLimitException(
    status: Int,
    title: String,
    detail: String? = null,
    typeUri: String? = null,
    instance: String? = null,
    extensions: Map<String, JsonElement> = emptyMap(),
    public val retryAfter: Duration? = null,
) : SpectronException(status, title, detail, typeUri, instance, extensions)

public class SpectronServerException(
    status: Int,
    title: String,
    detail: String? = null,
    typeUri: String? = null,
    instance: String? = null,
    extensions: Map<String, JsonElement> = emptyMap(),
    cause: Throwable? = null,
) : SpectronException(status, title, detail, typeUri, instance, extensions, cause)

public class SpectronTransportException(
    status: Int = 0,
    title: String = "Connection failed",
    detail: String? = null,
    cause: Throwable? = null,
) : SpectronException(status, title, detail, cause = cause)

internal fun errorFromResponse(
    status: Int,
    body: JsonElement?,
    headers: Map<String, String>,
): SpectronException {
    var title = "Spectron request failed"
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
        status == 401 -> SpectronAuthException(status, title, detail, typeUri, instance, extensions)
        status == 403 -> SpectronScopeException(status, title, detail, typeUri, instance, extensions)
        status == 404 -> SpectronNotFoundException(status, title, detail, typeUri, instance, extensions)
        status == 400 || status == 422 ->
            SpectronValidationException(status, title, detail, typeUri, instance, extensions)
        status == 429 -> SpectronRateLimitException(
            status, title, detail, typeUri, instance, extensions,
            retryAfter = parseRetryAfter(headers),
        )
        status >= 500 -> SpectronServerException(status, title, detail, typeUri, instance, extensions)
        else -> SpectronServerException(status, title, detail, typeUri, instance, extensions)
    }
}

private val reservedErrorKeys = setOf("status", "title", "detail", "type", "instance", "message")

private fun parseRetryAfter(headers: Map<String, String>): Duration? {
    val raw = headers["Retry-After"] ?: headers["retry-after"] ?: return null
    val seconds = raw.toDoubleOrNull() ?: return null
    return seconds.seconds
}
