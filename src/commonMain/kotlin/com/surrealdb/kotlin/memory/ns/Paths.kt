package com.surrealdb.kotlin.memory.ns

import com.surrealdb.kotlin.memory.quotePath

internal fun enduserBase(contextId: String): String = "/api/v1/${quotePath(contextId)}"
