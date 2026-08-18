package com.surrealdb.kotlin.internal

import com.surrealdb.kotlin.model.SurrealLiveNotification
import com.surrealdb.kotlin.model.SurrealRpcResponse
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

internal fun parseLiveNotification(response: SurrealRpcResponse): SurrealLiveNotification? {
    val result = response.result as? JsonObject ?: return null
    val action = result["action"]?.jsonPrimitive?.content ?: return null
    val liveQueryId = result["id"]?.jsonPrimitive?.content ?: return null
    val payload = result["result"] ?: JsonNull
    return SurrealLiveNotification(action = action, liveQueryId = liveQueryId, result = payload)
}
