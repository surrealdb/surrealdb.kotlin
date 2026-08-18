package com.surrealdb.kotlin.query

import kotlinx.serialization.json.JsonElement

/**
 * Builder for `UPDATE … MERGE …` queries.
 */
public class MergeQuery internal constructor(
    @PublishedApi internal val dispatcher: QueryDispatcher,
    private val what: Any,
    private val data: Any,
    private val cond: Expr? = null,
    private val returnMode: ReturnMode? = null,
) {
    public fun where(expr: Expr): MergeQuery = copy(cond = expr)
    public fun returnMode(mode: ReturnMode): MergeQuery = copy(returnMode = mode)

    public fun compile(): BoundQuery {
        val q = BoundQuery()
        q.appendLiteral("UPDATE ONLY ")
        q.appendValue(what)
        q.appendLiteral(" MERGE ")
        if (data is JsonElement) q.bind(data) else q.appendValue(data)
        cond?.let { q.appendLiteral(" WHERE "); it.compile(q) }
        returnMode?.render(q)
        return q
    }

    public suspend fun await(): JsonElement = firstQueryResult(dispatcher.dispatch(compile()))
    public suspend fun awaitRaw(): JsonElement = dispatcher.dispatch(compile())

    private fun copy(
        cond: Expr? = this.cond,
        returnMode: ReturnMode? = this.returnMode,
    ): MergeQuery = MergeQuery(dispatcher, what, data, cond, returnMode)
}
