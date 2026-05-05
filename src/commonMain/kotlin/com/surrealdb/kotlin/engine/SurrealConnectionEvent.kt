package com.surrealdb.kotlin.engine

public sealed class SurrealConnectionEvent {
    public object Connecting : SurrealConnectionEvent()
    public object Connected : SurrealConnectionEvent()
    public object Disconnected : SurrealConnectionEvent()
    public data class Reconnecting(val attempt: Int, val delayMillis: Long) : SurrealConnectionEvent()
    public data class Error(val cause: Throwable) : SurrealConnectionEvent()
}
