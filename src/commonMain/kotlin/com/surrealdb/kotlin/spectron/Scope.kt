package com.surrealdb.kotlin.spectron

/**
 * Normalise scope inputs to an ordered, de-duplicated list of slash-path
 * strings, e.g. `["team/eng"]`. Mirrors the surrealdb.py `scope_paths` helper.
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

internal fun normaliseScopePaths(raw: List<String>?): List<String> {
    if (raw.isNullOrEmpty()) return emptyList()
    val out = ArrayList<String>(raw.size)
    for (path in raw) {
        if (path.isNotEmpty() && path !in out) out.add(path)
    }
    return out
}
