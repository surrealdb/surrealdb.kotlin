package com.surrealdb.kotlin

import kotlinx.serialization.json.JsonObject

public class SurrealTransaction internal constructor(private val session: SurrealSession) {
    public suspend fun query(sql: String, vars: JsonObject? = null): kotlinx.serialization.json.JsonElement =
        session.query(sql, vars)

    public suspend fun queryResult(sql: String, vars: JsonObject? = null): Result<kotlinx.serialization.json.JsonElement> =
        session.queryResult(sql, vars)
}

public suspend fun SurrealSession.transaction(block: suspend SurrealTransaction.() -> Unit) {
    query("BEGIN TRANSACTION")
    try {
        SurrealTransaction(this).block()
        query("COMMIT TRANSACTION")
    } catch (e: Throwable) {
        runCatching { query("CANCEL TRANSACTION") }
        throw e
    }
}
