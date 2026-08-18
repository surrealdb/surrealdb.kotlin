package com.surrealdb.kotlin.api.live

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
public data class SurrealLiveNotification(
    val action: String,
    @SerialName("id") val liveQueryId: String,
    val result: JsonElement,
)
