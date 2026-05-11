package com.surrealdb.kotlin.query

import kotlinx.serialization.json.JsonElement

/**
 * Builder for SurrealQL function invocations — ports the
 * [run.ts](https://github.com/surrealdb/surrealdb.js/blob/ca8dae20ba439b6b4242ff2822b271a2e5aaaa60/packages/sdk/src/query/run.ts)
 * reference impl.
 *
 * The function name is validated against the same identifier pattern that
 * surrealdb.js uses (`[a-zA-Z0-9_:]+`); the version, if supplied, must be
 * dot-separated digits.
 */
public class RunQuery internal constructor(
    @PublishedApi internal val dispatcher: QueryDispatcher,
    private val name: String,
    private val version: String? = null,
    private val args: List<Any?> = emptyList(),
) {
    init {
        require(NAME_REGEX.matches(name)) { "Invalid function name: '$name'" }
        if (version != null) require(VERSION_REGEX.matches(version)) {
            "Invalid function version: '$version'"
        }
    }

    public fun version(version: String): RunQuery = RunQuery(dispatcher, name, version, args)
    public fun args(vararg args: Any?): RunQuery = RunQuery(dispatcher, name, version, args.toList())

    public fun compile(): BoundQuery {
        val q = BoundQuery()
        q.appendLiteral(name)
        if (version != null) q.appendLiteral("<$version>")
        q.appendLiteral("(")
        args.forEachIndexed { i, arg ->
            if (i > 0) q.appendLiteral(", ")
            q.appendValue(arg)
        }
        q.appendLiteral(")")
        return q
    }

    public suspend fun await(): JsonElement = firstQueryResult(dispatcher.dispatch(compile()))
    public suspend fun awaitRaw(): JsonElement = dispatcher.dispatch(compile())

    private companion object {
        val NAME_REGEX = Regex("""^[a-zA-Z0-9_:]+$""")
        val VERSION_REGEX = Regex("""^[0-9.]+$""")
    }
}
