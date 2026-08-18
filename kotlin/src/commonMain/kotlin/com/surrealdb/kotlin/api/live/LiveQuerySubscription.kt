package com.surrealdb.kotlin.api.live

import com.surrealdb.kotlin.api.live.SurrealLiveNotification
import kotlinx.coroutines.flow.Flow

public class LiveQuerySubscription internal constructor(
    public val id: String,
    public val events: Flow<SurrealLiveNotification>,
    private val cancelBlock: suspend () -> Unit,
) {
    public suspend fun cancel(): Unit = cancelBlock()
}
