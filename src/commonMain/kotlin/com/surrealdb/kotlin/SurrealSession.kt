package com.surrealdb.kotlin

import com.surrealdb.kotlin.internal.ConnectionController
import com.surrealdb.kotlin.live.LiveQuerySubscription
import kotlinx.coroutines.sync.Mutex
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

public open class SurrealSession internal constructor(
    internal val controller: ConnectionController,
    internal val sessionId: String,
) {
    private val authMutex = Mutex()

    /** Current namespace for this session, or null if none has been selected. */
    public suspend fun namespace(): String? = controller.snapshot(sessionId).namespace

    /** Current database for this session, or null if none has been selected. */
    public suspend fun database(): String? = controller.snapshot(sessionId).database

    /** Current access token for this session, or null if not authenticated. */
    public suspend fun accessToken(): String? = controller.snapshot(sessionId).token

    // ── Raw RPC ───────────────────────────────────────────────────────────────

    public suspend fun rpc(method: String, params: List<JsonElement> = emptyList()): JsonElement =
        withAutoAuthRetry { controller.rpc(sessionId, method, params) }

    public suspend fun rpcResult(method: String, params: List<JsonElement> = emptyList()): Result<JsonElement> =
        runCatching { rpc(method, params) }

    // ── Server ────────────────────────────────────────────────────────────────

    public suspend fun ping(): JsonElement = rpc("ping")
    public suspend fun pingResult(): Result<JsonElement> = runCatching { ping() }

    public suspend fun version(): JsonElement = rpc("version")
    public suspend fun versionResult(): Result<JsonElement> = runCatching { version() }

    public suspend fun use(namespace: String, database: String): JsonElement {
        val result = rpc("use", listOf(JsonPrimitive(namespace), JsonPrimitive(database)))
        controller.update(sessionId) {
            this.namespace = namespace
            this.database = database
        }
        return result
    }

    public suspend fun useResult(namespace: String, database: String): Result<JsonElement> =
        runCatching { use(namespace, database) }

    /** Returns the record of the currently authenticated user. */
    public suspend fun auth(): JsonElement = rpc("info")
    public suspend fun authResult(): Result<JsonElement> = runCatching { auth() }

    // ── Auth ──────────────────────────────────────────────────────────────────

    public suspend fun signup(params: JsonObject): JsonElement {
        val result = rpc("signup", listOf(params))
        applyTokenResult(result)
        return result
    }

    public suspend fun signupResult(params: JsonObject): Result<JsonElement> = runCatching { signup(params) }

    public suspend fun signin(params: JsonObject): JsonElement {
        val result = rpc("signin", listOf(params))
        applyTokenResult(result)
        return result
    }

    public suspend fun signinResult(params: JsonObject): Result<JsonElement> = runCatching { signin(params) }

    public suspend fun authenticate(token: String): JsonElement {
        val result = rpc("authenticate", listOf(JsonPrimitive(token)))
        controller.update(sessionId) { accessToken = token }
        scheduleRenewalIfPossible()
        return result
    }

    public suspend fun authenticateResult(token: String): Result<JsonElement> = runCatching { authenticate(token) }

    public suspend fun invalidate(): JsonElement {
        val result = rpc("invalidate")
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
        val result = rpc("reset")
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

    // ── Session variables ─────────────────────────────────────────────────────

    public suspend fun `let`(key: String, value: JsonElement): JsonElement {
        val result = rpc("let", listOf(JsonPrimitive(key), value))
        controller.update(sessionId) { variables[key] = value }
        return result
    }

    public suspend fun letResult(key: String, value: JsonElement): Result<JsonElement> =
        runCatching { `let`(key, value) }

    public suspend fun unset(key: String): JsonElement {
        val result = rpc("unset", listOf(JsonPrimitive(key)))
        controller.update(sessionId) { variables.remove(key) }
        return result
    }

    public suspend fun unsetResult(key: String): Result<JsonElement> = runCatching { unset(key) }

    // ── Query / run ───────────────────────────────────────────────────────────

    public suspend fun query(sql: String, vars: JsonObject? = null): JsonElement =
        rpc("query", buildList { add(JsonPrimitive(sql)); if (vars != null) add(vars) })

    public suspend fun queryResult(sql: String, vars: JsonObject? = null): Result<JsonElement> =
        runCatching { query(sql, vars) }

    public suspend fun run(function: String, version: String? = null, args: List<JsonElement> = emptyList()): JsonElement =
        rpc("run", listOf(
            JsonPrimitive(function),
            if (version != null) JsonPrimitive(version) else JsonNull,
            JsonArray(args),
        ))

    public suspend fun runResult(function: String, version: String? = null, args: List<JsonElement> = emptyList()): Result<JsonElement> =
        runCatching { run(function, version, args) }

    // ── CRUD ──────────────────────────────────────────────────────────────────

    public suspend fun select(thing: String): JsonElement = rpc("select", listOf(JsonPrimitive(thing)))
    public suspend fun selectResult(thing: String): Result<JsonElement> = runCatching { select(thing) }

    public suspend fun create(thing: String, data: JsonElement? = null): JsonElement =
        rpc("create", buildList { add(JsonPrimitive(thing)); if (data != null) add(data) })

    public suspend fun createResult(thing: String, data: JsonElement? = null): Result<JsonElement> =
        runCatching { create(thing, data) }

    public suspend fun insert(thing: String, data: JsonElement): JsonElement =
        rpc("insert", listOf(JsonPrimitive(thing), data))

    public suspend fun insertResult(thing: String, data: JsonElement): Result<JsonElement> =
        runCatching { insert(thing, data) }

    public suspend fun update(thing: String, data: JsonElement? = null): JsonElement =
        rpc("update", buildList { add(JsonPrimitive(thing)); if (data != null) add(data) })

    public suspend fun updateResult(thing: String, data: JsonElement? = null): Result<JsonElement> =
        runCatching { update(thing, data) }

    public suspend fun upsert(thing: String, data: JsonElement? = null): JsonElement =
        rpc("upsert", buildList { add(JsonPrimitive(thing)); if (data != null) add(data) })

    public suspend fun upsertResult(thing: String, data: JsonElement? = null): Result<JsonElement> =
        runCatching { upsert(thing, data) }

    public suspend fun merge(thing: String, data: JsonElement? = null): JsonElement =
        rpc("merge", buildList { add(JsonPrimitive(thing)); if (data != null) add(data) })

    public suspend fun mergeResult(thing: String, data: JsonElement? = null): Result<JsonElement> =
        runCatching { merge(thing, data) }

    public suspend fun patch(thing: String, patches: JsonElement, diff: Boolean? = null): JsonElement =
        rpc("patch", buildList { add(JsonPrimitive(thing)); add(patches); if (diff != null) add(JsonPrimitive(diff)) })

    public suspend fun patchResult(thing: String, patches: JsonElement, diff: Boolean? = null): Result<JsonElement> =
        runCatching { patch(thing, patches, diff) }

    public suspend fun delete(thing: String): JsonElement = rpc("delete", listOf(JsonPrimitive(thing)))
    public suspend fun deleteResult(thing: String): Result<JsonElement> = runCatching { delete(thing) }

    // ── Graph ─────────────────────────────────────────────────────────────────

    public suspend fun relate(inRecord: String, relation: String, outRecord: String, data: JsonElement? = null): JsonElement =
        rpc("relate", buildList {
            add(JsonPrimitive(inRecord)); add(JsonPrimitive(relation)); add(JsonPrimitive(outRecord))
            if (data != null) add(data)
        })

    public suspend fun relateResult(inRecord: String, relation: String, outRecord: String, data: JsonElement? = null): Result<JsonElement> =
        runCatching { relate(inRecord, relation, outRecord, data) }

    public suspend fun insertRelation(inRecord: String, relation: String, outRecord: String, data: JsonElement? = null): JsonElement =
        rpc("insert_relation", buildList {
            add(JsonPrimitive(inRecord)); add(JsonPrimitive(relation)); add(JsonPrimitive(outRecord))
            if (data != null) add(data)
        })

    public suspend fun insertRelationResult(inRecord: String, relation: String, outRecord: String, data: JsonElement? = null): Result<JsonElement> =
        runCatching { insertRelation(inRecord, relation, outRecord, data) }

    // ── Live queries ──────────────────────────────────────────────────────────

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

    // ── Typed helpers ─────────────────────────────────────────────────────────

    public val json: kotlinx.serialization.json.Json get() = controller.config.json

    public inline fun <reified T> decode(element: JsonElement): T =
        json.decodeFromJsonElement(element)

    public suspend inline fun <reified T> queryAs(sql: String, vars: JsonObject? = null): T = decode(query(sql, vars))
    public suspend inline fun <reified T> selectAs(thing: String): T = decode(select(thing))
    public suspend inline fun <reified T> createAs(thing: String, data: JsonElement? = null): T = decode(create(thing, data))
    public suspend inline fun <reified T> insertAs(thing: String, data: JsonElement): T = decode(insert(thing, data))
    public suspend inline fun <reified T> upsertAs(thing: String, data: JsonElement? = null): T = decode(upsert(thing, data))
    public suspend inline fun <reified T> updateAs(thing: String, data: JsonElement? = null): T = decode(update(thing, data))
    public suspend inline fun <reified T> mergeAs(thing: String, data: JsonElement? = null): T = decode(merge(thing, data))
    public suspend inline fun <reified T> patchAs(thing: String, patches: JsonElement, diff: Boolean? = null): T = decode(patch(thing, patches, diff))
    public suspend inline fun <reified T> deleteAs(thing: String): T = decode(delete(thing))

    // ── Internals ─────────────────────────────────────────────────────────────

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
            put("rt", JsonPrimitive(rt))
        }
        val result = runCatching { rpc("signin", listOf(params)) }.getOrNull() ?: return
        val tokens = extractTokens(result) ?: return
        controller.update(sessionId) {
            accessToken = tokens.access
            tokens.refresh?.let { refreshToken = it }
        }
        scheduleRenewalIfPossible()
    }

    private data class TokenPair(val access: String, val refresh: String?)

    private fun extractTokens(result: JsonElement): TokenPair? = when {
        result is JsonPrimitive && result.isString -> TokenPair(result.content, null)
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
                val result = controller.rpc(sessionId, "signin", listOf(authInput.params))
                applyTokenResult(result)
            }
            is SurrealAuthInput.Token -> {
                controller.rpc(sessionId, "authenticate", listOf(JsonPrimitive(authInput.token)))
                controller.update(sessionId) { accessToken = authInput.token }
                scheduleRenewalIfPossible()
            }
        }
    }
}
