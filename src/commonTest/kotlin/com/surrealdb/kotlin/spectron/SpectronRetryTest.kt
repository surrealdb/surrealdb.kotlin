package com.surrealdb.kotlin.spectron

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

class SpectronRetryTest {
    @Test
    fun scheduleRespectsCap() {
        assertEquals(emptyList(), backoffSchedule(0))
        assertEquals(listOf(250.milliseconds), backoffSchedule(1))
        assertEquals(
            listOf(250.milliseconds, 500.milliseconds, 1000.milliseconds),
            backoffSchedule(3),
        )
        assertEquals(
            listOf(250.milliseconds, 500.milliseconds, 1000.milliseconds),
            backoffSchedule(10),
        )
    }

    @Test
    fun retriesOnlyGets() {
        assertTrue(shouldRetry("GET", 503, 0, 3))
        assertTrue(shouldRetry("GET", null, 0, 3))
        assertFalse(shouldRetry("POST", 503, 0, 3))
        assertFalse(shouldRetry("PUT", null, 0, 3))
    }

    @Test
    fun stopsAtMaxRetries() {
        assertTrue(shouldRetry("GET", 503, 2, 3))
        assertFalse(shouldRetry("GET", 503, 3, 3))
    }

    @Test
    fun doesNotRetryClientErrors() {
        assertFalse(shouldRetry("GET", 404, 0, 3))
        assertFalse(shouldRetry("GET", 401, 0, 3))
        assertFalse(shouldRetry("GET", 200, 0, 3))
    }
}
