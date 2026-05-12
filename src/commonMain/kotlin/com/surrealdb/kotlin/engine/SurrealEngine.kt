package com.surrealdb.kotlin.engine

import com.surrealdb.kotlin.error.SurrealAuthenticationException
import com.surrealdb.kotlin.error.SurrealRpcException
import com.surrealdb.kotlin.model.SurrealRpcError
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.serialization.json.JsonElement

internal data class SessionSnapshot(
    val token: String?,
    val namespace: String?,
    val database: String?,
    val variables: Map<String, JsonElement> = emptyMap(),
)

internal interface SurrealEngine : SurrealProtocol, AutoCloseable {
    val features: Set<SurrealFeature>
    val events: SharedFlow<SurrealConnectionEvent>

    suspend fun start()
}

internal fun mapRpcError(error: SurrealRpcError): SurrealRpcException {
    // We classify by message keywords only — JSON-RPC code -32000 is the
    // generic SurrealDB server error and is used for *every* RPC failure, so
    // it's not a useful signal on its own.
    val message = error.message.lowercase()
    val isAuth = message.contains("authentication") ||
        message.contains("not enough permission") ||
        message.contains("invalid jwt") ||
        message.contains("token") ||
        message.contains("signin") ||
        message.contains("signup")
    return if (isAuth) {
        SurrealAuthenticationException(code = error.code, message = error.message, data = error.data)
    } else {
        SurrealRpcException(code = error.code, message = error.message, data = error.data)
    }
}
