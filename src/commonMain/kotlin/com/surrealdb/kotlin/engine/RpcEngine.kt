package com.surrealdb.kotlin.engine

import com.surrealdb.kotlin.SurrealClientConfig
import com.surrealdb.kotlin.error.SurrealFeatureNotSupportedException
import com.surrealdb.kotlin.internal.SurrealCodec
import com.surrealdb.kotlin.internal.randomRequestId
import com.surrealdb.kotlin.live.LiveQuerySubscription
import com.surrealdb.kotlin.model.SurrealRpcRequest
import com.surrealdb.kotlin.model.SurrealRpcResponse
import io.ktor.client.HttpClient
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive

internal abstract class RpcEngine(
    protected val config: SurrealClientConfig,
    protected val httpClient: HttpClient,
    protected val codec: SurrealCodec,
) : SurrealEngine {

    // replay=1 so a subscriber that joins after the connection has already been
    // established still sees the most recent connection state. extraBufferCapacity
    // absorbs bursts (e.g. a quick Connecting → Connected → Disconnected sequence)
    // without dropping events.
    private val _events = MutableSharedFlow<SurrealConnectionEvent>(
        replay = 1,
        extraBufferCapacity = 16,
    )

    final override val events: SharedFlow<SurrealConnectionEvent> = _events.asSharedFlow()

    protected fun publishEvent(event: SurrealConnectionEvent) {
        _events.tryEmit(event)
    }

    protected fun newRequest(
        method: String,
        params: List<JsonElement>,
        txn: String? = null,
    ): SurrealRpcRequest =
        SurrealRpcRequest(id = randomRequestId(), method = method, params = params, txn = txn)

    protected fun unwrap(response: SurrealRpcResponse): JsonElement {
        response.error?.let { throw mapRpcError(it) }
        return response.result ?: JsonNull
    }

    /**
     * Default implementation throws — engines that don't support live queries
     * should leave this alone; engines that do support them must override.
     */
    override suspend fun live(table: String, diff: Boolean?, session: SessionSnapshot): LiveQuerySubscription {
        throw SurrealFeatureNotSupportedException(
            "Live queries are not supported by ${this::class.simpleName} — use a ws:// or wss:// URL"
        )
    }

    /**
     * Default kill implementation routes through the regular rpc path; override
     * if a transport requires special handling.
     */
    override suspend fun kill(liveQueryId: String, session: SessionSnapshot): JsonElement =
        rpc("kill", listOf(JsonPrimitive(liveQueryId)), session, txn = null)
}
