package com.surrealdb.kotlin.runtime.codec

import com.surrealdb.kotlin.api.live.SurrealLiveNotification
import com.surrealdb.kotlin.runtime.SurrealRpcResponse
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
