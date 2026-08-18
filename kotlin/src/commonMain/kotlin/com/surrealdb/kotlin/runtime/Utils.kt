package com.surrealdb.kotlin.runtime

import kotlin.random.Random

internal fun randomRequestId(): String {
    val now = Random.nextInt().toUInt().toString(16)
    val random = Random.nextLong().toString(16)
    return "$now-$random"
}

internal fun isWsUrl(url: String): Boolean =
    url.startsWith("ws://") || url.startsWith("wss://")

internal fun normalizeRpcEndpoint(url: String): String {
    val base = if (isWsUrl(url)) wsToHttpUrl(url) else url
    val trimmed = base.trimEnd('/')
    return if (trimmed.endsWith("/rpc")) trimmed else "$trimmed/rpc"
}

internal fun deriveWsEndpoint(url: String): String {
    val rpc = if (isWsUrl(url)) {
        val base = url.trimEnd('/')
        if (base.endsWith("/rpc")) base else "$base/rpc"
    } else {
        val trimmed = url.trimEnd('/')
        val withRpc = if (trimmed.endsWith("/rpc")) trimmed else "$trimmed/rpc"
        httpToWsUrl(withRpc)
    }
    return rpc
}

internal fun httpToWsUrl(url: String): String = when {
    url.startsWith("https://") -> "wss://${url.removePrefix("https://")}"
    url.startsWith("http://") -> "ws://${url.removePrefix("http://")}"
    else -> url
}

internal fun wsToHttpUrl(url: String): String = when {
    url.startsWith("wss://") -> "https://${url.removePrefix("wss://")}"
    url.startsWith("ws://") -> "http://${url.removePrefix("ws://")}"
    else -> url
}
