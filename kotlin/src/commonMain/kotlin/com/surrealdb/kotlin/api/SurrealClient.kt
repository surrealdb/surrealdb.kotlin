package com.surrealdb.kotlin.api

import com.surrealdb.kotlin.api.SurrealConnectionEvent
import com.surrealdb.kotlin.api.SurrealFeature
import com.surrealdb.kotlin.runtime.ConnectionController
import kotlinx.coroutines.flow.SharedFlow

public class SurrealClient private constructor(
    public val config: SurrealClientConfig,
    rootController: ConnectionController,
) : SurrealSession(rootController, rootController.rootSessionId), AutoCloseable {

    public constructor(config: SurrealClientConfig) : this(config, ConnectionController(config))

    /** Stream of connection lifecycle events from the underlying engine. */
    public val connectionEvents: SharedFlow<SurrealConnectionEvent>
        get() = controller.events

    /** Capabilities the active engine supports. */
    public val features: Set<SurrealFeature>
        get() = controller.features

    /** Returns true if the active engine supports the given feature. */
    public fun supports(feature: SurrealFeature): Boolean = feature in features

    /**
     * Eagerly establish the connection. Calling this is only required when the
     * client was constructed with `autoConnect = false`.
     */
    public suspend fun connect() {
        controller.connect()
    }

    /**
     * Create a new session that shares the underlying connection but has its
     * own namespace, database, auth token and session variables.
     */
    public suspend fun newSession(): SurrealSession =
        SurrealSession(controller, controller.newSession())

    /** Remove a previously created session, cancelling any renewal jobs. */
    public suspend fun closeSession(session: SurrealSession) {
        if (session === this) return
        controller.removeSession(session.sessionId)
    }

    public override fun close() {
        controller.close()
    }
}
