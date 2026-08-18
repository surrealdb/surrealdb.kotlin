package com.surrealdb.kotlin.runtime.engine

import com.surrealdb.kotlin.api.SurrealConnectionEvent
import com.surrealdb.kotlin.api.SurrealFeature
import com.surrealdb.kotlin.api.error.SurrealAlreadyExistsException
import com.surrealdb.kotlin.api.error.SurrealAuthenticationException
import com.surrealdb.kotlin.api.error.SurrealErrorKind
import com.surrealdb.kotlin.api.error.SurrealNotFoundException
import com.surrealdb.kotlin.api.error.SurrealQueryException
import com.surrealdb.kotlin.api.error.SurrealRpcException
import com.surrealdb.kotlin.runtime.SurrealRpcError
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

/**
 * Maps a wire-level [SurrealRpcError] to a typed [SurrealRpcException].
 *
 * The server attaches a structured `kind` (and, for most kinds, nested
 * `details`) to every error it returns — see `surrealdb_types::Error` /
 * `ErrorDetails` server-side. We parse that structured data via
 * [SurrealErrorKind] and use it to pick the most specific exception subtype,
 * rather than guessing from the free-text `message` (JSON-RPC code -32000 is
 * the generic SurrealDB server error used for *every* RPC failure, so it was
 * never a useful signal either).
 *
 * A missing `kind` (e.g. a very old server) falls back to
 * [SurrealErrorKind.Internal], so unrecognised errors still surface as a
 * plain [SurrealRpcException] rather than failing to parse.
 */
internal fun mapRpcError(error: SurrealRpcError): SurrealRpcException {
    val kind = SurrealErrorKind.parse(error.kind, error.details)
    return when {
        kind is SurrealErrorKind.NotAllowed && kind.detail is SurrealErrorKind.NotAllowed.Detail.Auth ->
            SurrealAuthenticationException(code = error.code, message = error.message, data = error.data, kind = kind)
        kind is SurrealErrorKind.NotFound ->
            SurrealNotFoundException(code = error.code, message = error.message, data = error.data, kind = kind)
        kind is SurrealErrorKind.AlreadyExists ->
            SurrealAlreadyExistsException(code = error.code, message = error.message, data = error.data, kind = kind)
        kind is SurrealErrorKind.Query ->
            SurrealQueryException(code = error.code, message = error.message, data = error.data, kind = kind)
        else ->
            SurrealRpcException(code = error.code, message = error.message, data = error.data, kind = kind)
    }
}
