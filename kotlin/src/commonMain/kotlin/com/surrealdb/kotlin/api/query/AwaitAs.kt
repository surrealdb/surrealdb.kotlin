package com.surrealdb.kotlin.api.query

import kotlinx.serialization.json.decodeFromJsonElement

/**
 * Typed-decode terminal operations for each query builder. Each calls the
 * builder's `await()` (which returns the unwrapped first-statement result)
 * and decodes it via the dispatcher's [kotlinx.serialization.json.Json].
 */

public suspend inline fun <reified T> SelectQuery.awaitAs(): T =
    dispatcher.json.decodeFromJsonElement(await())

public suspend inline fun <reified T> CreateQuery.awaitAs(): T =
    dispatcher.json.decodeFromJsonElement(await())

public suspend inline fun <reified T> UpsertQuery.awaitAs(): T =
    dispatcher.json.decodeFromJsonElement(await())

public suspend inline fun <reified T> UpdateQuery.awaitAs(): T =
    dispatcher.json.decodeFromJsonElement(await())

public suspend inline fun <reified T> MergeQuery.awaitAs(): T =
    dispatcher.json.decodeFromJsonElement(await())

public suspend inline fun <reified T> PatchQuery.awaitAs(): T =
    dispatcher.json.decodeFromJsonElement(await())

public suspend inline fun <reified T> DeleteQuery.awaitAs(): T =
    dispatcher.json.decodeFromJsonElement(await())

public suspend inline fun <reified T> RelateQuery.awaitAs(): T =
    dispatcher.json.decodeFromJsonElement(await())

public suspend inline fun <reified T> InsertQuery.awaitAs(): T =
    dispatcher.json.decodeFromJsonElement(await())

public suspend inline fun <reified T> InsertRelationQuery.awaitAs(): T =
    dispatcher.json.decodeFromJsonElement(await())

public suspend inline fun <reified T> RunQuery.awaitAs(): T =
    dispatcher.json.decodeFromJsonElement(await())
