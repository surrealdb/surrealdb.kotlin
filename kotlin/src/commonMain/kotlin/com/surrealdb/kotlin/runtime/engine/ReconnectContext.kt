package com.surrealdb.kotlin.runtime.engine

import com.surrealdb.kotlin.api.ReconnectConfig

internal class ReconnectContext(private val config: ReconnectConfig) {
    private var attempts = 0

    val allowed: Boolean
        get() = config.enabled && (config.maxAttempts == null || attempts < config.maxAttempts)

    val attempt: Int
        get() = attempts

    fun reset() {
        attempts = 0
    }

    fun nextDelay(): Long {
        var delay = config.initialDelayMillis.toDouble()
        repeat(attempts) { delay *= config.multiplier }
        attempts++
        return delay.toLong().coerceAtMost(config.maxDelayMillis)
    }
}
