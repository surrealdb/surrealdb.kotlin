package com.surrealdb.kotlin.api

import com.surrealdb.kotlin.api.query.BoundQuery
import com.surrealdb.kotlin.api.query.CreateQuery
import com.surrealdb.kotlin.api.query.DeleteQuery
import com.surrealdb.kotlin.api.query.InsertQuery
import com.surrealdb.kotlin.api.query.InsertRelationQuery
import com.surrealdb.kotlin.api.query.MergeQuery
import com.surrealdb.kotlin.api.query.PatchQuery
import com.surrealdb.kotlin.api.query.QueryDispatcher
import com.surrealdb.kotlin.api.query.QueryableImpl
import com.surrealdb.kotlin.api.query.RelateQuery
import com.surrealdb.kotlin.api.query.RunQuery
import com.surrealdb.kotlin.api.query.SelectQuery
import com.surrealdb.kotlin.api.query.SurrealQueryable
import com.surrealdb.kotlin.api.query.Table
import com.surrealdb.kotlin.api.query.UpdateQuery
import com.surrealdb.kotlin.api.query.UpsertQuery
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * A client-side SurrealDB transaction.
 *
 * Created by [SurrealSession.beginTransaction] (or the [transaction] block
 * extension). Every queryable method (CRUD, raw `query`, etc.) is automatically
 * scoped to this transaction — the SDK passes the transaction id in the
 * JSON-RPC envelope's `txn` field, and the server applies the statement inside
 * the transaction without an explicit `BEGIN` round-trip.
 *
 * Either call [commit] or [cancel] before discarding — leaving a transaction
 * open will keep server resources allocated until it times out.
 */
public class SurrealTransaction internal constructor(
    private val session: SurrealSession,
    public val txnId: String,
) : SurrealQueryable {

    private val txnDispatcher = object : QueryDispatcher {
        override val json get() = session.controller.config.json
        override suspend fun dispatch(query: BoundQuery): JsonElement {
            val vars = query.bindingsAsJsonObject().takeIf { it.isNotEmpty() }
            return session.controller.query(
                sessionId = session.sessionId,
                sql = query.surql,
                vars = vars,
                txn = txnId,
            )
        }
    }
    private val queryable = QueryableImpl(txnDispatcher)

    override suspend fun query(bound: BoundQuery): JsonElement = txnDispatcher.dispatch(bound)

    override suspend fun query(sql: String, vars: JsonObject?): JsonElement {
        val bq = BoundQuery().appendLiteral(sql)
        if (vars != null) for ((k, v) in vars) bq.attachBinding(k, v)
        return txnDispatcher.dispatch(bq)
    }

    override fun select(what: Any): SelectQuery = queryable.select(what)
    override fun create(what: Any): CreateQuery = queryable.create(what)
    override fun upsert(what: Any): UpsertQuery = queryable.upsert(what)
    override fun update(what: Any): UpdateQuery = queryable.update(what)
    override fun merge(what: Any, data: Any): MergeQuery = queryable.merge(what, data)
    override fun patch(what: Any, patches: JsonElement, diff: Boolean): PatchQuery =
        queryable.patch(what, patches, diff)
    override fun delete(what: Any): DeleteQuery = queryable.delete(what)
    override fun relate(`in`: Any, relation: Any, out: Any): RelateQuery =
        queryable.relate(`in`, relation, out)
    override fun insert(into: Table, data: JsonElement): InsertQuery = queryable.insert(into, data)
    override fun insertRelation(into: Table, data: JsonElement): InsertRelationQuery =
        queryable.insertRelation(into, data)
    override fun run(function: String): RunQuery = queryable.run(function)

    /** Commit the transaction. */
    public suspend fun commit() {
        session.controller.commit(session.sessionId, txnId)
    }

    /** Cancel the transaction, discarding all changes. */
    public suspend fun cancel() {
        session.controller.cancel(session.sessionId, txnId)
    }
}

/**
 * Eagerly begin a transaction. The caller owns the lifecycle and must call
 * [SurrealTransaction.commit] or [SurrealTransaction.cancel].
 */
public suspend fun SurrealSession.beginTransaction(): SurrealTransaction =
    SurrealTransaction(this, controller.begin(sessionId))

/**
 * Run [block] inside a transaction. Commits on normal completion; cancels and
 * rethrows if the block throws.
 */
public suspend fun SurrealSession.transaction(
    block: suspend SurrealTransaction.() -> Unit,
) {
    val tx = beginTransaction()
    try {
        tx.block()
        tx.commit()
    } catch (t: Throwable) {
        runCatching { tx.cancel() }
        throw t
    }
}
