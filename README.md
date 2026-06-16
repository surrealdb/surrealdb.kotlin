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
- [Spectron](#spectron) client bundled under `com.surrealdb.kotlin.spectron` for memory and knowledge management.

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

## Spectron

The `com.surrealdb.kotlin.spectron` package ships a client for [Spectron](https://surrealdb.com/platform/spectron), the agent memory and knowledge service. It speaks the Spectron end-user HTTP API and is independent of the SurrealDB RPC engine.

```kotlin
import com.surrealdb.kotlin.spectron.Spectron

val memory = Spectron(
    contextId = "acme-prod",
    apiKey = "sk-spec-...",
    endpoint = "https://api.spectron.example",
)
memory.remember("I work at Acme as CTO")
val hits = memory.recall("what do I do at Acme", k = 5)
memory.close()
```

All methods are `suspend`. Wrap in `runBlocking { ... }` for synchronous callers.

### Constructor

| Param | Default | |
|---|---|---|
| `contextId` | required | Context id, e.g. `"acme-prod"` |
| `apiKey` | required | Bearer token |
| `endpoint` | required | Endpoint, e.g. `"https://api.spectron.example"` |
| `timeout` | `30.seconds` | Per-request timeout |
| `maxRetries` | `3` | GET-only retries on 5xx and connect errors |
| `httpClient` | platform default | Inject your own Ktor `HttpClient` for tests |
| `json` | lenient | `kotlinx.serialization` Json instance |

`apiKey` and `endpoint` are mutable and take effect on the next request.

The client exposes top-level verbs (`remember`, `rememberMany`, `recall`, `forget`, `chat`, `consolidate`, `audit`, `reflect`, `elaborate`, `inspect`, `queryContext`, `state`, `profile`, `whoami`, `health`) plus the namespaced surface: `documents`, `sessions`, `entities`, `lifecycle`, `traces`, `principals`, `scopes`, and `keys`. This mirrors the method placement of the [surrealdb.py](https://github.com/surrealdb/surrealdb.py) Spectron client.

Scopes are slash-path strings (`"team/eng"`). A scope selector is a DNF (disjunctive-normal-form) value of type `List<List<String>>`: an OR of clauses, where each clause is an AND of scope paths. A reader matches if they cover **all** the paths in **any one** clause. For example `listOf(listOf("org/apple"), listOf("org/beta", "region/eu"))` means `org/apple` OR (`org/beta` AND `region/eu`). An empty list targets the caller's default region.

Two helpers build the shape ergonomically. `scopeSet(...)` makes a single AND-clause from paths, a map, or `(key, value)` pairs (the common case, where a record is filed under one combination). `scopeSets(...)` joins several clauses with OR, for co-ownership. Both normalise paths, de-duplicate, preserve order, and drop empty clauses. `scopePaths(...)` is the building block returning a single clause's `List<String>`.

```kotlin
import com.surrealdb.kotlin.spectron.scopeSet
import com.surrealdb.kotlin.spectron.scopeSets

scopeSet(mapOf("org" to "acme"))                      // [["org/acme"]]
scopeSet(listOf("org/acme", "region/eu"))             // [["org/acme", "region/eu"]]  (AND)
scopeSets(listOf("org/apple"), listOf("org/beta"))    // [["org/apple"], ["org/beta"]]  (OR)
```

A single path is the same under either operator, so `scopeSet(listOf("team/eng"))` is just `[["team/eng"]]`. Note the shape changed: a flat `List<String>` of several paths was previously an AND; the equivalent is now a single nested clause via `scopeSet(...)`.

Every call accepts an optional `onBehalfOf` argument. When set, the request carries the `X-Spectron-On-Behalf-Of` header so a privileged caller can act as another principal:

```kotlin
memory.recall("open incidents", onBehalfOf = "alpha-bot")
memory.documents.list(status = "ready", onBehalfOf = "alpha-bot")
```

### Memory verbs

```kotlin
import com.surrealdb.kotlin.spectron.model.InferMode
import com.surrealdb.kotlin.spectron.model.Triple
import com.surrealdb.kotlin.spectron.model.TripleEntity

// Free-form fact, extracted server-side.
memory.remember("Christian was promoted to CTO", infer = InferMode.FULL)

// Caller-supplied triples, no LLM.
memory.remember(
    triples = listOf(
        Triple(entity = TripleEntity("christian", "Person"), key = "role", value = "CTO"),
    ),
    infer = InferMode.TRIPLES,
)

// Retrieval over facts and document passages.
val result = memory.recall("What role does Christian have?", k = 10, mode = "hybrid")
result.hits.forEach { println("${it.source} ${it.score} ${it.text}") }

memory.queryContext("brief on tobie", k = 10)
memory.state()
memory.profile()
memory.reflect("patterns in customer complaints this month?", persist = true)
memory.forget("anything about my old job", purge = false)
```

`chat` runs a server-driven turn that retrieves, generates, and persists memory updates in one call:

```kotlin
val reply = memory.chat("What do you know about me?", sessionId = session.id)
println(reply.reply)
println(reply.memoryUpdates?.entities)
```

### Documents

```kotlin
val doc = memory.documents.upload(
    file = bytes,
    filename = "returns.pdf",
    contentType = "application/pdf",
    title = "Returns Policy",
    source = "support-portal",
    scopes = scopeSet(listOf("team/support")),
)

memory.documents.get(doc.id)
memory.documents.chunks(doc.id, page = 0, pageSize = 50)
memory.documents.list(status = "ready", mimeType = "application/pdf")
memory.documents.fetchRaw(doc.id)
memory.documents.reprocess(doc.id)        // re-run the ingestion pipeline
memory.documents.recomputeLinks()
memory.documents.delete(doc.id)
```

Uploads accept a `ByteArray`. On JVM and Android, read a file with `file.readBytes()`. On iOS, use `NSData.bytes` via the appropriate interop.

#### Query

```kotlin
import com.surrealdb.kotlin.spectron.model.GraphEdgeKind
import com.surrealdb.kotlin.spectron.model.QueryMode
import com.surrealdb.kotlin.spectron.model.QueryFilter

val hits = memory.documents.query(
    "what is the return window for unopened items?",
    mode = QueryMode.HYBRID_GRAPH,
    k = 10,
    threshold = 0.5,
    vectorWeight = 0.5,
    rrfK = 60.0,
    graphAlpha = 0.3,
    graphEdges = listOf(GraphEdgeKind.KNOWLEDGE_HAS_KEYWORD, GraphEdgeKind.DOCUMENT_LINK),
    graphDepth = 2,
    expandGraph = true,
    filter = QueryFilter(mimeType = listOf("application/pdf")),
)
```

#### Keywords

```kotlin
memory.documents.keywords.list(minDocumentCount = 2, sort = "-documentCount", q = "return")
memory.documents.keywords.search("refund policies", k = 10, threshold = 0.6)
memory.documents.keywords.get("return-window")
memory.documents.keywords.forDocument(doc.id)
```

### Sessions

`create` returns a `SpectronSession` handle with `chat`, `remember`, `context`, `turns`, and `close`:

```kotlin
val session = memory.sessions.create(scopes = scopeSet(listOf("user/tobie")))

// Server-driven turn scoped to the session.
val reply = session.chat("What do you know about me?")

// Or drive the turns yourself.
session.remember("I just got promoted to CTO", role = TurnRole.USER)
val ctx = session.context("What is Tobie's role?")
val answer = myLlm.chat(system = ctx.context, user = userMessage)
session.remember(answer, role = TurnRole.ASSISTANT)
session.turns(limit = 50)
session.close()
```

The same operations are available as flat namespace calls keyed by session id, matching the Python client:

```kotlin
memory.sessions.context("sess-1", "What is Tobie's role?")
memory.sessions.turns("sess-1", limit = 50, offset = 0)
memory.sessions.delete("sess-1")
```

### Entities, lifecycle, traces

```kotlin
memory.entities.list(type = "Person")
memory.entities.get("Person", "christian_battaglia")
memory.entities.history("Person", "christian_battaglia", key = "role")
memory.entities.delete("Person", "christian_battaglia")

memory.lifecycle.expire()
memory.lifecycle.decay()
memory.lifecycle.fsck(check = "contradictions")

memory.traces.list(limit = 50)
memory.traces.get("decision_trace:abc123")
memory.traces.stats()
```

### Maintenance and introspection

```kotlin
memory.consolidate(dryRun = true)
memory.elaborate(entityRef = "Person/christian", sweep = false)
memory.inspect(ref = "entity:Person/christian", asOf = "2026-01-01T00:00:00Z")
memory.audit(principal = "alpha-bot", limit = 100)

memory.whoami()                 // caller identity and resolved grants
memory.health()                 // liveness probe, not context-scoped
```

### Governance

```kotlin
memory.principals.list()
memory.principals.effective("alpha-bot", path = "org/apple/")
memory.principals.grant("alpha-bot", path = "org/apple/*", verbs = listOf("read", "write"))
memory.principals.revoke("alpha-bot", path = "org/apple/*", verbs = listOf("write"))

memory.scopes.list()
memory.scopes.register("org/apple/product/ipad/", displayName = "iPad")
memory.scopes.forget("org/apple/product/ipad/")
memory.scopes.delete("org/apple/product/ipad/")

// Self-service API keys. The full secret is returned only once, on create or rotate.
val minted = memory.keys.create(name = "ci", ttlSeconds = 3600)
memory.keys.list()
memory.keys.rotate("ci", ttlSeconds = 7200)
memory.keys.delete("ci")
```

Audit lives at the top level as `memory.audit(...)` (see the previous section), mirroring the Python client.

### Errors

```kotlin
try {
    memory.documents.get("doc:missing")
} catch (e: SpectronNotFoundException) {
    println("${e.status}: ${e.title}")
} catch (e: SpectronRateLimitException) {
    println("retry after ${e.retryAfter}")
}
```

| Exception | HTTP |
|---|---|
| `SpectronException` | sealed base |
| `SpectronAuthException` | 401 |
| `SpectronScopeException` | 403 |
| `SpectronNotFoundException` | 404 |
| `SpectronValidationException` | 400, 422 |
| `SpectronRateLimitException` | 429 (with `retryAfter: Duration?`) |
| `SpectronServerException` | 5xx |
| `SpectronTransportException` | connect or parse failure |

Each carries `status`, `title`, `detail`, `typeUri`, `instance`, and `extensions: Map<String, JsonElement>`. The Spectron API returns a `{ "message": "..." }` error envelope, surfaced as `title`.

### Retries and scope

- GETs retry on connection errors and 5xx with 250 ms, 500 ms, and 1 s backoff, capped to `maxRetries` (default 3). Writes never retry.
- Scope selectors are sent as a DNF `List<List<String>>` (an OR of AND-clauses) of hierarchical `key/value` paths, matching the Spectron scope model. The read `lens` takes the same shape.

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
