package com.surrealdb.kotlin.engine

import com.surrealdb.kotlin.error.SurrealAlreadyExistsException
import com.surrealdb.kotlin.error.SurrealAuthenticationException
import com.surrealdb.kotlin.error.SurrealErrorKind
import com.surrealdb.kotlin.error.SurrealNotFoundException
import com.surrealdb.kotlin.error.SurrealQueryException
import com.surrealdb.kotlin.error.SurrealRpcException
import com.surrealdb.kotlin.model.SurrealRpcError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive

/**
 * Verifies [mapRpcError] classifies errors from the server's structured
 * `kind`/`details` wire fields (see `types/src/error.rs` server-side), not
 * from free-text `message` keyword matching. Each fixture below is the exact
 * JSON shape the server sends for that error family.
 */
class ErrorMappingTest {

    private val wireJson = Json { ignoreUnknownKeys = true }

    /** Decodes a raw wire error payload — exactly as it would arrive over HTTP/WS — and maps it. */
    private fun mapWire(rawErrorJson: String): SurrealRpcException =
        mapRpcError(wireJson.decodeFromString(SurrealRpcError.serializer(), rawErrorJson))

    // ── token-expired vs. generic invalid-auth ──────────────────────────────

    @Test
    fun `NotAllowed Auth TokenExpired maps to authentication exception with isTokenExpired`() {
        val ex = mapWire(
            """{"code":-32002,"message":"The token has expired","kind":"NotAllowed",""" +
                """"details":{"kind":"Auth","details":{"kind":"TokenExpired"}}}""",
        )
        assertIs<SurrealAuthenticationException>(ex)
        assertTrue(ex.isTokenExpired)
        assertFalse(ex.isInvalidAuth)
    }

    @Test
    fun `NotAllowed Auth InvalidAuth maps to authentication exception with isInvalidAuth`() {
        val ex = mapWire(
            """{"code":-32002,"message":"Invalid credentials","kind":"NotAllowed",""" +
                """"details":{"kind":"Auth","details":{"kind":"InvalidAuth"}}}""",
        )
        assertIs<SurrealAuthenticationException>(ex)
        assertTrue(ex.isInvalidAuth)
        assertFalse(ex.isTokenExpired)
    }

    @Test
    fun `token-expired and invalid-auth are distinguishable on the same exception type`() {
        val expired = mapWire(
            """{"message":"expired","kind":"NotAllowed","details":{"kind":"Auth","details":{"kind":"TokenExpired"}}}""",
        )
        val invalid = mapWire(
            """{"message":"invalid","kind":"NotAllowed","details":{"kind":"Auth","details":{"kind":"InvalidAuth"}}}""",
        )
        assertIs<SurrealAuthenticationException>(expired)
        assertIs<SurrealAuthenticationException>(invalid)
        assertTrue(expired.isTokenExpired && !expired.isInvalidAuth)
        assertTrue(invalid.isInvalidAuth && !invalid.isTokenExpired)
    }

    @Test
    fun `NotAllowed Auth NotAllowed reason carries actor action and resource`() {
        val ex = mapWire(
            """{"code":-32002,"message":"Not enough permissions","kind":"NotAllowed","details":""" +
                """{"kind":"Auth","details":{"kind":"NotAllowed",""" +
                """"details":{"actor":"user:tobie","action":"select","resource":"table:secret"}}}}""",
        )
        assertIs<SurrealAuthenticationException>(ex)
        assertFalse(ex.isTokenExpired)
        assertFalse(ex.isInvalidAuth)

        val kind = ex.kind
        assertIs<SurrealErrorKind.NotAllowed>(kind)
        val detail = kind.detail
        assertIs<SurrealErrorKind.NotAllowed.Detail.Auth>(detail)
        val reason = detail.reason
        assertIs<SurrealErrorKind.NotAllowed.AuthReason.NotPermitted>(reason)
        assertEquals("user:tobie", reason.actor)
        assertEquals("select", reason.action)
        assertEquals("table:secret", reason.resource)
    }

    @Test
    fun `NotAllowed Scripting is NOT an authentication exception`() {
        val ex = mapWire(
            """{"code":-32602,"message":"Scripting functions are not allowed","kind":"NotAllowed",""" +
                """"details":{"kind":"Scripting"}}""",
        )
        assertFalse(ex is SurrealAuthenticationException)

        val kind = ex.kind
        assertIs<SurrealErrorKind.NotAllowed>(kind)
        assertTrue(kind.isScriptingBlocked)
    }

    @Test
    fun `code -32000 alone does NOT force auth classification`() {
        // Regression: -32000 is JSON-RPC's generic server error, used for
        // every SurrealDB error. It must not by itself trigger any
        // classification without a structured `kind`.
        val ex = mapWire("""{"code":-32000,"message":"Some unrelated server error"}""")
        assertFalse(ex is SurrealAuthenticationException)
        assertEquals(SurrealErrorKind.Internal, ex.kind)
    }

    // ── not-found vs. already-exists ────────────────────────────────────────

    @Test
    fun `NotFound Record maps to not-found exception`() {
        val ex = mapWire(
            """{"message":"The record does not exist","kind":"NotFound",""" +
                """"details":{"kind":"Record","details":{"id":"person:chiru"}}}""",
        )
        assertIs<SurrealNotFoundException>(ex)

        val detail = ex.detail
        assertIs<SurrealErrorKind.NotFound.Detail.Record>(detail)
        assertEquals("person:chiru", detail.id)
    }

    @Test
    fun `AlreadyExists Record maps to already-exists exception`() {
        val ex = mapWire(
            """{"message":"Database record `person:chiru` already exists","kind":"AlreadyExists",""" +
                """"details":{"kind":"Record","details":{"id":"person:chiru"}}}""",
        )
        assertIs<SurrealAlreadyExistsException>(ex)

        val detail = ex.detail
        assertIs<SurrealErrorKind.AlreadyExists.Detail.Record>(detail)
        assertEquals("person:chiru", detail.id)
    }

    @Test
    fun `NotFound and AlreadyExists for the same table are distinguishable by exception type`() {
        val notFound = mapWire(
            """{"message":"missing","kind":"NotFound","details":{"kind":"Table","details":{"name":"person"}}}""",
        )
        val alreadyExists = mapWire(
            """{"message":"dup","kind":"AlreadyExists","details":{"kind":"Table","details":{"name":"person"}}}""",
        )
        assertIs<SurrealNotFoundException>(notFound)
        assertIs<SurrealAlreadyExistsException>(alreadyExists)
    }

    // ── transaction-conflict vs. other query failures ───────────────────────

    @Test
    fun `Query TransactionConflict is flagged retryable`() {
        val ex = mapWire(
            """{"code":-32009,"message":"Resource busy","kind":"Query","details":{"kind":"TransactionConflict"}}""",
        )
        assertIs<SurrealQueryException>(ex)
        assertTrue(ex.isTransactionConflict)
        assertFalse(ex.isTimedOut)
        assertFalse(ex.isCancelled)
        assertFalse(ex.isNotExecuted)
    }

    @Test
    fun `Query TimedOut is NOT a transaction conflict`() {
        val ex = mapWire(
            """{"code":-32004,"message":"Query timed out","kind":"Query",""" +
                """"details":{"kind":"TimedOut","details":{"duration":{"secs":5,"nanos":0}}}}""",
        )
        assertIs<SurrealQueryException>(ex)
        assertFalse(ex.isTransactionConflict)
        assertTrue(ex.isTimedOut)

        val kind = ex.kind
        assertIs<SurrealErrorKind.Query>(kind)
        val detail = kind.detail
        assertIs<SurrealErrorKind.Query.Detail.TimedOut>(detail)
        assertEquals(5L, detail.seconds)
        assertEquals(0L, detail.nanos)
    }

    @Test
    fun `Query Cancelled and NotExecuted are distinct from transaction conflict`() {
        val cancelled = mapWire("""{"message":"cancelled","kind":"Query","details":{"kind":"Cancelled"}}""")
        val notExecuted = mapWire("""{"message":"not executed","kind":"Query","details":{"kind":"NotExecuted"}}""")

        assertIs<SurrealQueryException>(cancelled)
        assertTrue(cancelled.isCancelled)
        assertFalse(cancelled.isTransactionConflict)

        assertIs<SurrealQueryException>(notExecuted)
        assertTrue(notExecuted.isNotExecuted)
        assertFalse(notExecuted.isTransactionConflict)
    }

    // ── forward compatibility / fallbacks ───────────────────────────────────

    @Test
    fun `missing kind falls back to Internal and a plain rpc exception`() {
        val ex = mapRpcError(SurrealRpcError(code = -32000, message = "legacy server error"))
        assertEquals(SurrealErrorKind.Internal, ex.kind)
        assertEquals(SurrealRpcException::class, ex::class)
    }

    @Test
    fun `unrecognised kind string falls back to Unknown instead of throwing`() {
        val ex = mapWire("""{"message":"from a newer server","kind":"SomeFutureKind"}""")

        val kind = ex.kind
        assertIs<SurrealErrorKind.Unknown>(kind)
        assertEquals("SomeFutureKind", kind.rawKind)
    }

    @Test
    fun `Thrown and Internal kinds map to a plain rpc exception`() {
        val thrown = mapWire("""{"message":"custom throw","kind":"Thrown"}""")
        val internal = mapWire("""{"message":"boom","kind":"Internal"}""")
        assertEquals(SurrealErrorKind.Thrown, thrown.kind)
        assertEquals(SurrealErrorKind.Internal, internal.kind)
        assertEquals(SurrealRpcException::class, thrown::class)
        assertEquals(SurrealRpcException::class, internal::class)
    }

    // ── error fields are preserved ──────────────────────────────────────────

    @Test
    fun `code message and data are forwarded to the exception`() {
        val error = SurrealRpcError(
            code = -32603,
            message = "Invalid params",
            data = JsonPrimitive("details"),
        )
        val ex = mapRpcError(error)
        assertEquals(-32603, ex.code)
        assertEquals("Invalid params", ex.message)
        assertEquals("details", (ex.data as JsonPrimitive).content)
    }
}
