package com.surrealdb.kotlin

import com.surrealdb.kotlin.query.Table
import com.surrealdb.kotlin.query.awaitAs
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Verifies that the `awaitAs<T>` builder terminals decode the unwrapped
 * first-statement result into the expected Kotlin type.
 *
 * Builders dispatch through the `query` RPC, so stubs must return the
 * `[{ status, time, result }]` envelope.
 */
class TypedHelpersTest {

    @Serializable
    data class Person(val id: String, val name: String, val age: Int = 0)

    private fun client(stub: String): SurrealClient {
        val engine = MockEngine { _ ->
            respond(
                content = stub,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }
        return SurrealClient(
            SurrealClientConfig(
                url = "http://localhost:8000",
                autoConnect = false,
                httpClientFactory = { _ -> HttpClient(engine) },
            ),
        )
    }

    private fun envelope(resultBody: String) =
        """{"id":"1","result":[{"status":"OK","time":"1ms","result":$resultBody}]}"""

    @Test
    fun `select awaitAs decodes single record`() = runTest {
        val stub = envelope("""{"id":"person:1","name":"Ada","age":30}""")
        val person: Person = client(stub).select("person:1").awaitAs()
        assertEquals("Ada", person.name)
        assertEquals(30, person.age)
    }

    @Test
    fun `create awaitAs decodes the created record`() = runTest {
        val stub = envelope("""{"id":"person:1","name":"Ada","age":30}""")
        val person: Person = client(stub).create("person:1")
            .content(buildJsonObject { put("name", JsonPrimitive("Ada")) })
            .awaitAs()
        assertEquals("Ada", person.name)
    }

    @Test
    fun `insert awaitAs decodes a list of inserted records`() = runTest {
        val stub = envelope("""[{"id":"person:1","name":"Ada","age":30}]""")
        val people: List<Person> = client(stub).insert(
            Table("person"),
            buildJsonArray { add(buildJsonObject { put("name", JsonPrimitive("Ada")) }) },
        ).awaitAs()
        assertEquals(1, people.size)
        assertEquals("Ada", people[0].name)
    }

    @Test
    fun `upsert awaitAs decodes the upserted record`() = runTest {
        val stub = envelope("""{"id":"person:1","name":"Ada","age":30}""")
        val person: Person = client(stub).upsert("person:1")
            .content(buildJsonObject { put("name", JsonPrimitive("Ada")) })
            .awaitAs()
        assertEquals("Ada", person.name)
    }

    @Test
    fun `update awaitAs decodes the updated record`() = runTest {
        val stub = envelope("""{"id":"person:1","name":"Ada","age":30}""")
        val person: Person = client(stub).update("person:1")
            .content(buildJsonObject { put("name", JsonPrimitive("Ada")) })
            .awaitAs()
        assertEquals("Ada", person.name)
    }

    @Test
    fun `merge awaitAs decodes the merged record`() = runTest {
        val stub = envelope("""{"id":"person:1","name":"Ada","age":31}""")
        val person: Person = client(stub)
            .merge("person:1", buildJsonObject { put("age", JsonPrimitive(31)) })
            .awaitAs()
        assertEquals(31, person.age)
    }

    @Test
    fun `delete awaitAs decodes the deleted record`() = runTest {
        val stub = envelope("""{"id":"person:1","name":"Ada","age":30}""")
        val person: Person = client(stub).delete("person:1").awaitAs()
        assertEquals("person:1", person.id)
    }

    @Test
    fun `queryAs decodes the query result envelope`() = runTest {
        @Serializable
        data class StatementResult<T>(val status: String, val result: T)
        val stub = """{"id":"1","result":[{"status":"OK","result":[{"id":"person:1","name":"Ada","age":30}]}]}"""
        val results: List<StatementResult<List<Person>>> = client(stub).queryAs("SELECT * FROM person")
        assertEquals(1, results.size)
        assertEquals("OK", results[0].status)
        assertEquals("Ada", results[0].result[0].name)
    }
}
