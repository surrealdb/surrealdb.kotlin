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
- Client-side transactions via `begin` / `commit` / `cancel` RPCs with the transaction id carried in the JSON-RPC envelope's `txn` field — every CRUD method inside the block is automatically scoped to that transaction.
- Coroutines `Flow` API for live query notifications.
- Fluent query builder DSL: `client.select(Table("user")).where(field("age") gt 18).limit(10).awaitAs<List<User>>()`. Every CRUD operation compiles to local SurrealQL with bound parameters and dispatches via the `query` RPC, mirroring [surrealdb.js v2.0.3](https://github.com/surrealdb/surrealdb.js).

## Supported RPC methods

The driver speaks JSON-RPC over both HTTP and WebSocket. The transport is picked from the URL scheme.

- Server: `ping`, `version`, `use`
- Auth: `signup`, `signin`, `authenticate`, `invalidate`, `reset`
- Session variables: `let`, `unset`
- Queries: `query`
- Transactions: `begin`, `commit`, `cancel` (WebSocket only)
- Live: `live`, `kill` (WebSocket only)

CRUD operations (`select`, `create`, `update`, `upsert`, `merge`, `patch`, `delete`, `relate`, `insert`, `insertRelation`, `run`) are not dedicated RPC methods — they compile locally to SurrealQL and dispatch through `query`. This matches the [surrealdb.js](https://github.com/surrealdb/surrealdb.js/tree/main/packages/sdk/src/query) approach and keeps the wire protocol slim.

For typed decoding, every builder exposes `awaitAs<T>()`; the raw `query()` family has `queryAs<T>()` plus `Result<JsonElement>` variants suffixed with `Result`.

## Quick start

```kotlin
val client = SurrealClient(SurrealClientConfig(url = "http://localhost:8000"))

client.signin(buildJsonObject {
    put("user", JsonPrimitive("root"))
    put("pass", JsonPrimitive("root"))
})
client.use("main", "main")

// Raw SurrealQL
val rows = client.query("SELECT * FROM person")

// Or the fluent builder
@Serializable data class Person(val id: String, val name: String, val age: Int)

val adults: List<Person> = client
    .select(Table("person"))
    .where(field("age") gte 18)
    .limit(50)
    .awaitAs()
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

Transactions are client-side: the SDK sends a `begin` RPC, captures the returned transaction id, and tags every subsequent `query` / CRUD-builder dispatch with that id in the JSON-RPC envelope's `txn` field. `commit` or `cancel` closes it. Requires a WebSocket URL.

Block form (commits on success, cancels on throw):

```kotlin
client.transaction {
    create(RecordId("person", "tx")).content(buildJsonObject { put("name", JsonPrimitive("Tx")) }).await()
    update(RecordId("counter", "1")).content(buildJsonObject { put("hits", JsonPrimitive(2)) }).await()
}
```

Explicit form for cases where you need finer control:

```kotlin
val tx = client.beginTransaction()
try {
    tx.create(Table("person")).content(buildJsonObject { put("name", JsonPrimitive("Ada")) }).await()
    tx.commit()
} catch (cause: Throwable) {
    tx.cancel()
    throw cause
}
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
