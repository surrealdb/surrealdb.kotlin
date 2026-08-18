package com.surrealdb.kotlin.runtime.engine

import com.surrealdb.kotlin.api.ReconnectConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReconnectContextTest {

    @Test
    fun `is allowed when enabled and within max attempts`() {
        val ctx = ReconnectContext(ReconnectConfig(enabled = true, maxAttempts = 3))
        assertTrue(ctx.allowed)
    }

    @Test
    fun `is not allowed when disabled`() {
        val ctx = ReconnectContext(ReconnectConfig(enabled = false))
        assertFalse(ctx.allowed)
    }

    @Test
    fun `is not allowed when max attempts reached`() {
        val ctx = ReconnectContext(ReconnectConfig(enabled = true, maxAttempts = 2))
        ctx.nextDelay() // attempt 1
        ctx.nextDelay() // attempt 2
        assertFalse(ctx.allowed)
    }

    @Test
    fun `unlimited attempts when maxAttempts is null`() {
        val ctx = ReconnectContext(ReconnectConfig(enabled = true, maxAttempts = null))
        repeat(100) { ctx.nextDelay() }
        assertTrue(ctx.allowed)
    }

    @Test
    fun `first delay equals initial delay`() {
        val ctx = ReconnectContext(
            ReconnectConfig(initialDelayMillis = 250, multiplier = 2.0, maxDelayMillis = 30_000)
        )
        assertEquals(250L, ctx.nextDelay())
    }

    @Test
    fun `delay grows by multiplier each attempt`() {
        val ctx = ReconnectContext(
            ReconnectConfig(initialDelayMillis = 100, multiplier = 2.0, maxDelayMillis = 60_000)
        )
        assertEquals(100L, ctx.nextDelay())   // 100 * 2^0
        assertEquals(200L, ctx.nextDelay())   // 100 * 2^1
        assertEquals(400L, ctx.nextDelay())   // 100 * 2^2
        assertEquals(800L, ctx.nextDelay())   // 100 * 2^3
    }

    @Test
    fun `delay caps at max delay`() {
        val ctx = ReconnectContext(
            ReconnectConfig(initialDelayMillis = 1000, multiplier = 10.0, maxDelayMillis = 5_000)
        )
        assertEquals(1000L, ctx.nextDelay())   // 1000
        assertEquals(5000L, ctx.nextDelay())   // would be 10_000, capped to 5_000
        assertEquals(5000L, ctx.nextDelay())   // would be 100_000, still capped
    }

    @Test
    fun `reset returns to initial delay`() {
        val ctx = ReconnectContext(
            ReconnectConfig(initialDelayMillis = 100, multiplier = 2.0, maxDelayMillis = 60_000)
        )
        ctx.nextDelay()
        ctx.nextDelay()
        ctx.nextDelay()
        ctx.reset()
        assertEquals(0, ctx.attempt)
        assertEquals(100L, ctx.nextDelay())
    }

    @Test
    fun `attempt counter increments per call`() {
        val ctx = ReconnectContext(ReconnectConfig())
        assertEquals(0, ctx.attempt)
        ctx.nextDelay()
        assertEquals(1, ctx.attempt)
        ctx.nextDelay()
        assertEquals(2, ctx.attempt)
    }

    @Test
    fun `non-integer multiplier still produces monotonically growing delays`() {
        val ctx = ReconnectContext(
            ReconnectConfig(initialDelayMillis = 100, multiplier = 1.5, maxDelayMillis = 60_000)
        )
        var previous = 0L
        repeat(8) {
            val next = ctx.nextDelay()
            assertTrue(next >= previous, "delay must not decrease: $next < $previous")
            previous = next
        }
    }
}
