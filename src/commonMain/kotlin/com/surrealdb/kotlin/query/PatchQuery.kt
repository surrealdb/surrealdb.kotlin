package com.surrealdb.kotlin.query

import kotlinx.serialization.json.JsonElement

/**
 * Builder for `UPDATE … PATCH …` queries (JSON-Patch RFC 6902).
 *
 * When [diff] is true, the result is the diff between before/after states
 * (`RETURN DIFF` is appended).
 */
public class PatchQuery internal constructor(
    @PublishedApi internal val dispatcher: QueryDispatcher,
    private val what: Any,
    private val patches: JsonElement,
    private val diff: Boolean,
    private val cond: Expr? = null,
) {
    public fun where(expr: Expr): PatchQuery = PatchQuery(dispatcher, what, patches, diff, expr)

    public fun compile(): BoundQuery {
        val q = BoundQuery()
        q.appendLiteral("UPDATE ONLY ")
        q.appendValue(what)
        q.appendLiteral(" PATCH ")
        q.bind(patches)
        cond?.let { q.appendLiteral(" WHERE "); it.compile(q) }
        if (diff) q.appendLiteral(" RETURN DIFF")
        return q
    }

    public suspend fun await(): JsonElement = firstQueryResult(dispatcher.dispatch(compile()))
    public suspend fun awaitRaw(): JsonElement = dispatcher.dispatch(compile())
}
