package com.surrealdb.kotlin.spectron

import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

internal val backoffSteps: List<Duration> = listOf(250.milliseconds, 500.milliseconds, 1000.milliseconds)

internal fun backoffSchedule(maxRetries: Int): List<Duration> {
    val capped = maxRetries.coerceIn(0, backoffSteps.size)
    return backoffSteps.take(capped)
}

internal fun shouldRetry(method: String, status: Int?, attempt: Int, maxRetries: Int): Boolean {
    if (attempt >= maxRetries) return false
    if (method.uppercase() != "GET") return false
    if (status == null) return true
    return status >= 500
}
