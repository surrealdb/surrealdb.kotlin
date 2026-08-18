package com.surrealdb.kotlin.runtime.engine

import com.surrealdb.kotlin.api.live.LiveQuerySubscription
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * The communication contract between the SDK and a SurrealDB datastore. Mirrors
 * the protocol defined at https://github.com/surrealdb/surrealdb-protocol — the
 * concept of raw method-keyed RPCs is intentionally absent here so non
 * JSON-RPC engines (e.g. a future gRPC engine) can implement the same surface.
 *
 * Higher-level consumers (`ConnectionController`, `SurrealSession`,
 * `SurrealTransaction`) communicate to engines through these functions.
 * `RpcEngine` translates them into JSON-RPC payloads.
 */
internal interface SurrealProtocol {
    // Connection operations
    suspend fun health(session: SessionSnapshot): JsonElement
    suspend fun version(session: SessionSnapshot): JsonElement

    // Session operations
    suspend fun use(namespace: String, database: String, session: SessionSnapshot): JsonElement
    suspend fun signup(params: JsonObject, session: SessionSnapshot): JsonElement
    suspend fun signin(params: JsonObject, session: SessionSnapshot): JsonElement
    suspend fun authenticate(token: String, session: SessionSnapshot): JsonElement
    suspend fun invalidate(session: SessionSnapshot): JsonElement
    suspend fun reset(session: SessionSnapshot): JsonElement
    suspend fun set(name: String, value: JsonElement, session: SessionSnapshot): JsonElement
    suspend fun unset(name: String, session: SessionSnapshot): JsonElement

    // Transaction operations
    suspend fun begin(session: SessionSnapshot): String
    suspend fun commit(txnId: String, session: SessionSnapshot)
    suspend fun cancel(txnId: String, session: SessionSnapshot)

    // Query operations
    suspend fun query(
        sql: String,
        vars: JsonObject?,
        session: SessionSnapshot,
        txn: String?,
    ): JsonElement

    // Live query operations
    suspend fun liveQuery(
        table: String,
        diff: Boolean?,
        session: SessionSnapshot,
    ): LiveQuerySubscription

    suspend fun kill(liveQueryId: String, session: SessionSnapshot): JsonElement
}
