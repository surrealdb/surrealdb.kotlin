package com.surrealdb.kotlin.api.query

import kotlinx.serialization.json.JsonElement

/**
 * Builder for `DELETE` queries.
 */
public class DeleteQuery internal constructor(
    @PublishedApi internal val dispatcher: QueryDispatcher,
    private val what: Any,
    private val cond: Expr? = null,
    private val returnMode: ReturnMode? = null,
) {
    public fun where(expr: Expr): DeleteQuery = copy(cond = expr)
    public fun returnMode(mode: ReturnMode): DeleteQuery = copy(returnMode = mode)

    public fun compile(): BoundQuery {
        val q = BoundQuery()
        q.appendLiteral("DELETE ONLY ")
        q.appendValue(what)
        cond?.let { q.appendLiteral(" WHERE "); it.compile(q) }
        returnMode?.render(q)
        return q
    }

    public suspend fun await(): JsonElement = firstQueryResult(dispatcher.dispatch(compile()))
    public suspend fun awaitRaw(): JsonElement = dispatcher.dispatch(compile())

    private fun copy(
        cond: Expr? = this.cond,
        returnMode: ReturnMode? = this.returnMode,
    ): DeleteQuery = DeleteQuery(dispatcher, what, cond, returnMode)
}
