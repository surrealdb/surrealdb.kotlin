package com.surrealdb.kotlin.runtime

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UtilsTest {

    // ── isWsUrl ──

    @Test
    fun `isWsUrl recognises ws and wss schemes`() {
        assertTrue(isWsUrl("ws://localhost:8000"))
        assertTrue(isWsUrl("wss://example.com/rpc"))
    }

    @Test
    fun `isWsUrl rejects http and https schemes`() {
        assertFalse(isWsUrl("http://localhost:8000"))
        assertFalse(isWsUrl("https://example.com"))
    }

    @Test
    fun `isWsUrl rejects unknown schemes`() {
        assertFalse(isWsUrl("ftp://example.com"))
        assertFalse(isWsUrl("localhost:8000"))
        assertFalse(isWsUrl(""))
    }

    // ── normalizeRpcEndpoint ──

    @Test
    fun `normalizeRpcEndpoint appends rpc when missing`() {
        assertEquals("http://localhost:8000/rpc", normalizeRpcEndpoint("http://localhost:8000"))
    }

    @Test
    fun `normalizeRpcEndpoint preserves rpc when already present`() {
        assertEquals("http://localhost:8000/rpc", normalizeRpcEndpoint("http://localhost:8000/rpc"))
    }

    @Test
    fun `normalizeRpcEndpoint strips trailing slash before appending rpc`() {
        assertEquals("http://localhost:8000/rpc", normalizeRpcEndpoint("http://localhost:8000/"))
    }

    @Test
    fun `normalizeRpcEndpoint converts ws-scheme to http`() {
        // The HTTP path uses normalize with an http endpoint; if the user provided
        // a ws:// URL, we still want a valid HTTP target derived from it.
        assertEquals("http://localhost:8000/rpc", normalizeRpcEndpoint("ws://localhost:8000"))
        assertEquals("https://example.com/rpc", normalizeRpcEndpoint("wss://example.com"))
    }

    // ── deriveWsEndpoint ──

    @Test
    fun `deriveWsEndpoint converts http to ws and appends rpc`() {
        assertEquals("ws://localhost:8000/rpc", deriveWsEndpoint("http://localhost:8000"))
    }

    @Test
    fun `deriveWsEndpoint converts https to wss and appends rpc`() {
        assertEquals("wss://example.com/rpc", deriveWsEndpoint("https://example.com"))
    }

    @Test
    fun `deriveWsEndpoint preserves ws scheme and appends rpc when missing`() {
        assertEquals("ws://localhost:8000/rpc", deriveWsEndpoint("ws://localhost:8000"))
    }

    @Test
    fun `deriveWsEndpoint preserves rpc suffix`() {
        assertEquals("ws://localhost:8000/rpc", deriveWsEndpoint("http://localhost:8000/rpc"))
        assertEquals("ws://localhost:8000/rpc", deriveWsEndpoint("ws://localhost:8000/rpc"))
    }

    // ── httpToWsUrl / wsToHttpUrl round-trips ──

    @Test
    fun `httpToWsUrl converts http to ws and https to wss`() {
        assertEquals("ws://example.com/rpc", httpToWsUrl("http://example.com/rpc"))
        assertEquals("wss://example.com/rpc", httpToWsUrl("https://example.com/rpc"))
    }

    @Test
    fun `httpToWsUrl is identity for ws URLs`() {
        assertEquals("ws://example.com/rpc", httpToWsUrl("ws://example.com/rpc"))
        assertEquals("wss://example.com/rpc", httpToWsUrl("wss://example.com/rpc"))
    }

    @Test
    fun `wsToHttpUrl converts ws to http and wss to https`() {
        assertEquals("http://example.com/rpc", wsToHttpUrl("ws://example.com/rpc"))
        assertEquals("https://example.com/rpc", wsToHttpUrl("wss://example.com/rpc"))
    }

    @Test
    fun `wsToHttpUrl is identity for http URLs`() {
        assertEquals("http://example.com/rpc", wsToHttpUrl("http://example.com/rpc"))
        assertEquals("https://example.com/rpc", wsToHttpUrl("https://example.com/rpc"))
    }

    // ── randomRequestId ──

    @Test
    fun `randomRequestId produces unique values`() {
        val ids = (1..1000).map { randomRequestId() }.toSet()
        assertEquals(1000, ids.size, "expected 1000 unique IDs, got ${ids.size}")
    }
}
