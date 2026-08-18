package com.surrealdb.kotlin

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Guards the `api` / `runtime` package boundary.
 *
 * `api` is what a user imports; `runtime` is how it works. A boundary that exists only in the
 * folder names is documentation, not architecture, so this asserts it against the source.
 *
 * The rule is a ratchet, not a ban: [ALLOWED] pins the crossings that exist today, and the test
 * fails both when a new one appears and when a listed one disappears — the second so that the
 * list shrinks deliberately rather than rotting.
 *
 * `ConnectionController` is the one entry. It never reaches the published surface: `SurrealSession`
 * holds it as `internal val controller` and `SurrealClient` names it only inside a constructor
 * body, so no runtime type appears in any public signature. Removing it means putting an interface
 * in `api` for `runtime` to implement; until that is worth doing, it is pinned here in the open.
 */
class ApiRuntimeBoundaryTest {
    private val allowed =
        setOf(
            "SurrealClient.kt -> com.surrealdb.kotlin.runtime.ConnectionController",
            "SurrealSession.kt -> com.surrealdb.kotlin.runtime.ConnectionController",
        )

    private val apiRoot = File("src/commonMain/kotlin/com/surrealdb/kotlin/api")

    @Test
    fun `api does not import runtime, beyond the pinned crossings`() {
        assertTrue(
            apiRoot.isDirectory,
            "Expected the api sources at ${apiRoot.absolutePath}. This test reads the source tree " +
                "and assumes Gradle's default working directory (the module directory).",
        )

        val found =
            apiRoot.walkTopDown()
                .filter { it.isFile && it.extension == "kt" }
                .flatMap { file ->
                    file.readLines()
                        .mapNotNull { line -> IMPORT.find(line)?.groupValues?.get(1) }
                        .map { imported -> "${file.name} -> $imported" }
                }
                .toSortedSet()

        assertEquals(
            allowed.toSortedSet(),
            found,
            "The api -> runtime boundary moved.\n" +
                "  new crossings:  ${(found - allowed).ifEmpty { "-" }}\n" +
                "  gone from list: ${(allowed - found).ifEmpty { "-" }}\n" +
                "A new crossing means api now names machinery — put an interface in api and " +
                "implement it in runtime, or, if the crossing is genuinely justified, add it to " +
                "ALLOWED with the reason. A crossing that disappeared is good news: delete it here.",
        )
    }

    private companion object {
        val IMPORT = Regex("""^import (com\.surrealdb\.kotlin\.runtime[A-Za-z0-9_.]*)""")
    }
}
