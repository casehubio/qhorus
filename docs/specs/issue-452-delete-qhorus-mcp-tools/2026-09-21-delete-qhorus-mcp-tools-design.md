# Delete QhorusMcpTools — Test Migration Design

**Issue:** #452 (primary), #453
**Date:** 2026-09-21
**Status:** approved

## Context

Issue #451 migrated all 113 `@Tool` operations to 7 `@McpDomain` API classes. `QhorusMcpTools.java` (2092 lines) and `QhorusMcpToolsBase.java` (603 lines) were stripped of all MCP annotations and are now inert `@ApplicationScoped` CDI beans with zero MCP footprint.

87 test files (1200+ call sites) still inject `QhorusMcpTools` and call its convenience methods as test infrastructure. These must be migrated to direct service/store calls before the classes can be deleted.

Separately, `ComplianceReportResource` and `ComplianceScheduleResource` in the `compliance-report` module fail the APT generator's `@Path` check (#453) and need `@HandWrittenEndpoint` annotations.

## Design

### QhorusTestHelper

A new `@ApplicationScoped` CDI bean in `testing/src/main/java/io/casehub/qhorus/testing/QhorusTestHelper.java`.

The `testing/` module already depends on `casehub-qhorus` (runtime) — no new dependency needed and no cycle concern. All modules with tests already depend on `casehub-qhorus-testing`.

**Injected dependencies:**

- `ChannelManager` — channel creation
- `ChannelStore` — name→UUID resolution for dispatch
- `MessageDispatcher` — message sending
- `MessageStore` — message queries (replaces `checkMessages`)
- `InstanceStore` — instance registration
- `DataStore` — artefact sharing
- `WatchdogStore` — watchdog registration
- `CommitmentStore` — commitment queries (used by some tests)

**Method overloads — same names as QhorusMcpTools for mechanical migration:**

#### Channel creation

```java
// Simple — APPEND, no constraints
Channel createChannel(String name)

// With semantic
Channel createChannel(String name, ChannelSemantic semantic)

// BARRIER with contributors
Channel createChannel(String name, ChannelSemantic semantic, List<String> contributors)

// Full control — passthrough to ChannelManager
Channel createChannel(ChannelCreateRequest request)
```

All overloads return `Channel` (the entity). Tests currently receiving a string channel name from `tools.createChannel(...)` will instead receive the `Channel` object and use `channel.id` or `channel.name` as needed.

#### Message sending

```java
// Simple send
DispatchResult sendMessage(String channelName, String sender, String type, String content)

// With correlation
DispatchResult sendMessage(String channelName, String sender, String type, String content,
                           String correlationId)

// With correlation + inReplyTo (for terminal types)
DispatchResult sendMessage(String channelName, String sender, String type, String content,
                           String correlationId, Long inReplyTo)

// Full control — passthrough
DispatchResult dispatch(MessageDispatch dispatch)
```

The helper resolves `channelName` → `UUID` via `ChannelStore.findByName()` internally. `type` is a `String` that maps to `MessageType.valueOf()` — preserving the same call-site shape as QhorusMcpTools. `ActorType` defaults to `AGENT` (matching current test behavior).

#### Message queries

```java
// Simple query — replaces checkMessages(channel, afterId, limit, null, null, null)
List<MessageView> checkMessages(String channelName, Long afterId, int limit)

// Filtered query
List<MessageView> checkMessages(String channelName, Long afterId, int limit,
                                 MessageType type, String correlationId, String topic)
```

Returns `List<MessageView>` directly — no wrapper type. Tests migrate from `result.messages().get(0).content()` to `messages.get(0).content()`. `MessageView` has the same fields tests need: `content()`, `sender()`, `type()`, `correlationId()`, `topic()`, etc.

#### Instance registration

```java
Instance registerInstance(String instanceId, String description, String... capabilities)
```

#### Artefact sharing

```java
SharedData shareArtefact(String key, String description, String content, String createdBy)
```

#### Watchdog registration

```java
Watchdog registerWatchdog(String channelName, String conditionType, int thresholdCount,
                          String notificationChannel)
```

### Inner type replacement mapping

| QhorusMcpTools type | Replaced by | Module |
|---------------------|-------------|--------|
| `CheckResult` | `List<MessageView>` | api |
| `MessageSummary` | `MessageView` | api (via store) |
| `ArtefactDetail` | `SharedData` | runtime |
| `WatchdogSummary` | `Watchdog` | runtime |
| `ChannelInfo` (QhorusMcpToolsBase) | `ChannelDetail` | api |
| `LedgerEntryMap` | `MessageLedgerEntry` | runtime/ledger |

Tests referencing `QhorusMcpTools.CheckResult` or similar qualified names will change to use the domain type directly. Import statements change from `import ...mcp.QhorusMcpTools` to the appropriate domain type import.

### Compliance-report fix (#453)

Add `@HandWrittenEndpoint("reason")` to both resources:

- **`ComplianceReportResource`** — `@HandWrittenEndpoint("multipart upload, content negotiation, binary signature downloads")`. Uses `@RestForm FileUpload`, `Accept` header routing to format renderers, and `application/pkcs7-signature` binary responses that `@McpDomain` cannot express.

- **`ComplianceScheduleResource`** — `@HandWrittenEndpoint("CRUD resource paired with hand-written ComplianceReportResource")`. Standard CRUD but part of the same REST surface as the report resource. The MCP channel is already covered by `ComplianceApi @McpDomain("compliance")`.

### Migration ordering

1. **Create QhorusTestHelper** in `testing/` — the foundation all other steps depend on
2. **Fix #453** — add `@HandWrittenEndpoint` to compliance resources (independent, quick)
3. **Migrate runtime tests** — largest batch (~60+ files, ~1000 call sites). Work module-by-module within runtime: channel tests, message tests, watchdog tests, gateway tests, ledger tests, MCP domain tests
4. **Migrate other module tests** — connector-backend, slack-channel, websocket-observer, webhook-observer, compliance-report, a2a-push-notification, notification-bridge, examples
5. **Delete QhorusMcpTools.java and QhorusMcpToolsBase.java**
6. **Full build verification** — `mvn clean install` to confirm no remaining references

### Migration pattern per file

For each test file:

1. Replace `@Inject QhorusMcpTools tools` with `@Inject QhorusTestHelper helper`
2. Replace `tools.createChannel(name, desc, null, null, ...)` with `helper.createChannel(name)` (or appropriate overload)
3. Replace `tools.sendMessage(channel, sender, "command", content, null, ...)` with `helper.sendMessage(channel, sender, "command", content)`
4. Replace `tools.checkMessages(channel, 0L, 10, null, null, null)` with `helper.checkMessages(channel, 0L, 10)`
5. Replace `QhorusMcpTools.CheckResult` with `List<MessageView>` and adjust field access
6. Replace other inner type references with domain types
7. Remove `QhorusMcpTools` / `QhorusMcpToolsBase` imports, add domain type imports

### What does NOT change

- Test logic and assertions — only the call-site shape changes
- `@QuarkusTest`, `@TestTransaction`, `@TestProfile` annotations
- CDI wiring patterns (the helper is an `@ApplicationScoped` bean, same as QhorusMcpTools was)
- Test data setup patterns (create channel → register instance → send messages → assert)

### Risks

- **Behavioral divergence:** QhorusMcpTools methods had side effects (e.g., `sendMessage` called `tools.validate()` before dispatch — since removed in the dispatch consolidation). The helper delegates to the same services the MCP tools did, so behavior is equivalent.
- **Missing overloads:** Some test files may use QhorusMcpTools methods not listed above. The helper should cover all methods found in tests — if an uncommon method surfaces during migration, add an overload.
- **Compile-time breakage in profile-gated modules:** `examples/agent-communication/` is behind `-Pwith-llm-examples`. Run `mvn test-compile -Pwith-llm-examples -f examples/agent-communication/pom.xml` after migration to catch stale call sites.

## References

- Issue #452 body — migration scope and approach recommendation
- Issue #453 body — compliance-report build failure
- Issue #451 — @McpDomain migration (predecessor)
- `testing/src/main/java/io/casehub/qhorus/testing/` — existing test utilities (RecordingChannelBackend, MessageLedgerEntryTestFactory)
- `api/src/main/java/io/casehub/qhorus/api/channel/ChannelManager.java` — channel service interface
- `api/src/main/java/io/casehub/qhorus/api/message/MessageDispatcher.java` — dispatch interface
- `api/src/main/java/io/casehub/qhorus/api/store/` — store interfaces (MessageStore, ChannelStore, InstanceStore, DataStore, WatchdogStore, CommitmentStore)
