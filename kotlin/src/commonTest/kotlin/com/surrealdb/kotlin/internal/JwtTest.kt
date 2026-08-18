package com.surrealdb.kotlin.internal

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@OptIn(ExperimentalEncodingApi::class)
class JwtTest {

    private fun jwt(payloadJson: String, urlSafe: Boolean = true): String {
        val header = encode("""{"alg":"HS256","typ":"JWT"}""", urlSafe)
        val payload = encode(payloadJson, urlSafe)
        val signature = encode("signature-bytes", urlSafe)
        return "$header.$payload.$signature"
    }

    private fun encode(text: String, urlSafe: Boolean): String {
        val b64 = Base64.encode(text.encodeToByteArray())
        // Standard base64 → drop padding and replace +/ with -_ for URL-safe variant
        return if (urlSafe) {
            b64.trimEnd('=').replace('+', '-').replace('/', '_')
        } else {
            b64
        }
    }

    @Test
    fun `parses standard exp claim and returns millis`() {
        val token = jwt("""{"exp":1700000000,"sub":"user"}""")
        assertEquals(1_700_000_000_000L, parseJwtExpiryMillis(token))
    }

    @Test
    fun `returns null when no exp claim is present`() {
        val token = jwt("""{"sub":"user","iat":1700000000}""")
        assertNull(parseJwtExpiryMillis(token))
    }

    @Test
    fun `returns null for empty string`() {
        assertNull(parseJwtExpiryMillis(""))
    }

    @Test
    fun `returns null for token with one segment`() {
        assertNull(parseJwtExpiryMillis("just-a-string"))
    }

    @Test
    fun `returns null when payload is not valid base64`() {
        assertNull(parseJwtExpiryMillis("header.@@@not-base64@@@.signature"))
    }

    @Test
    fun `returns null when payload is not valid JSON`() {
        val notJson = encode("not json at all", urlSafe = true)
        assertNull(parseJwtExpiryMillis("header.$notJson.signature"))
    }

    @Test
    fun `handles base64url padding-free payload`() {
        // Length 1 mod 4 → invalid; lengths 2 and 3 mod 4 are the padded cases.
        // Pick a payload whose base64 length is 2 mod 4 (one '=' of padding stripped).
        val payload = """{"exp":1}"""  // 9 bytes → base64 length 12 (no padding) — pick another
        val nine = encode(payload, urlSafe = true)
        // Use a payload that actually triggers padding stripping:
        val pad2 = """{"exp":12}"""    // 10 bytes → 16 chars with two '=' stripped to 14
        val token = "h.${encode(pad2, urlSafe = true)}.s"
        assertEquals(12_000L, parseJwtExpiryMillis(token))
        // Sanity: also the no-padding case
        val token9 = "h.$nine.s"
        assertEquals(1_000L, parseJwtExpiryMillis(token9))
    }

    @Test
    fun `handles base64url with - and _ characters`() {
        // Construct a payload guaranteed to produce '-' and '_' in URL-safe base64.
        // Standard base64 of bytes 0xfb 0xff 0xbf is "+/+/", URL-safe is "-_-_".
        // We pick a payload where standard b64 contains + or /:
        val raw = byteArrayOf(0xfb.toByte(), 0xff.toByte(), 0xbf.toByte())
        val urlSafe = Base64.encode(raw).trimEnd('=').replace('+', '-').replace('/', '_')
        // That payload is non-JSON, so we expect null — but the parser must not crash.
        assertNull(parseJwtExpiryMillis("h.$urlSafe.s"))
    }

    @Test
    fun `parses standard base64 (non-URL-safe) payload`() {
        // Some servers might emit standard base64. The parser handles it.
        val token = jwt("""{"exp":1234567890}""", urlSafe = false)
        assertEquals(1_234_567_890_000L, parseJwtExpiryMillis(token))
    }

    @Test
    fun `returns null when exp is not a number`() {
        val token = jwt("""{"exp":"not-a-number"}""")
        assertNull(parseJwtExpiryMillis(token))
    }
}
