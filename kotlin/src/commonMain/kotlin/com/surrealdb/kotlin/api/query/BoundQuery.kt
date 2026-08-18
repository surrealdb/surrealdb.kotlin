package com.surrealdb.kotlin.api.query

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * A SurrealQL string paired with its bindings. Produced by the query builder
 * DSL and consumed by [com.surrealdb.kotlin.api.SurrealSession.query]. The string
 * never contains user-supplied values directly — those are always bound to
 * `$_n` parameters so the query is safe to send as-is.
 *
 * Bindings are immutable; use [append] to compose larger queries.
 */
public class BoundQuery internal constructor(
    private val parts: MutableList<String>,
    private val binds: MutableMap<String, JsonElement>,
) {
    public constructor() : this(mutableListOf(), mutableMapOf())

    public constructor(literal: String) : this(mutableListOf(literal), mutableMapOf())

    /** The compiled SurrealQL string. */
    public val surql: String get() = parts.joinToString(separator = "")

    /** Bindings, keyed by `$<name>` (without the dollar). */
    public val bindings: Map<String, JsonElement> get() = binds.toMap()

    /** Append a raw SurrealQL fragment. The literal is not escaped — only
     *  trusted strings (keywords, operators, joiners) should use this path. */
    public fun appendLiteral(literal: String): BoundQuery = apply {
        parts.add(literal)
    }

    /** Append another fragment, merging its bindings. */
    public fun append(other: BoundQuery): BoundQuery = apply {
        parts.add(other.surql)
        binds.putAll(other.binds)
    }

    /** Bind [value] to a freshly-generated parameter and emit `$<param>`. */
    public fun bind(value: JsonElement): BoundQuery = apply {
        val key = nextParam()
        binds[key] = value
        parts.add("\$$key")
    }

    /** Bind [value] to [name] (caller-controlled), emit `$<name>`. */
    public fun bindNamed(name: String, value: JsonElement): BoundQuery = apply {
        binds[name] = value
        parts.add("\$$name")
    }

    /** Register a binding by name without emitting a placeholder — used when the
     *  caller has already embedded `$<name>` in the literal SurrealQL. */
    public fun attachBinding(name: String, value: JsonElement): BoundQuery = apply {
        binds[name] = value
    }

    /** Bindings as a [JsonObject] for the `query` RPC's vars parameter. */
    public fun bindingsAsJsonObject(): JsonObject = JsonObject(binds)

    private var counter = 0
    private fun nextParam(): String {
        while (true) {
            val candidate = "_${counter++}"
            if (candidate !in binds) return candidate
        }
    }

    override fun toString(): String =
        if (binds.isEmpty()) surql else "$surql  --  bindings=$binds"
}
