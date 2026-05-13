package com.surrealdb.kotlin.spectron

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SpectronScopeTest {
    @Test
    fun serialiseNull() {
        assertNull(serialiseScope(null))
    }

    @Test
    fun serialiseRoundTrip() {
        val scope = mapOf("org" to "anneal", "user" to "tobie")
        val wire = serialiseScope(scope)!!
        assertEquals(2, wire.size)
        assertEquals(ScopeEntry("org", "anneal"), wire[0])
        assertEquals(ScopeEntry("user", "tobie"), wire[1])
        assertEquals(scope, deserialiseScope(wire))
    }

    @Test
    fun deserialiseEmpty() {
        assertEquals(emptyMap(), deserialiseScope(null))
        assertEquals(emptyMap(), deserialiseScope(emptyList()))
    }
}
