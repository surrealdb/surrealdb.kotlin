package com.surrealdb.kotlin.live

import com.surrealdb.kotlin.model.SurrealLiveNotification
import kotlinx.coroutines.flow.Flow

public class LiveQuerySubscription internal constructor(
    public val id: String,
    public val events: Flow<SurrealLiveNotification>,
    private val cancelBlock: suspend () -> Unit,
) {
    public suspend fun cancel(): Unit = cancelBlock()
}
