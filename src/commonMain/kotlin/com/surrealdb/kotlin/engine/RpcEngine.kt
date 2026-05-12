package com.surrealdb.kotlin.engine

import com.surrealdb.kotlin.SurrealClientConfig
import com.surrealdb.kotlin.error.SurrealFeatureNotSupportedException
import com.surrealdb.kotlin.error.SurrealProtocolException
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
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Base for JSON-RPC transports. Translates each `SurrealProtocol` call into a
 * JSON-RPC envelope and delegates wire delivery to a single [dispatch] hook
 * provided by the subclass. This keeps every JSON-RPC method name in exactly
 * one place — adding a new protocol operation only needs a method here, not
 * changes scattered across the controller and session layers.
 */
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
     * Transport hook implemented by subclasses (`WebSocketEngine`, `HttpEngine`).
     * Receives a fully-formed JSON-RPC request and the session snapshot and
     * returns the raw response — the protocol methods below funnel through here.
     */
    protected abstract suspend fun dispatch(
        request: SurrealRpcRequest,
        session: SessionSnapshot,
    ): SurrealRpcResponse

    // ── Protocol methods ─────────────────────────────────────────────────────

    override suspend fun health(session: SessionSnapshot): JsonElement =
        unwrap(dispatch(newRequest("ping", emptyList()), session))

    override suspend fun version(session: SessionSnapshot): JsonElement =
        unwrap(dispatch(newRequest("version", emptyList()), session))

    override suspend fun use(
        namespace: String,
        database: String,
        session: SessionSnapshot,
    ): JsonElement = unwrap(dispatch(
        newRequest("use", listOf(JsonPrimitive(namespace), JsonPrimitive(database))),
        session,
    ))

    override suspend fun signup(params: JsonObject, session: SessionSnapshot): JsonElement =
        unwrap(dispatch(newRequest("signup", listOf(params)), session))

    override suspend fun signin(params: JsonObject, session: SessionSnapshot): JsonElement =
        unwrap(dispatch(newRequest("signin", listOf(params)), session))

    override suspend fun authenticate(token: String, session: SessionSnapshot): JsonElement =
        unwrap(dispatch(newRequest("authenticate", listOf(JsonPrimitive(token))), session))

    override suspend fun invalidate(session: SessionSnapshot): JsonElement =
        unwrap(dispatch(newRequest("invalidate", emptyList()), session))

    override suspend fun reset(session: SessionSnapshot): JsonElement =
        unwrap(dispatch(newRequest("reset", emptyList()), session))

    override suspend fun set(
        name: String,
        value: JsonElement,
        session: SessionSnapshot,
    ): JsonElement = unwrap(dispatch(
        newRequest("let", listOf(JsonPrimitive(name), value)),
        session,
    ))

    override suspend fun unset(name: String, session: SessionSnapshot): JsonElement =
        unwrap(dispatch(newRequest("unset", listOf(JsonPrimitive(name))), session))

    override suspend fun begin(session: SessionSnapshot): String {
        val result = unwrap(dispatch(newRequest("begin", emptyList()), session))
        val primitive = result as? JsonPrimitive
        return if (primitive != null && primitive.isString) {
            primitive.content
        } else {
            throw SurrealProtocolException("begin did not return a transaction id (got $result)")
        }
    }

    override suspend fun commit(txnId: String, session: SessionSnapshot) {
        unwrap(dispatch(newRequest("commit", listOf(JsonPrimitive(txnId))), session))
    }

    override suspend fun cancel(txnId: String, session: SessionSnapshot) {
        unwrap(dispatch(newRequest("cancel", listOf(JsonPrimitive(txnId))), session))
    }

    override suspend fun query(
        sql: String,
        vars: JsonObject?,
        session: SessionSnapshot,
        txn: String?,
    ): JsonElement = unwrap(dispatch(
        newRequest(
            method = "query",
            params = buildList {
                add(JsonPrimitive(sql))
                if (vars != null) add(vars)
            },
            txn = txn,
        ),
        session,
    ))

    /**
     * Default implementation throws — engines that don't support live queries
     * should leave this alone; engines that do support them must override.
     */
    override suspend fun liveQuery(
        table: String,
        diff: Boolean?,
        session: SessionSnapshot,
    ): LiveQuerySubscription {
        throw SurrealFeatureNotSupportedException(
            "Live queries are not supported by ${this::class.simpleName} — use a ws:// or wss:// URL"
        )
    }

    override suspend fun kill(liveQueryId: String, session: SessionSnapshot): JsonElement =
        unwrap(dispatch(newRequest("kill", listOf(JsonPrimitive(liveQueryId))), session))
}
