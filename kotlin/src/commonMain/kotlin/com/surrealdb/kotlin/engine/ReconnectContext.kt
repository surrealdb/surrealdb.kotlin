package com.surrealdb.kotlin.engine

public data class ReconnectConfig(
    val enabled: Boolean = true,
    val initialDelayMillis: Long = 250,
    val maxDelayMillis: Long = 30_000,
    val multiplier: Double = 1.5,
    val maxAttempts: Int? = null,
)

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
