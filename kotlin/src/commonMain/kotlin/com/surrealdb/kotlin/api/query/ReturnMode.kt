package com.surrealdb.kotlin.api.query

/**
 * How a write statement's `RETURN` clause should behave.
 *
 * Maps onto the SurrealQL `RETURN NONE | BEFORE | AFTER | DIFF | <fields>` form.
 * For `Fields`, identifiers are validated against the same pattern as
 * [Expr.Field] — anything more exotic should be expressed as a raw `query()`.
 */
public sealed class ReturnMode {
    public data object None : ReturnMode()
    public data object Before : ReturnMode()
    public data object After : ReturnMode()
    public data object Diff : ReturnMode()
    public data class Fields(val names: List<String>) : ReturnMode() {
        init {
            require(names.isNotEmpty()) { "ReturnMode.Fields requires at least one field" }
            names.forEach { Expr.Field(it) }
        }
    }

    internal fun render(into: BoundQuery) {
        into.appendLiteral(" RETURN ")
        when (this) {
            None -> into.appendLiteral("NONE")
            Before -> into.appendLiteral("BEFORE")
            After -> into.appendLiteral("AFTER")
            Diff -> into.appendLiteral("DIFF")
            is Fields -> into.appendLiteral(names.joinToString(separator = ", "))
        }
    }
}
