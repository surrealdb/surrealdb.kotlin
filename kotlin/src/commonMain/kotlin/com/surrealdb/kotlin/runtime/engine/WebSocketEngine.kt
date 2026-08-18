package com.surrealdb.kotlin.runtime.engine

import com.surrealdb.kotlin.api.SurrealClientConfig
import com.surrealdb.kotlin.api.SurrealConnectionEvent
import com.surrealdb.kotlin.api.SurrealFeature
import com.surrealdb.kotlin.api.error.SurrealProtocolException
import com.surrealdb.kotlin.api.error.SurrealTransportException
import com.surrealdb.kotlin.api.live.LiveQuerySubscription
import com.surrealdb.kotlin.api.live.SurrealLiveNotification
import com.surrealdb.kotlin.runtime.SurrealRpcRequest
import com.surrealdb.kotlin.runtime.SurrealRpcResponse
import com.surrealdb.kotlin.runtime.codec.SurrealCodec
import com.surrealdb.kotlin.runtime.codec.parseLiveNotification
import com.surrealdb.kotlin.runtime.deriveWsEndpoint
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import io.ktor.websocket.send
import kotlin.concurrent.Volatile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive

internal class WebSocketEngine(
    config: SurrealClientConfig,
    httpClient: HttpClient,
    codec: SurrealCodec,
    private val scope: CoroutineScope,
) : RpcEngine(config, httpClient, codec) {

    override val features: Set<SurrealFeature> = setOf(
        SurrealFeature.LiveQueries,
        SurrealFeature.Sessions,
        SurrealFeature.Transactions,
        SurrealFeature.RefreshTokens,
        SurrealFeature.ExportImport,
        SurrealFeature.SurrealML,
    )

    private val reconnect = ReconnectContext(config.reconnect)
    private val stateMutex = Mutex()
    private var wsSession: DefaultClientWebSocketSession? = null
    private var connectLoopJob: Job? = null
    @Volatile private var terminated = false
    @Volatile private var ready = false

    // Pending RPC calls — buffered so they survive reconnects.
    private val pendingRequests = mutableMapOf<String, BufferedCall>()
    private val liveChannels = mutableMapOf<String, Channel<SurrealLiveNotification>>()

    // Tracks the session state actually applied to the current socket. Reset on
    // each (re)connect so authenticate/use are re-sent.
    private val contextMutex = Mutex()
    private var appliedToken: String? = null
    private var appliedNamespace: String? = null
    private var appliedDatabase: String? = null

    @Volatile private var readyDeferred: CompletableDeferred<Unit> = CompletableDeferred()

    private data class BufferedCall(
        val request: SurrealRpcRequest,
        val deferred: CompletableDeferred<SurrealRpcResponse>,
    )

    override suspend fun start() {
        stateMutex.withLock {
            if (connectLoopJob == null && !terminated) {
                connectLoopJob = scope.launch { connectLoop() }
            }
        }
    }

    override suspend fun dispatch(
        request: SurrealRpcRequest,
        session: SessionSnapshot,
    ): SurrealRpcResponse {
        awaitReady()
        applyContext(session)
        return sendBuffered(request)
    }

    override suspend fun liveQuery(
        table: String,
        diff: Boolean?,
        session: SessionSnapshot,
    ): LiveQuerySubscription {
        awaitReady()
        applyContext(session)
        val params = buildList {
            add(JsonPrimitive(table))
            if (diff != null) add(JsonPrimitive(diff))
        }
        val response = sendBuffered(newRequest("live", params))
        response.error?.let { throw mapRpcError(it) }
        val id = response.result?.jsonPrimitive?.content
            ?: throw SurrealProtocolException("Live query did not return a subscription id")

        val channel = Channel<SurrealLiveNotification>(capacity = Channel.BUFFERED)
        stateMutex.withLock { liveChannels[id] = channel }

        return LiveQuerySubscription(id = id, events = channel.receiveAsFlow()) {
            runCatching { sendBuffered(newRequest("kill", listOf(JsonPrimitive(id)))) }
            stateMutex.withLock { liveChannels.remove(id) }
            channel.close()
        }
    }

    override fun close() {
        terminated = true
        scope.launch { teardown() }
    }

    // ── Connection loop ───────────────────────────────────────────────────────

    private suspend fun connectLoop() {
        try {
            while (!terminated) {
                publishEvent(SurrealConnectionEvent.Connecting)
                val newSession = try {
                    httpClient.webSocketSession(urlString = deriveWsEndpoint(config.url))
                } catch (cause: CancellationException) {
                    throw cause
                } catch (cause: Throwable) {
                    publishEvent(SurrealConnectionEvent.Error(cause))
                    if (!reconnect.allowed) {
                        failAllPending(SurrealTransportException("Failed to connect", cause))
                        return
                    }
                    val delayMs = reconnect.nextDelay()
                    publishEvent(SurrealConnectionEvent.Reconnecting(reconnect.attempt, delayMs))
                    delay(delayMs)
                    continue
                }

                stateMutex.withLock { wsSession = newSession }
                resetAppliedContext()
                replayPending(newSession)

                publishEvent(SurrealConnectionEvent.Connected)
                ready = true
                readyDeferred.complete(Unit)
                reconnect.reset()

                val cause = runCatching { readLoop(newSession) }.exceptionOrNull()
                ready = false
                readyDeferred = CompletableDeferred()

                stateMutex.withLock { if (wsSession === newSession) wsSession = null }
                resetAppliedContext()

                publishEvent(SurrealConnectionEvent.Disconnected)
                if (cause != null && cause !is CancellationException) {
                    publishEvent(SurrealConnectionEvent.Error(cause))
                }

                if (terminated || !reconnect.allowed) {
                    failAllPending(SurrealTransportException("WebSocket terminated", cause))
                    return
                }

                val delayMs = reconnect.nextDelay()
                publishEvent(SurrealConnectionEvent.Reconnecting(reconnect.attempt, delayMs))
                delay(delayMs)
            }
        } finally {
            // Always release any awaitReady() callers so they don't hang.
            terminated = true
            ready = false
            if (!readyDeferred.isCompleted) {
                readyDeferred.completeExceptionally(SurrealTransportException("Engine terminated"))
            }
        }
    }

    private suspend fun replayPending(socket: DefaultClientWebSocketSession) {
        val calls = stateMutex.withLock { pendingRequests.values.toList() }
        for (call in calls) {
            runCatching { socket.send(Frame.Text(codec.encodeWsText(call.request))) }
        }
    }

    private suspend fun readLoop(activeSession: DefaultClientWebSocketSession) {
        for (frame in activeSession.incoming) {
            when (frame) {
                is Frame.Text -> routeIncoming(codec.decodeWsText(frame.readText()))
                is Frame.Close -> throw SurrealTransportException("WebSocket closed by server")
                else -> Unit
            }
        }
        throw SurrealTransportException("WebSocket closed unexpectedly")
    }

    private suspend fun routeIncoming(response: SurrealRpcResponse) {
        val responseId = response.id
        if (responseId != null) {
            val pending = stateMutex.withLock { pendingRequests.remove(responseId) }
            pending?.deferred?.complete(response)
            return
        }
        val live = parseLiveNotification(response)
        if (live != null) {
            val channel = stateMutex.withLock { liveChannels[live.liveQueryId] }
            channel?.trySend(live)
        }
    }

    // ── Sending ──────────────────────────────────────────────────────────────

    private suspend fun sendBuffered(request: SurrealRpcRequest): SurrealRpcResponse {
        val deferred = CompletableDeferred<SurrealRpcResponse>()
        val call = BufferedCall(request, deferred)

        val socket = stateMutex.withLock {
            pendingRequests[request.id] = call
            wsSession
        }

        if (socket != null) {
            runCatching { socket.send(Frame.Text(codec.encodeWsText(request))) }
                .onFailure { /* will be replayed on reconnect */ }
        }

        return try {
            withTimeout(config.requestTimeoutMillis) { deferred.await() }
        } finally {
            stateMutex.withLock { pendingRequests.remove(request.id) }
        }
    }

    // ── Session context ──────────────────────────────────────────────────────

    private suspend fun awaitReady() {
        if (terminated) throw SurrealTransportException("Engine has been closed")
        if (ready) return
        if (connectLoopJob == null) start()
        readyDeferred.await()
    }

    private suspend fun applyContext(snap: SessionSnapshot) {
        contextMutex.withLock {
            if (snap.token != appliedToken) {
                if (snap.token != null) {
                    val r = sendBuffered(newRequest("authenticate", listOf(JsonPrimitive(snap.token))))
                    r.error?.let { throw mapRpcError(it) }
                }
                appliedToken = snap.token
            }
            if (snap.namespace != appliedNamespace || snap.database != appliedDatabase) {
                if (snap.namespace != null && snap.database != null) {
                    val r = sendBuffered(newRequest("use", listOf(
                        JsonPrimitive(snap.namespace), JsonPrimitive(snap.database),
                    )))
                    r.error?.let { throw mapRpcError(it) }
                }
                appliedNamespace = snap.namespace
                appliedDatabase = snap.database
            }
        }
    }

    private fun resetAppliedContext() {
        appliedToken = null
        appliedNamespace = null
        appliedDatabase = null
    }

    // ── Cleanup ──────────────────────────────────────────────────────────────

    private suspend fun failAllPending(cause: SurrealTransportException) {
        val pending: List<BufferedCall>
        val channels: List<Channel<SurrealLiveNotification>>

        stateMutex.withLock {
            pending = pendingRequests.values.toList()
            channels = liveChannels.values.toList()
            pendingRequests.clear()
            liveChannels.clear()
        }

        pending.forEach { it.deferred.completeExceptionally(cause) }
        channels.forEach { it.close(cause) }
    }

    private suspend fun teardown() {
        terminated = true
        connectLoopJob?.cancel()
        val (sessionToClose, channelsToClose) = stateMutex.withLock {
            val s = wsSession
            val ch = liveChannels.values.toList()
            pendingRequests.values.forEach {
                it.deferred.completeExceptionally(SurrealTransportException("Engine closed"))
            }
            pendingRequests.clear()
            liveChannels.clear()
            wsSession = null
            s to ch
        }
        channelsToClose.forEach { it.close() }
        sessionToClose?.close(CloseReason(CloseReason.Codes.NORMAL, "Client closed"))
        publishEvent(SurrealConnectionEvent.Disconnected)
    }
}

