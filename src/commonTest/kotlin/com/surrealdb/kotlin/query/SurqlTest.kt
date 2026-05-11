package com.surrealdb.kotlin.query

import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SurqlTest {

    @Test
    fun `surql DSL produces matching string and bindings`() {
        val q = surql {
            +"SELECT * FROM "
            value(Table("person"))
            +" WHERE age > "
            value(18)
        }
        assertTrue(q.surql.startsWith("SELECT * FROM type::table("))
        assertTrue(q.surql.contains(" WHERE age > "))
        // Table name + literal 18 = 2 bindings
        assertEquals(2, q.bindings.size)
    }

    @Test
    fun `surql named parameters survive`() {
        val q = surql("SELECT * FROM person WHERE id = \$id", "id" to "alice")
        assertEquals("SELECT * FROM person WHERE id = \$id", q.surql)
        assertEquals("alice", q.bindings["id"]?.let { (it as JsonPrimitive).content })
    }

    @Test
    fun `bound query bind counter avoids collisions across nested fragments`() {
        val q = BoundQuery()
        q.appendLiteral("a=")
        q.bind(JsonPrimitive(1))
        q.appendLiteral(", b=")
        q.bind(JsonPrimitive(2))
        assertEquals(2, q.bindings.size)
        // The two binds should have distinct keys.
        assertEquals(2, q.bindings.keys.size)
    }

    @Test
    fun `expr composition produces nested parens`() {
        val q = BoundQuery()
        ((field("age") gt 18) and (field("active") eq true)).compile(q)
        assertTrue(q.surql.contains(" AND "))
        assertTrue(q.surql.startsWith("(") && q.surql.endsWith(")"))
    }

    @Test
    fun `record id binds table and id parts separately`() {
        val q = BoundQuery()
        q.appendValue(RecordId("user", "alice"))
        assertEquals(2, q.bindings.size)
        assertTrue(q.surql.startsWith("type::record("))
    }

    @Test
    fun `field name validation rejects injection attempts`() {
        try {
            Expr.Field("name; DROP TABLE x")
            error("should have thrown")
        } catch (_: IllegalArgumentException) {
            // expected
        }
    }
}
