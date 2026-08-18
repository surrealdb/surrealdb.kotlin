package com.surrealdb.kotlin.error

import kotlinx.serialization.json.JsonElement

public sealed class SurrealException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)

public class SurrealTransportException(
    message: String,
    cause: Throwable? = null,
) : SurrealException(message, cause)

public class SurrealProtocolException(
    message: String,
    cause: Throwable? = null,
) : SurrealException(message, cause)

/**
 * A SurrealDB server reported an RPC failure. [kind] carries the server's
 * structured error taxonomy (see [SurrealErrorKind]) so callers can branch on
 * error semantics — e.g. via `when (exception.kind) { is
 * SurrealErrorKind.Query -> ... }` — instead of parsing [message], which is
 * meant for humans and is not a stable contract.
 *
 * Errors built without wire `kind`/`details` information (e.g. call sites
 * that only have a free-text message, like statement-level query errors)
 * default [kind] to [SurrealErrorKind.Internal].
 */
public open class SurrealRpcException(
    public val code: Int?,
    message: String,
    public val data: JsonElement? = null,
    public val kind: SurrealErrorKind = SurrealErrorKind.Internal,
    cause: Throwable? = null,
) : SurrealException(message, cause)

/**
 * The server rejected the request for an authentication/authorization reason
 * (`kind == "NotAllowed"` with an `Auth` detail). In particular, this is
 * thrown when the session's access token has expired ([isTokenExpired]) or
 * the supplied credentials are invalid ([isInvalidAuth]) — distinguishing the
 * two lets callers re-authenticate only when it can actually help.
 */
public class SurrealAuthenticationException(
    code: Int?,
    message: String,
    data: JsonElement? = null,
    kind: SurrealErrorKind.NotAllowed,
) : SurrealRpcException(code = code, message = message, data = data, kind = kind) {
    private val notAllowed: SurrealErrorKind.NotAllowed get() = kind as SurrealErrorKind.NotAllowed

    /** True if the access token used for this session has expired. */
    public val isTokenExpired: Boolean get() = notAllowed.isTokenExpired

    /** True if the supplied credentials are invalid (as opposed to expired). */
    public val isInvalidAuth: Boolean get() = notAllowed.isInvalidAuth
}

/**
 * The requested resource does not exist on the server (`kind == "NotFound"`).
 * [detail] identifies which kind of resource was missing (table, record,
 * namespace, RPC method, ...) — distinct from [SurrealAlreadyExistsException],
 * which the server reports for the opposite conflict.
 */
public class SurrealNotFoundException(
    code: Int?,
    message: String,
    data: JsonElement? = null,
    kind: SurrealErrorKind.NotFound,
) : SurrealRpcException(code = code, message = message, data = data, kind = kind) {
    public val detail: SurrealErrorKind.NotFound.Detail?
        get() = (kind as SurrealErrorKind.NotFound).detail
}

/**
 * The resource being created already exists on the server
 * (`kind == "AlreadyExists"`) — distinct from [SurrealNotFoundException].
 */
public class SurrealAlreadyExistsException(
    code: Int?,
    message: String,
    data: JsonElement? = null,
    kind: SurrealErrorKind.AlreadyExists,
) : SurrealRpcException(code = code, message = message, data = data, kind = kind) {
    public val detail: SurrealErrorKind.AlreadyExists.Detail?
        get() = (kind as SurrealErrorKind.AlreadyExists).detail
}

/**
 * A query failed to execute (`kind == "Query"`). [isTransactionConflict]
 * distinguishes a conflicting concurrent write — safe to retry — from
 * timeouts, cancellations, or statements skipped after a prior failure in the
 * same batch.
 */
public class SurrealQueryException(
    code: Int?,
    message: String,
    data: JsonElement? = null,
    kind: SurrealErrorKind.Query,
) : SurrealRpcException(code = code, message = message, data = data, kind = kind) {
    private val query: SurrealErrorKind.Query get() = kind as SurrealErrorKind.Query

    /** True if a concurrent transaction wrote to the same data; safe to retry. */
    public val isTransactionConflict: Boolean get() = query.isTransactionConflict

    /** True if the query exceeded its configured timeout. */
    public val isTimedOut: Boolean get() = query.isTimedOut

    /** True if the statement was skipped, e.g. after a prior failure in the same batch. */
    public val isNotExecuted: Boolean get() = query.isNotExecuted

    /** True if the query was cancelled before completing. */
    public val isCancelled: Boolean get() = query.isCancelled
}

public class SurrealFeatureNotSupportedException(
    message: String,
) : SurrealException(message)
