package com.surrealdb.kotlin.query

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * Builder for `CREATE` queries.
 */
public class CreateQuery internal constructor(
    @PublishedApi internal val dispatcher: QueryDispatcher,
    private val what: Any,
    private val data: JsonElement? = null,
    private val returnMode: ReturnMode? = null,
) {
    public fun content(data: JsonElement): CreateQuery = copy(data = data)
    public fun returnMode(mode: ReturnMode): CreateQuery = copy(returnMode = mode)

    public fun compile(): BoundQuery {
        val q = BoundQuery()
        q.appendLiteral("CREATE ONLY ")
        q.appendValue(what)
        data?.let {
            q.appendLiteral(" CONTENT ")
            q.bind(it)
        }
        returnMode?.render(q)
        return q
    }

    public suspend fun await(): JsonElement = firstQueryResult(dispatcher.dispatch(compile()))
    public suspend fun awaitRaw(): JsonElement = dispatcher.dispatch(compile())

    private fun copy(
        data: JsonElement? = this.data,
        returnMode: ReturnMode? = this.returnMode,
    ): CreateQuery = CreateQuery(dispatcher, what, data, returnMode)
}
