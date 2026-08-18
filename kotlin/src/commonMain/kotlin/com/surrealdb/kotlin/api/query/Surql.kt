package com.surrealdb.kotlin.api.query

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/**
 * Convert an arbitrary value into a [JsonElement] for binding. Accepts the
 * SDK's typed value wrappers ([Table], [RecordId], [RecordIdRange]) plus the
 * normal Kotlin types. Throws for anything we can't represent on the wire.
 */
public fun toJson(value: Any?): JsonElement = when (value) {
    null -> kotlinx.serialization.json.JsonNull
    is JsonElement -> value
    is String -> JsonPrimitive(value)
    is Boolean -> JsonPrimitive(value)
    is Number -> JsonPrimitive(value)
    is Table -> buildJsonObject { put("\$type", JsonPrimitive("table")); put("name", JsonPrimitive(value.name)) }
    is RecordId -> buildJsonObject {
        put("\$type", JsonPrimitive("record"))
        put("tb", JsonPrimitive(value.table))
        put("id", JsonPrimitive(value.id))
    }
    is RecordIdRange -> buildJsonObject {
        put("\$type", JsonPrimitive("recordrange"))
        put("tb", JsonPrimitive(value.table))
        value.start?.let { put("start", JsonPrimitive(it)) }
        value.end?.let { put("end", JsonPrimitive(it)) }
        put("includeEnd", JsonPrimitive(value.includeEnd))
    }
    else -> throw IllegalArgumentException(
        "Cannot bind value of type ${value::class.simpleName}: $value — pass a JsonElement, Table, RecordId or a primitive."
    )
}

/**
 * Append [value] to the query, choosing the right SurrealQL expression based
 * on the value's type. Strings/numbers/booleans become bound parameters;
 * [Table], [RecordId], and [RecordIdRange] expand into the SurrealQL type
 * constructors with their fields bound separately.
 */
private val IDENT = Regex("""[A-Za-z_][A-Za-z0-9_]*""")

internal fun BoundQuery.appendValue(value: Any?): BoundQuery = apply {
    when (value) {
        is Table -> {
            appendLiteral("type::table(")
            bind(JsonPrimitive(value.name))
            appendLiteral(")")
        }
        is RecordId -> {
            // SurrealDB v3 calls this `type::record`; the older `type::thing`
            // is gone. We bind both halves so callers can't inject.
            appendLiteral("type::record(")
            bind(JsonPrimitive(value.table))
            appendLiteral(", ")
            bind(JsonPrimitive(value.id))
            appendLiteral(")")
        }
        is RecordIdRange -> {
            // Range literals in v3 are `tb:start..end` — there's no
            // type::range constructor that accepts table+start+end. Inline
            // the table after validating it as an identifier.
            require(IDENT.matches(value.table)) {
                "RecordIdRange.table must be a plain identifier (got '${value.table}')"
            }
            appendLiteral(value.table)
            appendLiteral(":")
            if (value.start != null) bind(JsonPrimitive(value.start)) else appendLiteral("..")
            if (value.start != null) appendLiteral(if (value.includeEnd) "..=" else "..")
            if (value.end != null) bind(JsonPrimitive(value.end))
        }
        else -> bind(toJson(value))
    }
}

/**
 * DSL receiver for assembling a [BoundQuery]:
 * ```
 * val q = surql {
 *     +"SELECT * FROM "
 *     value(Table("user"))
 *     +" WHERE age > "; value(18)
 * }
 * ```
 */
public class SurqlBuilder internal constructor() {
    private val query = BoundQuery()

    /** Append a raw SurrealQL fragment. */
    public operator fun String.unaryPlus() {
        query.appendLiteral(this)
    }

    /** Append a raw SurrealQL fragment (no escaping). */
    public fun literal(text: String): SurqlBuilder = apply { query.appendLiteral(text) }

    /** Append a value, automatically binding non-literal types. */
    public fun value(value: Any?): SurqlBuilder = apply { query.appendValue(value) }

    /** Bind [value] under [name] and emit `$<name>`. */
    public fun param(name: String, value: Any?): SurqlBuilder = apply {
        query.bindNamed(name, toJson(value))
    }

    /** Splice another [BoundQuery] fragment, merging bindings. */
    public fun fragment(fragment: BoundQuery): SurqlBuilder = apply { query.append(fragment) }

    internal fun build(): BoundQuery = query
}

/** Build a [BoundQuery] using the DSL. */
public fun surql(block: SurqlBuilder.() -> Unit): BoundQuery =
    SurqlBuilder().apply(block).build()

/**
 * Build a [BoundQuery] from a raw SurrealQL string and a set of pre-named
 * bindings. The string is sent as-is; callers must ensure `$name` placeholders
 * are present for each binding.
 */
public fun surql(sql: String, vararg bindings: Pair<String, Any?>): BoundQuery {
    val builder = BoundQuery().appendLiteral(sql)
    for ((name, value) in bindings) builder.attachBinding(name, toJson(value))
    return builder
}
