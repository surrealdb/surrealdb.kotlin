package com.surrealdb.kotlin.spectron

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.headers
import io.ktor.client.request.parameter
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.util.flattenEntries
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlin.time.Duration

internal val DEFAULT_TIMEOUT: Duration = Duration.parse("PT30S")
internal const val DEFAULT_MAX_RETRIES: Int = 3
private const val USER_AGENT_VALUE: String = "surrealdb-kotlin-spectron/1.0"

internal fun quotePath(value: String): String = buildString(value.length) {
    for (byte in value.encodeToByteArray()) {
        val b = byte.toInt() and 0xFF
        val c = b.toChar()
        val isUnreserved = (c in '0'..'9') ||
            (c in 'A'..'Z') ||
            (c in 'a'..'z') ||
            c == '-' || c == '_' || c == '.' || c == '~'
        if (isUnreserved) {
            append(c)
        } else {
            append('%')
            append(hexDigits[(b ushr 4) and 0xF])
            append(hexDigits[b and 0xF])
        }
    }
}

private val hexDigits: CharArray = "0123456789ABCDEF".toCharArray()

internal const val ON_BEHALF_OF_HEADER: String = "X-Spectron-On-Behalf-Of"

/** Build the optional delegation header. Empty when no principal is supplied. */
internal fun onBehalfOfHeader(principal: String?): Map<String, String> =
    if (principal.isNullOrEmpty()) emptyMap() else mapOf(ON_BEHALF_OF_HEADER to principal)

internal class SpectronTransport(
    public var endpoint: String,
    public var apiKey: String,
    private val httpClient: HttpClient,
    internal val json: Json,
    private val maxRetries: Int,
    private val ownsClient: Boolean,
) {
    init {
        require(apiKey.isNotEmpty()) { "Spectron API key is required" }
    }

    fun close() {
        if (ownsClient) httpClient.close()
    }

    suspend fun get(
        path: String,
        params: Map<String, Any?> = emptyMap(),
        headers: Map<String, String> = emptyMap(),
    ): JsonElement? = request(HttpMethod.Get, path, params = params, extraHeaders = headers)

    suspend fun post(
        path: String,
        body: JsonElement? = null,
        headers: Map<String, String> = emptyMap(),
        params: Map<String, Any?> = emptyMap(),
    ): JsonElement? = request(HttpMethod.Post, path, params = params, jsonBody = body, extraHeaders = headers)

    suspend fun put(
        path: String,
        body: JsonElement? = null,
        headers: Map<String, String> = emptyMap(),
    ): JsonElement? = request(HttpMethod.Put, path, jsonBody = body, extraHeaders = headers)

    suspend fun delete(
        path: String,
        body: JsonElement? = null,
        params: Map<String, Any?> = emptyMap(),
        headers: Map<String, String> = emptyMap(),
    ): JsonElement? = request(HttpMethod.Delete, path, params = params, jsonBody = body, extraHeaders = headers)

    suspend fun postMultipart(
        path: String,
        file: ByteArray,
        filename: String,
        mimeType: String?,
        fields: Map<String, String>,
        headers: Map<String, String> = emptyMap(),
    ): JsonElement? = request(
        HttpMethod.Post,
        path,
        multipart = buildMultipart(file, filename, mimeType, fields),
        extraHeaders = headers,
    )

    suspend fun putMultipart(
        path: String,
        file: ByteArray,
        filename: String,
        mimeType: String?,
        fields: Map<String, String>,
        headers: Map<String, String> = emptyMap(),
    ): JsonElement? = request(
        HttpMethod.Put,
        path,
        multipart = buildMultipart(file, filename, mimeType, fields),
        extraHeaders = headers,
    )

    suspend fun getRawBytes(path: String, headers: Map<String, String> = emptyMap()): ByteArray {
        val response = executeRequest(HttpMethod.Get, path, emptyMap(), null, null, headers)
        if (!response.status.isSuccess()) {
            handleError(response)
        }
        return response.body<ByteArray>()
    }

    private suspend fun request(
        method: HttpMethod,
        path: String,
        params: Map<String, Any?> = emptyMap(),
        jsonBody: JsonElement? = null,
        multipart: MultiPartFormDataContent? = null,
        extraHeaders: Map<String, String> = emptyMap(),
    ): JsonElement? {
        var attempt = 0
        val schedule = backoffSchedule(maxRetries)
        while (true) {
            val response = try {
                executeRequest(method, path, params, jsonBody, multipart, extraHeaders)
            } catch (cause: CancellationException) {
                throw cause
            } catch (cause: HttpRequestTimeoutException) {
                if (!shouldRetry(method.value, null, attempt, maxRetries)) {
                    throw SpectronTransportException(detail = cause.message, cause = cause)
                }
                delay(schedule[attempt])
                attempt++
                continue
            } catch (cause: Throwable) {
                if (!shouldRetry(method.value, null, attempt, maxRetries)) {
                    throw SpectronTransportException(detail = cause.message, cause = cause)
                }
                delay(schedule[attempt])
                attempt++
                continue
            }

            val status = response.status.value
            if (status >= 400) {
                if (shouldRetry(method.value, status, attempt, maxRetries)) {
                    delay(schedule[attempt])
                    attempt++
                    continue
                }
                handleError(response)
            }
            if (response.status == HttpStatusCode.NoContent) return null
            val text = response.bodyAsText()
            if (text.isEmpty()) return null
            return decodeJson(text)
        }
    }

    private suspend fun executeRequest(
        method: HttpMethod,
        path: String,
        params: Map<String, Any?>,
        jsonBody: JsonElement?,
        multipart: MultiPartFormDataContent?,
        extraHeaders: Map<String, String> = emptyMap(),
    ): HttpResponse {
        val url = buildUrl(path)
        return httpClient.request(url) {
            this.method = method
            headers {
                append(HttpHeaders.Authorization, "Bearer $apiKey")
                append(HttpHeaders.Accept, ContentType.Application.Json.toString())
                append(HttpHeaders.UserAgent, USER_AGENT_VALUE)
                for ((key, value) in extraHeaders) append(key, value)
            }
            for ((key, value) in params) {
                if (value != null) parameter(key, value.toString())
            }
            when {
                multipart != null -> setBody(multipart)
                jsonBody != null && jsonBody !is JsonNull -> {
                    headers.append(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    setBody(json.encodeToString(JsonElement.serializer(), jsonBody))
                }
            }
        }
    }

    private fun buildUrl(path: String): String {
        if (path.startsWith("http://") || path.startsWith("https://")) return path
        return endpoint.trimEnd('/') + "/" + path.trimStart('/')
    }

    private fun buildMultipart(
        file: ByteArray,
        filename: String,
        mimeType: String?,
        fields: Map<String, String>,
    ): MultiPartFormDataContent = MultiPartFormDataContent(
        formData {
            append(
                key = "file",
                value = file,
                headers = io.ktor.http.Headers.build {
                    append(
                        HttpHeaders.ContentDisposition,
                        "form-data; name=\"file\"; filename=\"$filename\"",
                    )
                    append(
                        HttpHeaders.ContentType,
                        mimeType ?: ContentType.Application.OctetStream.toString(),
                    )
                },
            )
            for ((key, value) in fields) {
                append(key, value)
            }
        },
    )

    private suspend fun handleError(response: HttpResponse): Nothing {
        val status = response.status.value
        val headerMap = response.headers.flattenEntries().toMap()
        val text = response.bodyAsText()
        val body: JsonElement? = if (text.isEmpty()) null else runCatching { decodeJson(text) }.getOrNull()
        throw errorFromResponse(status, body, headerMap)
    }

    private fun decodeJson(text: String): JsonElement = try {
        json.parseToJsonElement(text)
    } catch (cause: SerializationException) {
        throw SpectronTransportException(detail = "Failed to parse JSON: ${cause.message}", cause = cause)
    }
}

private fun HttpStatusCode.isSuccess(): Boolean = value in 200..299
