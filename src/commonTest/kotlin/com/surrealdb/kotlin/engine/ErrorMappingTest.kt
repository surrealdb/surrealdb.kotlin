package com.surrealdb.kotlin.engine

import com.surrealdb.kotlin.error.SurrealAuthenticationException
import com.surrealdb.kotlin.error.SurrealRpcException
import com.surrealdb.kotlin.model.SurrealRpcError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ErrorMappingTest {

    private fun mapped(message: String, code: Int? = -32000) =
        mapRpcError(SurrealRpcError(code = code, message = message, data = null))

    // ── auth-classified messages ──

    @Test
    fun `authentication keyword maps to auth exception`() {
        val ex = mapped("Authentication required")
        assertTrue(ex is SurrealAuthenticationException, "got ${ex::class.simpleName}")
    }

    @Test
    fun `not enough permission maps to auth exception`() {
        val ex = mapped("Not enough permissions to perform this action")
        assertTrue(ex is SurrealAuthenticationException)
    }

    @Test
    fun `invalid jwt maps to auth exception`() {
        val ex = mapped("Invalid JWT token format")
        assertTrue(ex is SurrealAuthenticationException)
    }

    @Test
    fun `token keyword maps to auth exception`() {
        val ex = mapped("The access token has expired")
        assertTrue(ex is SurrealAuthenticationException)
    }

    @Test
    fun `signin keyword maps to auth exception`() {
        val ex = mapped("Signin failed for user")
        assertTrue(ex is SurrealAuthenticationException)
    }

    @Test
    fun `signup keyword maps to auth exception`() {
        val ex = mapped("Signup failed: invalid scope")
        assertTrue(ex is SurrealAuthenticationException)
    }

    @Test
    fun `keyword detection is case insensitive`() {
        assertTrue(mapped("AUTHENTICATION REQUIRED") is SurrealAuthenticationException)
        assertTrue(mapped("Token Expired") is SurrealAuthenticationException)
        assertTrue(mapped("INVALID JWT") is SurrealAuthenticationException)
    }

    // ── non-auth messages stay as plain RpcException ──

    @Test
    fun `record-already-exists error is NOT auth`() {
        val ex = mapped("Database record `person:chiru` already exists")
        assertTrue(ex is SurrealRpcException)
        assertTrue(ex !is SurrealAuthenticationException)
    }

    @Test
    fun `table-not-found error is NOT auth`() {
        val ex = mapped("The table 'person' does not exist")
        assertTrue(ex !is SurrealAuthenticationException)
    }

    @Test
    fun `parse-error is NOT auth`() {
        val ex = mapped("Parse error at line 1: unexpected character")
        assertTrue(ex !is SurrealAuthenticationException)
    }

    @Test
    fun `code -32000 alone does NOT trigger auth classification`() {
        // Regression: -32000 is JSON-RPC's generic server error. Used for every
        // SurrealDB error. It must not by itself force auth classification.
        val ex = mapped("Some unrelated server error", code = -32000)
        assertTrue(ex !is SurrealAuthenticationException, "got ${ex::class.simpleName}")
    }

    // ── error fields are preserved ──

    @Test
    fun `code, message and data are forwarded to the exception`() {
        val error = SurrealRpcError(
            code = -32602,
            message = "Invalid params",
            data = kotlinx.serialization.json.JsonPrimitive("details"),
        )
        val ex = mapRpcError(error)
        assertEquals(-32602, ex.code)
        assertEquals("Invalid params", ex.message)
        assertEquals("details", (ex.data as kotlinx.serialization.json.JsonPrimitive).content)
    }
}
