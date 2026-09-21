# Decisions — #452 Delete QhorusMcpTools

## D1: QhorusTestHelper location

**Choice:** `testing/` module, depending on API interfaces only (ChannelManager, MessageDispatcher, store interfaces)
**Alternatives:**
- `runtime/src/test/` — direct runtime access but only runtime tests can use it; other modules need their own approach
- No helper (inline only) — maximum explicitness but 488 createChannel sites each become 5-10 line builder chains
**Rationale:** All modules (runtime, connector-backend, slack-channel, websocket-observer, etc.) already depend on `testing/` for test utilities. API-only dependency avoids the runtime→testing cycle.
**Trade-offs:** Helper cannot use runtime-internal classes; must work through API interfaces only.
**Sources:** Issue #452 body (recommends helper approach), project structure (testing/ already holds RecordingChannelBackend, MessageLedgerEntryTestFactory)
**Exploration:** quick
**Status:** captured

## D2: Inner type migration strategy

**Choice:** Use native domain types — helper returns `Channel`, `MessageView`, `DispatchResult`, etc. Tests migrate from MCP DTOs (`CheckResult`, `MessageSummary`) to real domain types.
**Alternatives:**
- Replicate inner types in testing/ — lower per-site migration effort but perpetuates an unnecessary abstraction
- Promote to API module — but these were MCP-specific DTOs, not domain concepts
**Rationale:** Clean break from the MCP layer. Domain types are the real API surface; MCP DTOs were a presentation concern that tests shouldn't depend on.
**Trade-offs:** More migration effort per call site where CheckResult fields don't map 1:1 to MessageView fields.
**Sources:** QhorusMcpToolsBase inner types, api/channel/ChannelDetail, api/message/MessageResult, runtime/message/Message entity
**Exploration:** quick
**Status:** captured

## D3: QhorusTestHelper API style

**Choice:** Overloaded methods — `createChannel(name)` for simple case, `createChannel(name, semantic)` for more, full-args when needed. Same method names as QhorusMcpTools for easy find-replace migration.
**Alternatives:**
- Fluent builder per operation — maximally flexible but every call site changes shape significantly, harder to migrate mechanically
- Static factory methods — fewer overloads but loses CDI injection and requires different call patterns
**Rationale:** Same method names enable mechanical find-replace. Overloads with sensible defaults (APPEND semantic, null optional fields) eliminate the 19-null-arg pattern while keeping simple cases trivial.
**Trade-offs:** Many overloads can be confusing; need disciplined Javadoc on which overload to use when.
**Sources:** QhorusMcpTools method signatures, ChannelCreateRequest.builder() API
**Exploration:** quick
**Status:** captured

## D4: Compliance-report build failure (#453)

**Choice:** Add `@HandWrittenEndpoint` to both `ComplianceReportResource` and `ComplianceScheduleResource`
**Alternatives:**
- Migrate to @McpDomain — not feasible for ComplianceReportResource (multipart upload, content negotiation, binary .p7s downloads). ComplianceScheduleResource could technically migrate but the compliance module already has ComplianceApi @McpDomain("compliance") for its MCP surface.
**Rationale:** Both resources use JAX-RS features that @McpDomain cannot express. The MCP channel is already covered by ComplianceApi. Hand-written REST is the correct pattern for these endpoints.
**Trade-offs:** None significant — these endpoints are correctly hand-written.
**Sources:** ComplianceReportResource.java (multipart, Accept header routing), ComplianceScheduleResource.java (standard CRUD), ComplianceApi.java (@McpDomain already exists)
**Exploration:** quick
**Status:** captured
