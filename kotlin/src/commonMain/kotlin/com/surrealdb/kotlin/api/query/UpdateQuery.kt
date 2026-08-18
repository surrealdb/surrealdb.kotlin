package com.surrealdb.kotlin.api.query

import kotlinx.serialization.json.JsonElement

/**
 * Builder for `UPDATE` queries (replacement-style).
 *
 * For merge / patch behaviour use [MergeQuery] / [PatchQuery] which compile to
 * `UPDATE ... MERGE` / `UPDATE ... PATCH` respectively.
 */
public class UpdateQuery internal constructor(
    @PublishedApi internal val dispatcher: QueryDispatcher,
    private val what: Any,
    private val data: JsonElement? = null,
    private val cond: Expr? = null,
    private val returnMode: ReturnMode? = null,
) {
    public fun content(data: JsonElement): UpdateQuery = copy(data = data)
    public fun where(expr: Expr): UpdateQuery = copy(cond = expr)
    public fun returnMode(mode: ReturnMode): UpdateQuery = copy(returnMode = mode)

    public fun compile(): BoundQuery {
        val q = BoundQuery()
        q.appendLiteral("UPDATE ONLY ")
        q.appendValue(what)
        data?.let {
            q.appendLiteral(" CONTENT ")
            q.bind(it)
        }
        cond?.let { q.appendLiteral(" WHERE "); it.compile(q) }
        returnMode?.render(q)
        return q
    }

    public suspend fun await(): JsonElement = firstQueryResult(dispatcher.dispatch(compile()))
    public suspend fun awaitRaw(): JsonElement = dispatcher.dispatch(compile())

    private fun copy(
        data: JsonElement? = this.data,
        cond: Expr? = this.cond,
        returnMode: ReturnMode? = this.returnMode,
    ): UpdateQuery = UpdateQuery(dispatcher, what, data, cond, returnMode)
}
