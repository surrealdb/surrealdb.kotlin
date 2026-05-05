# surrealdb.kotlin

Kotlin Multiplatform SurrealDB driver for:
- Android
- JVM (server)
- iOS (arm64, x64, simulator)

API surface and behaviour mirror [surrealdb.js v2.0.3](https://github.com/surrealdb/surrealdb.js), which is the cross-SDK reference.

## Features

- Single-URL connection. The engine is selected from the protocol: `http://` or `https://` use the HTTP engine; `ws://` or `wss://` use the WebSocket engine.
- Two engines with explicit capability sets (`SurrealFeature`). Live queries require a WebSocket URL.
- WebSocket reconnection with configurable exponential backoff and pending-call replay across drops.
- Multi-session support: `client.newSession()` returns an isolated session that shares the underlying connection.
- Connection lifecycle exposed as a `SharedFlow<SurrealConnectionEvent>` (`Connecting`, `Connected`, `Disconnected`, `Reconnecting`, `Error`).
- Auto authentication: an optional `credentialProvider` callback re-signs in and retries on auth failure.
- JWT auto-renewal: when a signin response carries a refresh token, renewal is scheduled before the access token's `exp` claim.
- Transaction DSL: `client.transaction { query("...") }` wraps the block in `BEGIN`/`COMMIT`, cancelling on throw.
- Coroutines `Flow` API for live query notifications.

## Supported RPC methods

- Server: `ping`, `version`, `use`, `auth`
- Auth: `signup`, `signin`, `authenticate`, `invalidate`, `reset`
- Session variables: `let`, `unset`
- Queries: `query`, `run`
- CRUD: `select`, `create`, `insert`, `update`, `upsert`, `merge`, `patch`, `delete`
- Graph: `relate`, `insertRelation`
- Live: `live`, `kill`

Each method also has a `Result<JsonElement>` variant suffixed with `Result` (e.g. `queryResult`), and a typed decode variant suffixed with `As` (e.g. `queryAs<T>`, `selectAs<T>`).

## Quick start

```kotlin
val client = SurrealClient(SurrealClientConfig(url = "http://localhost:8000"))

client.signin(buildJsonObject {
    put("user", JsonPrimitive("root"))
    put("pass", JsonPrimitive("root"))
})
client.use("main", "main")

val records = client.query("SELECT * FROM person")
```

Switch to WebSocket transport simply by changing the URL scheme:

```kotlin
val client = SurrealClient(SurrealClientConfig(url = "ws://localhost:8000"))
```

## Live queries

`live(table)` subscribes to changes on a single table and returns a subscription whose `events` is a `Flow`:

```kotlin
val sub = client.live("person")

val job = scope.launch {
    sub.events.collect { event ->
        println("${event.action}: ${event.result}")
    }
}

// later
client.kill(sub.id)
sub.cancel()
job.cancel()
```

For complex `LIVE SELECT` queries with `WHERE` clauses, run the SurrealQL through `query("LIVE SELECT ...")` to obtain the live UUID.

## Multi-session

One connection can host many sessions, each with its own namespace, database, auth token, and variables:

```kotlin
val tenantA = client.newSession()
val tenantB = client.newSession()

tenantA.signin(buildJsonObject { put("user", JsonPrimitive("a")) })
tenantA.use("ns", "db")

tenantB.signin(buildJsonObject { put("user", JsonPrimitive("b")) })
tenantB.use("ns", "db")

tenantA.query("SELECT * FROM person")  // runs as tenant A
tenantB.query("SELECT * FROM person")  // runs as tenant B, isolated

client.closeSession(tenantA)
```

The `SurrealClient` itself is the root session, so the simple single-tenant case keeps using `client.query(...)` directly.

## Transactions

```kotlin
client.transaction {
    query("CREATE person:tx SET name = 'Tx'")
    query("UPDATE counter:1 SET hits += 1")
}  // COMMIT on success, CANCEL on throw
```

## Connection events

```kotlin
scope.launch {
    client.connectionEvents.collect { event ->
        when (event) {
            is SurrealConnectionEvent.Connecting -> println("connecting")
            is SurrealConnectionEvent.Connected -> println("connected")
            is SurrealConnectionEvent.Disconnected -> println("disconnected")
            is SurrealConnectionEvent.Reconnecting ->
                println("retry ${event.attempt} in ${event.delayMillis}ms")
            is SurrealConnectionEvent.Error -> println("error ${event.cause.message}")
        }
    }
}
```

## Configuration

```kotlin
SurrealClientConfig(
    url = "wss://example.com",
    autoConnect = true,
    requestTimeoutMillis = 30_000,
    reconnect = ReconnectConfig(
        enabled = true,
        initialDelayMillis = 250,
        maxDelayMillis = 30_000,
        multiplier = 1.5,
        maxAttempts = null,  // null means infinite
    ),
    tokenRenewalLeadMillis = 60_000,  // renew 60s before exp
    autoAuthenticate = true,
    credentialProvider = {
        SurrealAuthInput.SignIn(buildJsonObject {
            put("user", JsonPrimitive("root"))
            put("pass", JsonPrimitive("root"))
        })
    },
)
```

## Capability checks

```kotlin
if (client.supports(SurrealFeature.LiveQueries)) {
    val sub = client.live("person")
}
```

`HttpEngine` only advertises `ExportImport` and `SurrealML`. `WebSocketEngine` additionally advertises `LiveQueries`, `Sessions`, `Transactions`, and `RefreshTokens`. Calling an unsupported method throws `SurrealFeatureNotSupportedException`.

## Tests

Unit tests:

```bash
./gradlew jvmTest
```

Integration tests against a real SurrealDB:

```bash
docker run -d --name surrealdb -p 8000:8000 surrealdb/surrealdb:latest \
    start --user root --pass root memory

SURREAL_RUN_INTEGRATION=true \
SURREAL_JVM_ENDPOINT=http://127.0.0.1:8000 \
./gradlew jvmTest
```

The integration suite covers the full RPC flow, multi-session isolation, the transaction DSL, and WebSocket live queries.

Run a single test class or method:

```bash
./gradlew jvmTest --tests "com.surrealdb.kotlin.RpcMethodsTest"
./gradlew jvmTest --tests "*ErrorMapping*"
```

Mobile integration tests are opt-in and expect a reachable SurrealDB endpoint:
- Android default endpoint: `http://10.0.2.2:8000`
- iOS default endpoint: `http://127.0.0.1:8000`

## Notes

- Embedded mode is intentionally not included in this release.
- The wire codec is JSON-only. CBOR and flatbuffers can be added behind the codec layer in future versions without breaking the public API.
- Buffered call replay after a WebSocket reconnect can produce duplicate side effects if the original send succeeded but the response was lost during the disconnect. This trade-off matches the surrealdb.js behaviour.
