package com.surrealdb.kotlin

import com.surrealdb.kotlin.internal.ConnectionController
import com.surrealdb.kotlin.live.LiveQuerySubscription
import com.surrealdb.kotlin.query.BoundQuery
import com.surrealdb.kotlin.query.QueryDispatcher
import com.surrealdb.kotlin.query.QueryableImpl
import com.surrealdb.kotlin.query.SurrealQueryable
import com.surrealdb.kotlin.query.firstQueryResult
import kotlinx.coroutines.sync.Mutex
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

public open class SurrealSession internal constructor(
    internal val controller: ConnectionController,
    internal val sessionId: String,
) : SurrealQueryable {
    private val authMutex = Mutex()

    // Dispatcher used by builder objects. `txn = null` here — a transaction
    // exposes its own session-bound queryable with a non-null txn.
    @PublishedApi
    internal val sessionDispatcher: QueryDispatcher = object : QueryDispatcher {
        override val json get() = controller.config.json
        override suspend fun dispatch(query: BoundQuery): JsonElement =
            this@SurrealSession.query(query)
    }
    private val queryable = QueryableImpl(sessionDispatcher)

    /** Current namespace for this session, or null if none has been selected. */
    public suspend fun namespace(): String? = controller.snapshot(sessionId).namespace

    /** Current database for this session, or null if none has been selected. */
    public suspend fun database(): String? = controller.snapshot(sessionId).database

    /** Current access token for this session, or null if not authenticated. */
    public suspend fun accessToken(): String? = controller.snapshot(sessionId).token

    public suspend fun ping(): JsonElement = withAutoAuthRetry { controller.health(sessionId) }
    public suspend fun pingResult(): Result<JsonElement> = runCatching { ping() }

    public suspend fun version(): JsonElement = withAutoAuthRetry { controller.version(sessionId) }
    public suspend fun versionResult(): Result<JsonElement> = runCatching { version() }

    public suspend fun use(namespace: String, database: String): JsonElement {
        val result = withAutoAuthRetry { controller.use(sessionId, namespace, database) }
        controller.update(sessionId) {
            this.namespace = namespace
            this.database = database
        }
        return result
    }

    public suspend fun useResult(namespace: String, database: String): Result<JsonElement> =
        runCatching { use(namespace, database) }

    public suspend fun auth(): JsonElement {
        return try {
            firstQueryResult(query(BoundQuery("SELECT * FROM ONLY \$auth")))
        } catch (cause: com.surrealdb.kotlin.error.SurrealRpcException) {
            // v3.0.5 emits this when `$auth` resolves to zero rows — the
            // semantically-correct return is "no auth record bound".
            if (cause.message?.contains("single result output", ignoreCase = true) == true) {
                kotlinx.serialization.json.JsonNull
            } else {
                throw cause
            }
        }
    }

    public suspend fun authResult(): Result<JsonElement> = runCatching { auth() }

    public suspend fun signup(params: JsonObject): JsonElement {
        val result = withAutoAuthRetry { controller.signup(sessionId, params) }
        applyTokenResult(result)
        return result
    }

    public suspend fun signupResult(params: JsonObject): Result<JsonElement> = runCatching { signup(params) }

    public suspend fun signin(params: JsonObject): JsonElement {
        val result = withAutoAuthRetry { controller.signin(sessionId, params) }
        applyTokenResult(result)
        return result
    }

    public suspend fun signinResult(params: JsonObject): Result<JsonElement> = runCatching { signin(params) }

    public suspend fun authenticate(token: String): JsonElement {
        val result = withAutoAuthRetry { controller.authenticate(sessionId, token) }
        controller.update(sessionId) { accessToken = token }
        scheduleRenewalIfPossible()
        return result
    }

    public suspend fun authenticateResult(token: String): Result<JsonElement> = runCatching { authenticate(token) }

    public suspend fun invalidate(): JsonElement {
        val result = withAutoAuthRetry { controller.invalidate(sessionId) }
        controller.update(sessionId) {
            accessToken = null
            refreshToken = null
            renewalJob?.cancel()
            renewalJob = null
        }
        return result
    }

    public suspend fun invalidateResult(): Result<JsonElement> = runCatching { invalidate() }

    public suspend fun reset(): JsonElement {
        val result = withAutoAuthRetry { controller.reset(sessionId) }
        controller.update(sessionId) {
            accessToken = null
            refreshToken = null
            namespace = null
            database = null
            variables.clear()
            renewalJob?.cancel()
            renewalJob = null
        }
        return result
    }

    public suspend fun resetResult(): Result<JsonElement> = runCatching { reset() }

    public suspend fun `let`(key: String, value: JsonElement): JsonElement {
        val result = withAutoAuthRetry { controller.set(sessionId, key, value) }
        controller.update(sessionId) { variables[key] = value }
        return result
    }

    public suspend fun letResult(key: String, value: JsonElement): Result<JsonElement> =
        runCatching { `let`(key, value) }

    public suspend fun unset(key: String): JsonElement {
        val result = withAutoAuthRetry { controller.unset(sessionId, key) }
        controller.update(sessionId) { variables.remove(key) }
        return result
    }

    public suspend fun unsetResult(key: String): Result<JsonElement> = runCatching { unset(key) }

    /** Dispatch a raw SurrealQL string as the `query` RPC. */
    override suspend fun query(sql: String, vars: JsonObject?): JsonElement =
        withAutoAuthRetry { controller.query(sessionId, sql, vars) }

    /** Dispatch a pre-built [BoundQuery] via the `query` RPC. */
    override suspend fun query(bound: BoundQuery): JsonElement =
        query(bound.surql, bound.bindingsAsJsonObject().takeIf { it.isNotEmpty() })

    public suspend fun queryResult(sql: String, vars: JsonObject? = null): Result<JsonElement> =
        runCatching { query(sql, vars) }

    override fun select(what: Any): com.surrealdb.kotlin.query.SelectQuery = queryable.select(what)
    override fun create(what: Any): com.surrealdb.kotlin.query.CreateQuery = queryable.create(what)
    override fun upsert(what: Any): com.surrealdb.kotlin.query.UpsertQuery = queryable.upsert(what)
    override fun update(what: Any): com.surrealdb.kotlin.query.UpdateQuery = queryable.update(what)
    override fun merge(what: Any, data: Any): com.surrealdb.kotlin.query.MergeQuery =
        queryable.merge(what, data)
    override fun patch(what: Any, patches: JsonElement, diff: Boolean): com.surrealdb.kotlin.query.PatchQuery =
        queryable.patch(what, patches, diff)
    override fun delete(what: Any): com.surrealdb.kotlin.query.DeleteQuery = queryable.delete(what)
    override fun relate(`in`: Any, relation: Any, out: Any): com.surrealdb.kotlin.query.RelateQuery =
        queryable.relate(`in`, relation, out)
    override fun insert(into: com.surrealdb.kotlin.query.Table, data: JsonElement): com.surrealdb.kotlin.query.InsertQuery =
        queryable.insert(into, data)
    override fun insertRelation(
        into: com.surrealdb.kotlin.query.Table,
        data: JsonElement,
    ): com.surrealdb.kotlin.query.InsertRelationQuery = queryable.insertRelation(into, data)
    override fun run(function: String): com.surrealdb.kotlin.query.RunQuery = queryable.run(function)

    /**
     * Subscribe to live notifications for changes on a table. The argument is a
     * table name or record id — to use complex `LIVE SELECT` SurrealQL, run it
     * via [query] which will return a live query UUID.
     */
    public suspend fun live(table: String, diff: Boolean? = null): LiveQuerySubscription =
        withAutoAuthRetry { controller.live(sessionId, table, diff) }

    public suspend fun liveResult(table: String, diff: Boolean? = null): Result<LiveQuerySubscription> =
        runCatching { live(table, diff) }

    public suspend fun kill(liveQueryId: String): JsonElement =
        withAutoAuthRetry { controller.kill(sessionId, liveQueryId) }

    public suspend fun killResult(liveQueryId: String): Result<JsonElement> = runCatching { kill(liveQueryId) }

    public val json: kotlinx.serialization.json.Json get() = controller.config.json

    public inline fun <reified T> decode(element: JsonElement): T =
        json.decodeFromJsonElement(element)

    public suspend inline fun <reified T> queryAs(sql: String, vars: JsonObject? = null): T = decode(query(sql, vars))
    public suspend inline fun <reified T> queryAs(bound: BoundQuery): T = decode(query(bound))

    private suspend fun applyTokenResult(result: JsonElement) {
        val tokens = extractTokens(result) ?: return
        controller.update(sessionId) {
            accessToken = tokens.access
            tokens.refresh?.let { refreshToken = it }
        }
        scheduleRenewalIfPossible()
    }

    private suspend fun scheduleRenewalIfPossible() {
        controller.scheduleRenewal(sessionId) {
            authMutex.lock()
            try {
                renewToken()
            } finally {
                authMutex.unlock()
            }
        }
    }

    private suspend fun renewToken() {
        var refresh: String? = null
        controller.update(sessionId) { refresh = refreshToken }
        val rt = refresh ?: return

        // SurrealDB v2 refresh-token RPC: { rt: <refresh_token> } via signin
        val params = kotlinx.serialization.json.buildJsonObject {
            put("rt", kotlinx.serialization.json.JsonPrimitive(rt))
        }
        val result = runCatching { controller.signin(sessionId, params) }.getOrNull() ?: return
        val tokens = extractTokens(result) ?: return
        controller.update(sessionId) {
            accessToken = tokens.access
            tokens.refresh?.let { refreshToken = it }
        }
        scheduleRenewalIfPossible()
    }

    private data class TokenPair(val access: String, val refresh: String?)

    private fun extractTokens(result: JsonElement): TokenPair? = when {
        result is kotlinx.serialization.json.JsonPrimitive && result.isString -> TokenPair(result.content, null)
        result is JsonObject -> {
            val access = (result["access"] ?: result["token"] ?: result["jwt"])?.jsonPrimitive?.content
            val refresh = result["refresh"]?.jsonPrimitive?.content
            access?.let { TokenPair(it, refresh) }
        }
        else -> null
    }

    private suspend fun <T> withAutoAuthRetry(allowRetry: Boolean = true, block: suspend () -> T): T {
        return try {
            block()
        } catch (cause: com.surrealdb.kotlin.error.SurrealAuthenticationException) {
            if (!allowRetry || !controller.config.autoAuthenticate) throw cause
            val provider = controller.config.credentialProvider ?: throw cause
            val credential = provider() ?: throw cause
            applyAuthInput(credential)
            withAutoAuthRetry(allowRetry = false, block = block)
        }
    }

    private suspend fun applyAuthInput(authInput: SurrealAuthInput) {
        when (authInput) {
            is SurrealAuthInput.SignIn -> {
                val result = controller.signin(sessionId, authInput.params)
                applyTokenResult(result)
            }
            is SurrealAuthInput.Token -> {
                controller.authenticate(sessionId, authInput.token)
                controller.update(sessionId) { accessToken = authInput.token }
                scheduleRenewalIfPossible()
            }
        }
    }
}
