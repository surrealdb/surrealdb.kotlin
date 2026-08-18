package com.surrealdb.kotlin.internal

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * Returns the `exp` claim of a JWT in epoch milliseconds, or null if the token
 * cannot be parsed or has no expiry.
 */
@OptIn(ExperimentalEncodingApi::class)
internal fun parseJwtExpiryMillis(token: String): Long? {
    val parts = token.split('.')
    if (parts.size < 2) return null
    val payload = parts[1]
    return runCatching {
        val standard = payload.replace('-', '+').replace('_', '/')
        val padded = when (standard.length % 4) {
            0 -> standard
            2 -> "$standard=="
            3 -> "$standard="
            else -> standard
        }
        val decoded = Base64.decode(padded).decodeToString()
        val obj = Json.parseToJsonElement(decoded).jsonObject
        obj["exp"]?.jsonPrimitive?.longOrNull?.let { it * 1000 }
    }.getOrNull()
}
