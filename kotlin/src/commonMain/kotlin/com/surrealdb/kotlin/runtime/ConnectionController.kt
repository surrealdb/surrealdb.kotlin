package com.surrealdb.kotlin.runtime

import com.surrealdb.kotlin.api.SurrealClientConfig
import com.surrealdb.kotlin.api.SurrealConnectionEvent
import com.surrealdb.kotlin.api.SurrealFeature
import com.surrealdb.kotlin.api.error.SurrealFeatureNotSupportedException
import com.surrealdb.kotlin.api.live.LiveQuerySubscription
import com.surrealdb.kotlin.runtime.codec.SurrealCodec
import com.surrealdb.kotlin.runtime.engine.HttpEngine
import com.surrealdb.kotlin.runtime.engine.SessionSnapshot
import com.surrealdb.kotlin.runtime.engine.SurrealEngine
import com.surrealdb.kotlin.runtime.engine.WebSocketEngine
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.websocket.WebSockets
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock
import kotlinx.serialization.json.JsonElement

internal class MutableSessionState {
    var namespace: String? = null
    var database: String? = null
    var accessToken: String? = null
    var refreshToken: String? = null
    var variables: MutableMap<String, JsonElement> = mutableMapOf()
    var renewalJob: Job? = null
}

internal class ConnectionController(
    val config: SurrealClientConfig,
) : AutoCloseable {
    private val ownsHttpClient = config.httpClientFactory == null
    private val httpClient: HttpClient = config.httpClientFactory?.invoke(config) ?: defaultHttpClient(config)
    private val codec = SurrealCodec(config)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val engine: SurrealEngine = when {
        isWsUrl(config.url) -> WebSocketEngine(config, httpClient, codec, scope)
        else -> HttpEngine(config, httpClient, codec)
    }

    val features: Set<SurrealFeature> get() = engine.features
    val events: SharedFlow<SurrealConnectionEvent> get() = engine.events

    private val sessionsMutex = Mutex()
    private val sessions = mutableMapOf<String, MutableSessionState>()

    @OptIn(ExperimentalUuidApi::class)
    private fun newSessionId(): String = Uuid.random().toString()

    val rootSessionId: String = newSessionId()

    init {
        sessions[rootSessionId] = MutableSessionState()
        if (config.autoConnect) {
            scope.launch { engine.start() }
        }
    }

    suspend fun connect() {
        engine.start()
    }

    suspend fun newSession(): String {
        val id = newSessionId()
        sessionsMutex.withLock { sessions[id] = MutableSessionState() }
        return id
    }

    suspend fun removeSession(sessionId: String) {
        if (sessionId == rootSessionId) return
        sessionsMutex.withLock {
            sessions.remove(sessionId)?.renewalJob?.cancel()
        }
    }

    fun supports(feature: SurrealFeature): Boolean = feature in engine.features

    // ── Typed protocol passthroughs ──────────────────────────────────────────

    suspend fun health(sessionId: String): JsonElement =
        engine.health(snapshot(sessionId))

    suspend fun version(sessionId: String): JsonElement =
        engine.version(snapshot(sessionId))

    suspend fun use(sessionId: String, namespace: String, database: String): JsonElement =
        engine.use(namespace, database, snapshot(sessionId))

    suspend fun signup(sessionId: String, params: kotlinx.serialization.json.JsonObject): JsonElement =
        engine.signup(params, snapshot(sessionId))

    suspend fun signin(sessionId: String, params: kotlinx.serialization.json.JsonObject): JsonElement =
        engine.signin(params, snapshot(sessionId))

    suspend fun authenticate(sessionId: String, token: String): JsonElement =
        engine.authenticate(token, snapshot(sessionId))

    suspend fun invalidate(sessionId: String): JsonElement =
        engine.invalidate(snapshot(sessionId))

    suspend fun reset(sessionId: String): JsonElement =
        engine.reset(snapshot(sessionId))

    suspend fun set(sessionId: String, name: String, value: JsonElement): JsonElement =
        engine.set(name, value, snapshot(sessionId))

    suspend fun unset(sessionId: String, name: String): JsonElement =
        engine.unset(name, snapshot(sessionId))

    suspend fun query(
        sessionId: String,
        sql: String,
        vars: kotlinx.serialization.json.JsonObject?,
        txn: String? = null,
    ): JsonElement = engine.query(sql, vars, snapshot(sessionId), txn)

    // ── Client-side transactions ─────────────────────────────────────────────

    private fun requireTransactionSupport() {
        if (SurrealFeature.Transactions !in engine.features) {
            throw SurrealFeatureNotSupportedException(
                "Transactions are not supported by the active engine — use a ws:// or wss:// URL"
            )
        }
    }

    suspend fun begin(sessionId: String): String {
        requireTransactionSupport()
        return engine.begin(snapshot(sessionId))
    }

    suspend fun commit(sessionId: String, txnId: String) {
        requireTransactionSupport()
        engine.commit(txnId, snapshot(sessionId))
    }

    suspend fun cancel(sessionId: String, txnId: String) {
        requireTransactionSupport()
        engine.cancel(txnId, snapshot(sessionId))
    }

    suspend fun live(sessionId: String, table: String, diff: Boolean?): LiveQuerySubscription {
        if (SurrealFeature.LiveQueries !in engine.features) {
            throw SurrealFeatureNotSupportedException(
                "Live queries are not supported by the active engine — use a ws:// or wss:// URL"
            )
        }
        return engine.liveQuery(table, diff, snapshot(sessionId))
    }

    suspend fun kill(sessionId: String, liveQueryId: String): JsonElement =
        engine.kill(liveQueryId, snapshot(sessionId))

    suspend fun snapshot(sessionId: String): SessionSnapshot = sessionsMutex.withLock {
        val s = sessions[sessionId] ?: error("Unknown session $sessionId")
        SessionSnapshot(
            token = s.accessToken,
            namespace = s.namespace,
            database = s.database,
            variables = s.variables.toMap(),
        )
    }

    suspend fun update(sessionId: String, block: MutableSessionState.() -> Unit) {
        sessionsMutex.withLock {
            sessions[sessionId]?.block()
        }
    }

    /**
     * Schedule JWT renewal for a session: cancels any existing renewal job and
     * schedules a new one based on the access token's `exp` claim.
     */
    suspend fun scheduleRenewal(sessionId: String, onRenew: suspend () -> Unit) {
        sessionsMutex.withLock {
            val s = sessions[sessionId] ?: return@withLock
            s.renewalJob?.cancel()
            val token = s.accessToken ?: return@withLock
            val expiryMs = parseJwtExpiryMillis(token) ?: return@withLock
            val now = Clock.System.now().toEpochMilliseconds()
            val fireAt = expiryMs - config.tokenRenewalLeadMillis
            val delayMs = (fireAt - now).coerceAtLeast(0)
            s.renewalJob = scope.launch {
                kotlinx.coroutines.delay(delayMs)
                runCatching { onRenew() }
            }
        }
    }

    override fun close() {
        scope.launch {
            sessionsMutex.withLock {
                sessions.values.forEach { it.renewalJob?.cancel() }
                sessions.clear()
            }
            engine.close()
            if (ownsHttpClient) httpClient.close()
            scope.cancel()
        }
    }

    private fun defaultHttpClient(config: SurrealClientConfig): HttpClient = HttpClient {
        install(WebSockets)
        install(HttpTimeout) {
            requestTimeoutMillis = config.requestTimeoutMillis
            connectTimeoutMillis = config.requestTimeoutMillis
            socketTimeoutMillis = config.requestTimeoutMillis
        }
        expectSuccess = false
    }
}

