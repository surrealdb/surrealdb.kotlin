package com.surrealdb.kotlin.runtime.codec

import com.surrealdb.kotlin.api.SurrealClientConfig
import com.surrealdb.kotlin.api.error.SurrealProtocolException
import com.surrealdb.kotlin.runtime.SurrealRpcRequest
import com.surrealdb.kotlin.runtime.SurrealRpcResponse
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

internal class SurrealCodec(
    private val config: SurrealClientConfig,
) {
    // Envelope-only Json: forced explicitNulls=false so optional fields like
    // `txn` are omitted from the wire when absent. Inherits the user-configured
    // serializer settings otherwise so any custom modules still apply.
    private val envelopeJson: Json = Json(from = config.json) {
        explicitNulls = false
    }

    fun contentTypeHeader(): String = "application/json"

    fun encodeHttpPayload(request: SurrealRpcRequest): ByteArray =
        envelopeJson.encodeToString(request).encodeToByteArray()

    fun decodeHttpPayload(body: ByteArray): SurrealRpcResponse = try {
        envelopeJson.decodeFromString(SurrealRpcResponse.serializer(), body.decodeToString())
    } catch (cause: SerializationException) {
        throw SurrealProtocolException("Unable to decode SurrealDB HTTP response", cause)
    }

    fun encodeWsText(request: SurrealRpcRequest): String = envelopeJson.encodeToString(request)

    fun decodeWsText(text: String): SurrealRpcResponse = try {
        envelopeJson.decodeFromString(SurrealRpcResponse.serializer(), text)
    } catch (cause: SerializationException) {
        throw SurrealProtocolException("Unable to decode SurrealDB websocket frame", cause)
    }
}
