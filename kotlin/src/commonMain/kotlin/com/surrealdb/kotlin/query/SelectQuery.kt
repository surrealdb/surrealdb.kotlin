package com.surrealdb.kotlin.query

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

/**
 * Builder for `SELECT` queries. Ports the surface of surrealdb.js's
 * [`SelectPromise`](https://github.com/surrealdb/surrealdb.js/blob/ca8dae20ba439b6b4242ff2822b271a2e5aaaa60/packages/sdk/src/query/select.ts).
 *
 * The terminal call is [await] / [awaitFirst] / [awaitAs]. Each chain method
 * returns a fresh instance so builders are safe to share or pin to a variable.
 */
public class SelectQuery internal constructor(
    @PublishedApi internal val dispatcher: QueryDispatcher,
    private val what: Any,
    private val selection: Selection = Selection.All,
    private val fields: List<String> = emptyList(),
    private val start: Int? = null,
    private val limit: Int? = null,
    private val cond: Expr? = null,
    private val fetchFields: List<String> = emptyList(),
    private val timeoutSeconds: Double? = null,
    private val versionAt: String? = null,
) {
    internal enum class Selection { All, Fields, Value }

    /** Select only the named fields. */
    public fun fields(vararg fields: String): SelectQuery {
        fields.forEach { Expr.Field(it) }
        return copy(selection = Selection.Fields, fields = fields.toList())
    }

    /** Project a single field as VALUE. */
    public fun value(field: String): SelectQuery {
        Expr.Field(field)
        return copy(selection = Selection.Value, fields = listOf(field))
    }

    public fun start(start: Int): SelectQuery = copy(start = start)
    public fun limit(limit: Int): SelectQuery = copy(limit = limit)
    public fun where(expr: Expr): SelectQuery = copy(cond = expr)
    public fun fetch(vararg fields: String): SelectQuery {
        fields.forEach { Expr.Field(it) }
        return copy(fetchFields = fields.toList())
    }
    public fun timeout(seconds: Double): SelectQuery = copy(timeoutSeconds = seconds)
    /** Version cutoff as a SurrealQL datetime literal (e.g. `d'2024-01-01T00:00:00Z'`). */
    public fun version(literal: String): SelectQuery = copy(versionAt = literal)

    /** Compile this query without dispatching it. */
    public fun compile(): BoundQuery {
        val q = BoundQuery()
        q.appendLiteral("SELECT")
        when (selection) {
            Selection.All -> q.appendLiteral(" *")
            Selection.Fields -> q.appendLiteral(" " + fields.joinToString(", "))
            Selection.Value -> q.appendLiteral(" VALUE " + fields.first())
        }
        q.appendLiteral(" FROM ONLY ")
        q.appendValue(what)
        cond?.let { q.appendLiteral(" WHERE "); it.compile(q) }
        start?.let { q.appendLiteral(" START "); q.bind(JsonPrimitive(it)) }
        limit?.let { q.appendLiteral(" LIMIT "); q.bind(JsonPrimitive(it)) }
        if (fetchFields.isNotEmpty()) q.appendLiteral(" FETCH " + fetchFields.joinToString(", "))
        timeoutSeconds?.let { q.appendLiteral(" TIMEOUT ${it}s") }
        versionAt?.let { q.appendLiteral(" VERSION $it") }
        return q
    }

    /** Dispatch the query and return the unwrapped first-statement result. */
    public suspend fun await(): JsonElement = firstQueryResult(dispatcher.dispatch(compile()))

    /** Dispatch and return the raw `[{ status, result, time, type }]` envelope. */
    public suspend fun awaitRaw(): JsonElement = dispatcher.dispatch(compile())

    private fun copy(
        selection: Selection = this.selection,
        fields: List<String> = this.fields,
        start: Int? = this.start,
        limit: Int? = this.limit,
        cond: Expr? = this.cond,
        fetchFields: List<String> = this.fetchFields,
        timeoutSeconds: Double? = this.timeoutSeconds,
        versionAt: String? = this.versionAt,
    ): SelectQuery = SelectQuery(
        dispatcher, what, selection, fields, start, limit, cond, fetchFields, timeoutSeconds, versionAt,
    )
}
