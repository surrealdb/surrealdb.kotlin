package com.surrealdb.kotlin.api.query

import com.surrealdb.kotlin.api.error.SurrealProtocolException
import com.surrealdb.kotlin.api.error.SurrealRpcException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Extract the inner `result` of the first statement from a `query` RPC
 * response. SurrealDB returns `[{ status, time, result, type }, ...]` with one
 * entry per statement; the builder layer always sends a single statement so we
 * take the first entry and unwrap its `result`.
 *
 * Throws [SurrealRpcException] if the statement reports `status: "ERR"`.
 */
internal fun firstQueryResult(response: JsonElement): JsonElement {
    val array = response as? JsonArray
        ?: throw SurrealProtocolException("Expected array response from query, got: $response")
    val first = array.firstOrNull() as? JsonObject
        ?: return JsonNull
    val status = first["status"]?.jsonPrimitive?.content
    if (status == "ERR") {
        val message = first["result"]?.jsonPrimitive?.content ?: "query failed"
        throw SurrealRpcException(code = null, message = message, data = first)
    }
    return first["result"] ?: JsonNull
}
