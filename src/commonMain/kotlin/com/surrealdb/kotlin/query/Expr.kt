package com.surrealdb.kotlin.query

import kotlinx.serialization.json.JsonPrimitive

/**
 * A SurrealQL expression usable in `WHERE` / `RETURN` etc. clauses.
 *
 * Compose with infix operators. Every leaf either references a field by name
 * (interpolated as a literal, but constrained to a strict identifier pattern)
 * or binds a value as a parameter, so user values never reach the SurrealQL
 * string directly.
 */
public sealed class Expr {
    public abstract fun compile(into: BoundQuery)

    /** A bare field reference (column / record property). */
    public data class Field(val name: String) : Expr() {
        init {
            require(IDENTIFIER.matches(name)) {
                "Field name must match $IDENTIFIER (got '$name')"
            }
        }

        override fun compile(into: BoundQuery) {
            into.appendLiteral(name)
        }
    }

    /** A bound value reference. */
    public data class Value(val value: Any?) : Expr() {
        override fun compile(into: BoundQuery) {
            into.appendValue(value)
        }
    }

    /** A pre-compiled SurrealQL fragment. */
    public data class Raw(val fragment: BoundQuery) : Expr() {
        override fun compile(into: BoundQuery) {
            into.append(fragment)
        }
    }

    /** Binary infix operator (`=`, `!=`, `<`, `<=`, `>`, `>=`, `IN`, `CONTAINS`). */
    public data class Binary(val op: String, val left: Expr, val right: Expr) : Expr() {
        override fun compile(into: BoundQuery) {
            into.appendLiteral("(")
            left.compile(into)
            into.appendLiteral(" $op ")
            right.compile(into)
            into.appendLiteral(")")
        }
    }

    /** Logical AND. */
    public data class And(val left: Expr, val right: Expr) : Expr() {
        override fun compile(into: BoundQuery) {
            into.appendLiteral("(")
            left.compile(into); into.appendLiteral(" AND "); right.compile(into)
            into.appendLiteral(")")
        }
    }

    /** Logical OR. */
    public data class Or(val left: Expr, val right: Expr) : Expr() {
        override fun compile(into: BoundQuery) {
            into.appendLiteral("(")
            left.compile(into); into.appendLiteral(" OR "); right.compile(into)
            into.appendLiteral(")")
        }
    }

    /** Logical NOT. */
    public data class Not(val inner: Expr) : Expr() {
        override fun compile(into: BoundQuery) {
            into.appendLiteral("!(")
            inner.compile(into)
            into.appendLiteral(")")
        }
    }

    public companion object {
        // SurrealQL allows dotted paths and array indexing; we accept the common
        // safe subset: dotted identifiers with optional `[N]` indexing. Anything
        // outside this needs to go through `Raw(surql { ... })`.
        private val IDENTIFIER = Regex("""[A-Za-z_][A-Za-z0-9_]*(?:\.[A-Za-z_][A-Za-z0-9_]*|\[[0-9]+])*""")
    }
}

/** Reference a field by name. */
public fun field(name: String): Expr.Field = Expr.Field(name)

/** Wrap a Kotlin value as a bound expression. */
public fun value(value: Any?): Expr.Value = Expr.Value(value)

/** Wrap a raw SurrealQL fragment as an expression. */
public fun raw(fragment: BoundQuery): Expr.Raw = Expr.Raw(fragment)

// ── Infix operators ──────────────────────────────────────────────────────────

public infix fun Expr.eq(other: Any?): Expr = Expr.Binary("=", this, asExpr(other))
public infix fun Expr.neq(other: Any?): Expr = Expr.Binary("!=", this, asExpr(other))
public infix fun Expr.lt(other: Any?): Expr = Expr.Binary("<", this, asExpr(other))
public infix fun Expr.lte(other: Any?): Expr = Expr.Binary("<=", this, asExpr(other))
public infix fun Expr.gt(other: Any?): Expr = Expr.Binary(">", this, asExpr(other))
public infix fun Expr.gte(other: Any?): Expr = Expr.Binary(">=", this, asExpr(other))
public infix fun Expr.contains(other: Any?): Expr = Expr.Binary("CONTAINS", this, asExpr(other))
public infix fun Expr.inside(other: Any?): Expr = Expr.Binary("IN", this, asExpr(other))

public infix fun Expr.and(other: Expr): Expr = Expr.And(this, other)
public infix fun Expr.or(other: Expr): Expr = Expr.Or(this, other)
public fun not(inner: Expr): Expr = Expr.Not(inner)

private fun asExpr(value: Any?): Expr = if (value is Expr) value else Expr.Value(value)

internal fun Expr.toBoundQuery(): BoundQuery {
    val q = BoundQuery()
    compile(q)
    return q
}
