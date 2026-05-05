package com.surrealdb.kotlin.internal

import com.surrealdb.kotlin.SurrealClientConfig
import com.surrealdb.kotlin.error.SurrealProtocolException
import com.surrealdb.kotlin.model.SurrealRpcRequest
import com.surrealdb.kotlin.model.SurrealRpcResponse
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString

internal class SurrealCodec(
    private val config: SurrealClientConfig,
) {
    fun contentTypeHeader(): String = "application/json"

    fun encodeHttpPayload(request: SurrealRpcRequest): ByteArray =
        config.json.encodeToString(request).encodeToByteArray()

    fun decodeHttpPayload(body: ByteArray): SurrealRpcResponse = try {
        config.json.decodeFromString(SurrealRpcResponse.serializer(), body.decodeToString())
    } catch (cause: SerializationException) {
        throw SurrealProtocolException("Unable to decode SurrealDB HTTP response", cause)
    }

    fun encodeWsText(request: SurrealRpcRequest): String = config.json.encodeToString(request)

    fun decodeWsText(text: String): SurrealRpcResponse = try {
        config.json.decodeFromString(SurrealRpcResponse.serializer(), text)
    } catch (cause: SerializationException) {
        throw SurrealProtocolException("Unable to decode SurrealDB websocket frame", cause)
    }
}
