package com.surrealdb.kotlin.query

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Snapshot tests for each builder's compiled [BoundQuery]. No server is needed
 * — every test asserts on the SurrealQL + bindings produced by `compile()`.
 *
 * These tests are the contract for the query-construction layer: any change in
 * the emitted SurrealQL is a change in the wire protocol's behaviour and
 * should be intentional.
 */
class QueryBuilderTest {

    private val dispatcher = object : QueryDispatcher {
        override val json: Json = Json
        override suspend fun dispatch(query: BoundQuery): JsonElement =
            error("not used in compile-only tests")
    }

    private fun bindings(q: BoundQuery): Map<String, JsonElement> = q.bindings

    // ── select ──

    @Test
    fun `select from table emits SELECT FROM ONLY with bound table name`() {
        val q = SelectQuery(dispatcher, Table("person")).compile()
        assertTrue(q.surql.startsWith("SELECT * FROM ONLY type::table("))
        assertEquals(1, q.bindings.size)
        assertEquals("person", q.bindings.values.first().toString().trim('"'))
    }

    @Test
    fun `select from record id binds table and id separately`() {
        val q = SelectQuery(dispatcher, RecordId("person", "alice")).compile()
        assertTrue(q.surql.startsWith("SELECT * FROM ONLY type::record("))
        assertEquals(2, q.bindings.size)
    }

    @Test
    fun `select fields emits comma-separated field list`() {
        val q = SelectQuery(dispatcher, Table("person")).fields("id", "name", "age").compile()
        assertTrue(q.surql.startsWith("SELECT id, name, age FROM ONLY"))
    }

    @Test
    fun `select value emits VALUE clause`() {
        val q = SelectQuery(dispatcher, Table("person")).value("name").compile()
        assertTrue(q.surql.startsWith("SELECT VALUE name FROM ONLY"))
    }

    @Test
    fun `select where compiles expression with bound value`() {
        val q = SelectQuery(dispatcher, Table("person"))
            .where(field("age") gt 18)
            .compile()
        assertTrue(q.surql.contains(" WHERE (age > "))
    }

    @Test
    fun `select limit and start bind values`() {
        val q = SelectQuery(dispatcher, Table("person"))
            .start(10)
            .limit(5)
            .compile()
        assertTrue(q.surql.contains(" START "))
        assertTrue(q.surql.contains(" LIMIT "))
    }

    @Test
    fun `select fetch emits comma-separated field list`() {
        val q = SelectQuery(dispatcher, Table("post"))
            .fetch("author", "comments")
            .compile()
        assertTrue(q.surql.endsWith(" FETCH author, comments"))
    }

    @Test
    fun `select rejects invalid field identifier`() {
        try {
            SelectQuery(dispatcher, Table("person")).fields("name; DROP TABLE x; --")
            error("should have thrown")
        } catch (_: IllegalArgumentException) {
            // expected
        }
    }

    // ── create / update / upsert ──

    @Test
    fun `create content binds the data object`() {
        val q = CreateQuery(dispatcher, RecordId("person", "1"))
            .content(buildJsonObject { put("name", JsonPrimitive("Ada")) })
            .compile()
        assertTrue(q.surql.startsWith("CREATE ONLY type::record("))
        assertTrue(q.surql.contains(" CONTENT "))
    }

    @Test
    fun `update content compiles to UPDATE ONLY CONTENT`() {
        val q = UpdateQuery(dispatcher, RecordId("person", "1"))
            .content(buildJsonObject { put("name", JsonPrimitive("X")) })
            .compile()
        assertTrue(q.surql.startsWith("UPDATE ONLY type::record("))
        assertTrue(q.surql.contains(" CONTENT "))
    }

    @Test
    fun `upsert with where compiles to UPSERT ONLY then WHERE`() {
        val q = UpsertQuery(dispatcher, Table("person"))
            .content(buildJsonObject { put("name", JsonPrimitive("X")) })
            .where(field("email") eq "x@y.z")
            .compile()
        assertTrue(q.surql.startsWith("UPSERT ONLY type::table("))
        assertTrue(q.surql.contains(" CONTENT "))
        assertTrue(q.surql.contains(" WHERE (email = "))
    }

    @Test
    fun `merge compiles to UPDATE ONLY MERGE`() {
        val q = MergeQuery(dispatcher, RecordId("person", "1"), buildJsonObject { put("active", JsonPrimitive(true)) })
            .compile()
        assertTrue(q.surql.startsWith("UPDATE ONLY type::record("))
        assertTrue(q.surql.contains(" MERGE "))
    }

    @Test
    fun `patch with diff appends RETURN DIFF`() {
        val q = PatchQuery(dispatcher, RecordId("person", "1"), buildJsonObject {}, diff = true).compile()
        assertTrue(q.surql.endsWith(" RETURN DIFF"))
    }

    @Test
    fun `delete with where compiles to DELETE ONLY then WHERE`() {
        val q = DeleteQuery(dispatcher, Table("person"))
            .where(field("active") eq false)
            .compile()
        assertTrue(q.surql.startsWith("DELETE ONLY type::table("))
        assertTrue(q.surql.contains(" WHERE (active = "))
    }

    // ── relate / insert ──

    @Test
    fun `relate compiles to arrow chain`() {
        val q = RelateQuery(
            dispatcher,
            RecordId("person", "a"),
            Table("likes"),
            RecordId("person", "b"),
        ).compile()
        assertTrue(q.surql.contains("->"))
        assertTrue(q.surql.startsWith("RELATE "))
    }

    @Test
    fun `insert compiles to INSERT INTO with bound table and data`() {
        val q = InsertQuery(
            dispatcher,
            Table("person"),
            buildJsonObject { put("name", JsonPrimitive("A")) },
        ).compile()
        assertTrue(q.surql.startsWith("INSERT INTO $"))
    }

    @Test
    fun `insertRelation compiles to INSERT RELATION INTO`() {
        val q = InsertRelationQuery(
            dispatcher,
            Table("likes"),
            buildJsonObject { put("in", JsonPrimitive("p:a")) },
        ).compile()
        assertTrue(q.surql.startsWith("INSERT RELATION INTO $"))
    }

    // ── run ──

    @Test
    fun `run with no args compiles to empty parens`() {
        val q = RunQuery(dispatcher, "fn::greet").compile()
        assertEquals("fn::greet()", q.surql)
    }

    @Test
    fun `run with args binds each one`() {
        val q = RunQuery(dispatcher, "fn::greet").args("alice", 42).compile()
        assertTrue(q.surql.startsWith("fn::greet("))
        assertEquals(2, q.bindings.size)
    }

    @Test
    fun `run rejects invalid function names`() {
        try {
            RunQuery(dispatcher, "fn::; DROP TABLE x")
            error("should have thrown")
        } catch (_: IllegalArgumentException) {
            // expected
        }
    }

    @Test
    fun `run rejects invalid version`() {
        try {
            RunQuery(dispatcher, "fn::ok", version = "not-a-version")
            error("should have thrown")
        } catch (_: IllegalArgumentException) {
            // expected
        }
    }
}
