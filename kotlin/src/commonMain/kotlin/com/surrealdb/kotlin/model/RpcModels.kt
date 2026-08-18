package com.surrealdb.kotlin.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement

@Serializable
internal data class SurrealRpcRequest(
    val id: String,
    val method: String,
    val params: List<JsonElement> = emptyList(),
    val txn: String? = null,
)

@Serializable
internal data class SurrealRpcResponse(
    val id: String? = null,
    val result: JsonElement? = null,
    val error: SurrealRpcError? = null,
    val method: String? = null,
    val params: JsonArray? = null,
)

@Serializable
internal data class SurrealRpcError(
    val code: Int? = null,
    val message: String,
    // Structured error taxonomy (see `surrealdb_types::Error` / `ErrorDetails`
    // server-side): `kind` names the error family (e.g. "NotAllowed",
    // "NotFound") and `details` carries family-specific nested data using the
    // same recursive `{ kind, details? }` shape. Both are optional so older
    // servers/wire formats that only send `code`/`message` still decode.
    // Parsed into a typed `SurrealErrorKind` by `mapRpcError`.
    val kind: String? = null,
    val details: JsonElement? = null,
    val data: JsonElement? = null,
)

@Serializable
public data class SurrealLiveNotification(
    val action: String,
    @SerialName("id") val liveQueryId: String,
    val result: JsonElement,
)
