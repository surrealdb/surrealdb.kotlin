package com.surrealdb.kotlin.query

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * Common surface shared by [com.surrealdb.kotlin.SurrealSession] and
 * [com.surrealdb.kotlin.SurrealTransaction]: every CRUD method assembles a
 * SurrealQL query locally and dispatches it via the `query` RPC. When this
 * queryable is a transaction, the query is sent with the transaction id at
 * the RPC envelope level so the server scopes it correctly.
 */
public interface SurrealQueryable {
    /** Dispatch a pre-built [BoundQuery] via the `query` RPC. */
    public suspend fun query(bound: BoundQuery): JsonElement

    /** Dispatch a raw SurrealQL string with optional name-keyed bindings. */
    public suspend fun query(sql: String, vars: JsonObject? = null): JsonElement

    public fun select(what: Any): SelectQuery
    public fun create(what: Any): CreateQuery
    public fun upsert(what: Any): UpsertQuery
    public fun update(what: Any): UpdateQuery
    public fun merge(what: Any, data: Any): MergeQuery
    public fun patch(what: Any, patches: JsonElement, diff: Boolean = false): PatchQuery
    public fun delete(what: Any): DeleteQuery
    public fun relate(`in`: Any, relation: Any, out: Any): RelateQuery
    public fun insert(into: Table, data: JsonElement): InsertQuery
    public fun insertRelation(into: Table, data: JsonElement): InsertRelationQuery
    public fun run(function: String): RunQuery
}

/**
 * Default implementation of [SurrealQueryable] driven by a single
 * [QueryDispatcher] that knows how to send a [BoundQuery] (with optional txn).
 *
 * Both [com.surrealdb.kotlin.SurrealSession] and
 * [com.surrealdb.kotlin.SurrealTransaction] delegate to this via Kotlin
 * delegation so the CRUD surface lives in exactly one place.
 */
internal class QueryableImpl(
    internal val dispatcher: QueryDispatcher,
) : SurrealQueryable {
    override suspend fun query(bound: BoundQuery): JsonElement = dispatcher.dispatch(bound)

    override suspend fun query(sql: String, vars: JsonObject?): JsonElement {
        val bq = BoundQuery().appendLiteral(sql)
        if (vars != null) for ((k, v) in vars) bq.attachBinding(k, v)
        return dispatcher.dispatch(bq)
    }

    override fun select(what: Any): SelectQuery = SelectQuery(dispatcher, what)
    override fun create(what: Any): CreateQuery = CreateQuery(dispatcher, what)
    override fun upsert(what: Any): UpsertQuery = UpsertQuery(dispatcher, what)
    override fun update(what: Any): UpdateQuery = UpdateQuery(dispatcher, what)
    override fun merge(what: Any, data: Any): MergeQuery = MergeQuery(dispatcher, what, data)
    override fun patch(what: Any, patches: JsonElement, diff: Boolean): PatchQuery =
        PatchQuery(dispatcher, what, patches, diff)
    override fun delete(what: Any): DeleteQuery = DeleteQuery(dispatcher, what)
    override fun relate(`in`: Any, relation: Any, out: Any): RelateQuery =
        RelateQuery(dispatcher, `in`, relation, out)
    override fun insert(into: Table, data: JsonElement): InsertQuery = InsertQuery(dispatcher, into, data)
    override fun insertRelation(into: Table, data: JsonElement): InsertRelationQuery =
        InsertRelationQuery(dispatcher, into, data)
    override fun run(function: String): RunQuery = RunQuery(dispatcher, function)
}

/**
 * Sends a compiled [BoundQuery] over the wire. Each builder carries a
 * dispatcher instance so the same builder type works whether it was created
 * from a session or from inside a transaction.
 *
 * Exposed at `@PublishedApi internal` so inline `awaitAs<T>` extensions on
 * builders can reach the [Json] without making the interface itself public API.
 */
@PublishedApi
internal interface QueryDispatcher {
    public val json: Json
    public suspend fun dispatch(query: BoundQuery): JsonElement
}
