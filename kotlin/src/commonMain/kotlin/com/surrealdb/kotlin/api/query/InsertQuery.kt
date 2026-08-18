package com.surrealdb.kotlin.api.query

import kotlinx.serialization.json.JsonElement

/**
 * Builder for `INSERT INTO <table> $data` queries.
 */
public class InsertQuery internal constructor(
    @PublishedApi internal val dispatcher: QueryDispatcher,
    private val into: Table,
    private val data: JsonElement,
) {
    public fun compile(): BoundQuery {
        // INSERT INTO requires a table reference; `type::table($tb)` doesn't
        // parse here, but a bound table name does. Bindings still keep user
        // input out of the SurrealQL string.
        val q = BoundQuery()
        q.appendLiteral("INSERT INTO ")
        q.bind(kotlinx.serialization.json.JsonPrimitive(into.name))
        q.appendLiteral(" ")
        q.bind(data)
        return q
    }

    public suspend fun await(): JsonElement = firstQueryResult(dispatcher.dispatch(compile()))
    public suspend fun awaitRaw(): JsonElement = dispatcher.dispatch(compile())
}

/**
 * Builder for `INSERT RELATION INTO <table> $data` queries.
 */
public class InsertRelationQuery internal constructor(
    @PublishedApi internal val dispatcher: QueryDispatcher,
    private val into: Table,
    private val data: JsonElement,
) {
    public fun compile(): BoundQuery {
        val q = BoundQuery()
        q.appendLiteral("INSERT RELATION INTO ")
        q.bind(kotlinx.serialization.json.JsonPrimitive(into.name))
        q.appendLiteral(" ")
        q.bind(data)
        return q
    }

    public suspend fun await(): JsonElement = firstQueryResult(dispatcher.dispatch(compile()))
    public suspend fun awaitRaw(): JsonElement = dispatcher.dispatch(compile())
}
