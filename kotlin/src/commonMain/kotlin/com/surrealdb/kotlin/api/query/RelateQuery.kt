package com.surrealdb.kotlin.api.query

import kotlinx.serialization.json.JsonElement

/**
 * Builder for `RELATE` queries.
 */
private val IDENTIFIER = Regex("""[A-Za-z_][A-Za-z0-9_]*""")

public class RelateQuery internal constructor(
    @PublishedApi internal val dispatcher: QueryDispatcher,
    private val `in`: Any,
    private val relation: Any,
    private val out: Any,
    private val data: JsonElement? = null,
    private val returnMode: ReturnMode? = null,
) {
    public fun content(data: JsonElement): RelateQuery = copy(data = data)
    public fun returnMode(mode: ReturnMode): RelateQuery = copy(returnMode = mode)

    public fun compile(): BoundQuery {
        // RELATE positions don't accept bare `type::record(...)` function
        // calls, but they do accept parenthesised expressions. The relation
        // slot must be either a literal table identifier (e.g. `likes`) or a
        // specific relation record id — same constraint as RECORD references.
        val q = BoundQuery()
        q.appendLiteral("RELATE ")
        renderRelateOperand(q, `in`)
        q.appendLiteral("->")
        renderRelationSlot(q, relation)
        q.appendLiteral("->")
        renderRelateOperand(q, out)
        data?.let {
            q.appendLiteral(" CONTENT ")
            q.bind(it)
        }
        returnMode?.render(q)
        return q
    }

    private fun renderRelateOperand(q: BoundQuery, operand: Any) {
        when (operand) {
            is RecordId -> {
                q.appendLiteral("(")
                q.appendValue(operand)
                q.appendLiteral(")")
            }
            else -> q.appendValue(operand)
        }
    }

    private fun renderRelationSlot(q: BoundQuery, slot: Any) {
        when (slot) {
            is Table -> {
                require(IDENTIFIER.matches(slot.name)) {
                    "Relation table must be an identifier (got '${slot.name}')"
                }
                q.appendLiteral(slot.name)
            }
            is RecordId -> renderRelateOperand(q, slot)
            else -> q.appendValue(slot)
        }
    }

    public suspend fun await(): JsonElement = firstQueryResult(dispatcher.dispatch(compile()))
    public suspend fun awaitRaw(): JsonElement = dispatcher.dispatch(compile())

    private fun copy(
        data: JsonElement? = this.data,
        returnMode: ReturnMode? = this.returnMode,
    ): RelateQuery = RelateQuery(dispatcher, `in`, relation, out, data, returnMode)
}
