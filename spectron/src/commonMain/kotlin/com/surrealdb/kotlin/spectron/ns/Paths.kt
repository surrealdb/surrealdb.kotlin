package com.surrealdb.kotlin.spectron.ns

import com.surrealdb.kotlin.spectron.quotePath

internal fun enduserBase(contextId: String): String = "/api/v1/${quotePath(contextId)}"
