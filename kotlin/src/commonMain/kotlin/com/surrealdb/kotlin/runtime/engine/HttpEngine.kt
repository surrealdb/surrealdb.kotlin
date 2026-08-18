package com.surrealdb.kotlin.runtime.engine

import com.surrealdb.kotlin.api.SurrealClientConfig
import com.surrealdb.kotlin.api.SurrealConnectionEvent
import com.surrealdb.kotlin.api.SurrealFeature
import com.surrealdb.kotlin.api.error.SurrealTransportException
import com.surrealdb.kotlin.runtime.SurrealRpcRequest
import com.surrealdb.kotlin.runtime.SurrealRpcResponse
import com.surrealdb.kotlin.runtime.codec.SurrealCodec
import com.surrealdb.kotlin.runtime.normalizeRpcEndpoint
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import kotlin.concurrent.Volatile

internal class HttpEngine(
    config: SurrealClientConfig,
    httpClient: HttpClient,
    codec: SurrealCodec,
) : RpcEngine(config, httpClient, codec) {

    override val features: Set<SurrealFeature> = setOf(
        SurrealFeature.ExportImport,
        SurrealFeature.SurrealML,
    )

    @Volatile private var started = false

    override suspend fun start() {
        if (started) return
        started = true
        publishEvent(SurrealConnectionEvent.Connected)
    }

    override suspend fun dispatch(
        request: SurrealRpcRequest,
        session: SessionSnapshot,
    ): SurrealRpcResponse {
        val endpoint = normalizeRpcEndpoint(config.url)
        val payload = codec.encodeHttpPayload(request)
        val contentType = codec.contentTypeHeader()

        val response = httpClient.post(endpoint) {
            headers {
                append(HttpHeaders.ContentType, contentType)
                append(HttpHeaders.Accept, contentType)
                session.token?.let { append(HttpHeaders.Authorization, "Bearer $it") }
                session.namespace?.let { append("Surreal-NS", it) }
                session.database?.let { append("Surreal-DB", it) }
            }
            setBody(payload)
        }

        val bytes = response.body<ByteArray>()
        if (!response.status.isSuccess()) {
            throw SurrealTransportException(
                "SurrealDB request failed with HTTP ${response.status.value}: ${bytes.decodeToString()}"
            )
        }

        return codec.decodeHttpPayload(bytes)
    }

    override fun close() {
        publishEvent(SurrealConnectionEvent.Disconnected)
    }
}
