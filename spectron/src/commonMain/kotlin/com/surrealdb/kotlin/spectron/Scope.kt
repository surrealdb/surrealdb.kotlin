package com.surrealdb.kotlin.spectron

/**
 * Normalise scope inputs to an ordered, de-duplicated list of slash-path
 * strings, e.g. `["team/eng"]`. This builds a single clause: the list of
 * paths that go together inside one AND-clause of a scope set.
 *
 * A map or `(key, value)` pair becomes a `key/value` path; ready-made path
 * strings pass through. Empty strings are dropped, and the result preserves
 * first-seen order with duplicates removed. An empty result represents the
 * caller's default write region.
 */
public fun scopePaths(paths: List<String>): List<String> = normaliseScopePaths(paths)

public fun scopePaths(scope: Map<String, String>): List<String> =
    normaliseScopePaths(scope.map { (k, v) -> "$k/$v" })

public fun scopePaths(vararg pairs: Pair<String, String>): List<String> =
    normaliseScopePaths(pairs.map { (k, v) -> "$k/$v" })

/**
 * Build a scope selector holding a single AND-clause: a reader must cover
 * **every** path in the clause to match. This is the common case, where a
 * record is filed under one combination of scopes (e.g. `org/apple` AND
 * `region/eu`).
 *
 * The result is the DNF (disjunctive-normal-form) shape the wire expects:
 * an outer list of clauses, here with one clause. A single path yields
 * `[["team/eng"]]`. Combine independent owners with [scopeSets].
 */
public fun scopeSet(paths: List<String>): List<List<String>> = normaliseScopeSets(listOf(paths))

public fun scopeSet(scope: Map<String, String>): List<List<String>> =
    scopeSet(scope.map { (k, v) -> "$k/$v" })

public fun scopeSet(vararg pairs: Pair<String, String>): List<List<String>> =
    scopeSet(pairs.map { (k, v) -> "$k/$v" })

/**
 * Build a scope selector from several AND-clauses joined by OR: a reader
 * matches if they cover **all** the paths in **any one** clause. Use this for
 * co-ownership, where the same record is independently owned by more than one
 * party (e.g. `[["org/apple"], ["org/beta", "region/eu"]]`).
 *
 * Each clause is normalised (de-duplicated, empties dropped, order preserved);
 * a clause that normalises to empty is dropped, so no empty clause is ever
 * emitted. An empty result represents the caller's default region.
 */
public fun scopeSets(vararg clauses: List<String>): List<List<String>> =
    normaliseScopeSets(clauses.toList())

internal fun normaliseScopePaths(raw: List<String>?): List<String> {
    if (raw.isNullOrEmpty()) return emptyList()
    val out = ArrayList<String>(raw.size)
    for (path in raw) {
        if (path.isNotEmpty() && path !in out) out.add(path)
    }
    return out
}

/**
 * Normalise a DNF selector for the wire: normalise each clause's paths and drop
 * any clause that ends up empty. The outer order is preserved; the empty outer
 * list means the caller's default region.
 */
internal fun normaliseScopeSets(raw: List<List<String>>?): List<List<String>> {
    if (raw.isNullOrEmpty()) return emptyList()
    val out = ArrayList<List<String>>(raw.size)
    for (clause in raw) {
        val paths = normaliseScopePaths(clause)
        if (paths.isNotEmpty()) out.add(paths)
    }
    return out
}
