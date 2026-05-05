package com.surrealdb.kotlin

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
 * Verifies the typed `*As<T>` decode helpers route through the engine and
 * deserialize the response into the expected Kotlin type.
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

    private val singlePerson = """{"id":"1","result":{"id":"person:1","name":"Ada","age":30}}"""

    @Test
    fun `selectAs decodes single record`() = runTest {
        val person: Person = client(singlePerson).selectAs("person:1")
        assertEquals("Ada", person.name)
        assertEquals(30, person.age)
    }

    @Test
    fun `createAs decodes the created record`() = runTest {
        val person: Person = client(singlePerson).createAs(
            thing = "person:1",
            data = buildJsonObject { put("name", JsonPrimitive("Ada")) },
        )
        assertEquals("Ada", person.name)
    }

    @Test
    fun `insertAs decodes a list of inserted records`() = runTest {
        val stub = """{"id":"1","result":[{"id":"person:1","name":"Ada","age":30}]}"""
        val people: List<Person> = client(stub).insertAs(
            thing = "person",
            data = buildJsonArray { add(buildJsonObject { put("name", JsonPrimitive("Ada")) }) },
        )
        assertEquals(1, people.size)
        assertEquals("Ada", people[0].name)
    }

    @Test
    fun `upsertAs decodes the upserted record`() = runTest {
        val person: Person = client(singlePerson).upsertAs(
            thing = "person:1",
            data = buildJsonObject { put("name", JsonPrimitive("Ada")) },
        )
        assertEquals("Ada", person.name)
    }

    @Test
    fun `updateAs decodes the updated record`() = runTest {
        val person: Person = client(singlePerson).updateAs(
            thing = "person:1",
            data = buildJsonObject { put("name", JsonPrimitive("Ada")) },
        )
        assertEquals("Ada", person.name)
    }

    @Test
    fun `mergeAs decodes the merged record`() = runTest {
        val person: Person = client(singlePerson).mergeAs(
            thing = "person:1",
            data = buildJsonObject { put("age", JsonPrimitive(31)) },
        )
        assertEquals("Ada", person.name)
    }

    @Test
    fun `patchAs decodes the patched record`() = runTest {
        val person: Person = client(singlePerson).patchAs(
            thing = "person:1",
            patches = buildJsonArray {},
        )
        assertEquals("person:1", person.id)
    }

    @Test
    fun `deleteAs decodes the deleted record`() = runTest {
        val person: Person = client(singlePerson).deleteAs("person:1")
        assertEquals("Ada", person.name)
    }

    @Test
    fun `queryAs decodes the query result envelope`() = runTest {
        // For SurrealDB query results we typically get an array of statement
        // result envelopes. Here we test that an arbitrary user shape decodes.
        @Serializable
        data class StatementResult<T>(val status: String, val result: T)
        val stub = """{"id":"1","result":[{"status":"OK","result":[{"id":"person:1","name":"Ada","age":30}]}]}"""
        val results: List<StatementResult<List<Person>>> = client(stub).queryAs("SELECT * FROM person")
        assertEquals(1, results.size)
        assertEquals("OK", results[0].status)
        assertEquals("Ada", results[0].result[0].name)
    }
}
