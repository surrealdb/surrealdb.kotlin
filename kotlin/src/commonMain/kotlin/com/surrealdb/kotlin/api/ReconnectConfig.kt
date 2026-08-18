package com.surrealdb.kotlin.api

public data class ReconnectConfig(
    val enabled: Boolean = true,
    val initialDelayMillis: Long = 250,
    val maxDelayMillis: Long = 30_000,
    val multiplier: Double = 1.5,
    val maxAttempts: Int? = null,
)
