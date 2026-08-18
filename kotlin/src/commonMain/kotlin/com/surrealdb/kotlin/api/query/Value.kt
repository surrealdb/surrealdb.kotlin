package com.surrealdb.kotlin.api.query

/**
 * A SurrealDB table reference (e.g. `user`).
 *
 * Used as a query target — the builder will emit it via `type::table($_n)` with
 * the name bound as a parameter, never inlined into the SurrealQL string, so
 * user-supplied table names cannot be used for injection.
 */
public data class Table(public val name: String) {
    override fun toString(): String = name
}

/**
 * A SurrealDB record id (e.g. `user:alice`).
 *
 * Both `table` and `id` are surfaced verbatim; the builder emits the pair via
 * `type::record($_tb, $_id)` (SurrealDB v3) with both halves bound.
 */
public data class RecordId(public val table: String, public val id: String) {
    override fun toString(): String = "$table:$id"
}

/**
 * A range of record ids on a single table (e.g. `user:alice..user:zara`).
 */
public data class RecordIdRange(
    public val table: String,
    public val start: String? = null,
    public val end: String? = null,
    public val includeEnd: Boolean = false,
)
