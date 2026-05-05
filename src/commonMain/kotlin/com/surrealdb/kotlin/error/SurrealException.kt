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

public open class SurrealRpcException(
    public val code: Int?,
    message: String,
    public val data: JsonElement? = null,
    cause: Throwable? = null,
) : SurrealException(message, cause)

public class SurrealAuthenticationException(
    code: Int?,
    message: String,
    data: JsonElement? = null,
) : SurrealRpcException(code = code, message = message, data = data)

public class SurrealFeatureNotSupportedException(
    message: String,
) : SurrealException(message)
