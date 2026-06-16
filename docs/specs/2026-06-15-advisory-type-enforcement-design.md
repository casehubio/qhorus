# Advisory Channel Type Enforcement

**Date:** 2026-06-15  
**Issue:** casehubio/qhorus#271  
**Branch:** issue-266-consumer-tenancyid-migration

---

## Context and Motivation

`allowedTypes` and `deniedTypes` on Qhorus channels currently hard-enforce through
`StoredMessageTypePolicy.validate()`, which throws `MessageTypeViolationException` on any
violation. Protocol PP-20260604-a7ad99 declared them "hard enforcement gates, not documentation
labels."

First-principles analysis against the normative model reveals a more precise framing.

**The obligation-creating distinction is load-bearing.** `MessageService.dispatch()` calls
`commitmentService.open()` for exactly two types: COMMAND and QUERY. When an LLM agent
dispatches COMMAND to the wrong channel (e.g. observe instead of work) and receives an advisory,
it corrects itself by dispatching to the right channel — using the same or a new correlationId.
Under full advisory enforcement, this creates either:
- a duplicate OPEN Commitment (same correlationId, two channels) — resolution of either
  Commitment leaves the other stalled permanently, polluting `list_stalled_obligations` with
  a phantom; or
- an orphaned OPEN Commitment (new correlationId on wrong channel) — stalled with no agent
  to fulfill it and no mechanism to distinguish it from a genuine governance failure.

For COMMAND and QUERY, advisory dispatch is therefore architecturally unsound — hard
enforcement is correct normatively: a directive that cannot be honoured on a channel where no
agent will respond is not a valid speech act.

**For all other types, advisory is correct.** STATUS, EVENT, DONE, FAILURE, DECLINE, RESPONSE,
and HANDOFF do not call `commitmentService.open()` — they update or close existing commitments
(via correlationId) or create no normative record at all (EVENT). Advisory dispatch for these
types produces an accurate audit entry without orphan risk. Hard enforcement for non-
obligation-creating types erases constraint violations from the ledger — advisory enforcement
makes them visible and attributable.

**HANDOFF is safe under advisory enforcement.** `CommitmentService.delegate()` creates the
child Commitment with `child.channelId = c.channelId` where `c` is the parent Commitment
found by correlationId lookup — not the HANDOFF message's channel. A HANDOFF dispatched to
the wrong channel (e.g. observe) finds the parent COMMAND Commitment on `work`, marks it
DELEGATED, and creates the child with `channelId = work`. If no parent exists for the
correlationId, `delegate()` returns `Optional.empty()` and no child is created. In neither
case does the HANDOFF's own channel affect child Commitment placement. HANDOFF does not
belong in the hard-enforcement group.

**Conclusion:** enforce COMMAND and QUERY hard; treat all others as advisory.

---

## Architecture Overview

### What changes

| Component | Change |
|---|---|
| `MessageTypePolicy` | New default method `advisory(Channel, MessageType) → String`; `validate()` SAM unchanged |
| `StoredMessageTypePolicy` | `validate()` → hard-enforce COMMAND/QUERY only; `advisory()` → violation text for all other types |
| `DispatchResult` | New `@JsonInclude(NON_EMPTY) List<String> advisories` field |
| `MessageService.dispatch()` | Calls `validate()` (hard gate) then `advisory()` (WARN + advisories list) |
| `ReactiveMessageService.dispatch()` | Same; advisory captured via `AtomicReference` (see below) |
| `QhorusMcpTools.sendMessage()` | Remove pre-dispatch `validate()` call — `MessageService` is the single gate; expose `advisories` from `DispatchResult` in response map |
| `ReactiveQhorusMcpTools` | Same |
| `@Tool` description for `set_channel_type_constraints` | Update to reflect hybrid enforcement semantics |
| `CLAUDE.md` testing conventions | Two stale notes need updating (see Required Updates) |

### What stays unchanged

- `MessageTypeViolationException` — retained in `api/`; still thrown for COMMAND/QUERY violations.
- `MessageTypePolicy` — remains `@FunctionalInterface`; `validate()` is still the SAM.
- `allowedTypes`/`deniedTypes` on `Channel` — unchanged data model.
- `ChannelCreateRequest` compact constructor — `allowedTypes ∩ deniedTypes = ∅` enforced at
  creation (PP-20260604-c19f7c unaffected).

---

## `MessageTypePolicy` SPI Extension

Add a default `advisory()` method alongside the existing SAM. Existing custom implementations
that override only `validate()` are unchanged.

```java
@FunctionalInterface
public interface MessageTypePolicy {

    /**
     * Hard-block gate. Throw {@link MessageTypeViolationException} to reject; return normally
     * to permit. {@link StoredMessageTypePolicy} hard-enforces only COMMAND and QUERY
     * violations — these are the only types that call {@code commitmentService.open()};
     * advisory dispatch on the wrong channel creates orphan Commitments when the LLM corrects.
     *
     * <p>Custom policies that need hard enforcement for additional types override this method.
     */
    void validate(Channel channel, MessageType type);

    /**
     * Advisory evaluation. Returns a human-readable violation description when the type
     * violates the channel's declared constraints and the type is not COMMAND or QUERY
     * (those are hard-enforced by {@link #validate}). Returns {@code null} when permitted
     * or when the type is obligation-creating.
     *
     * <p>Never throws for well-formed channel configurations. For malformed
     * {@code allowedTypes}/{@code deniedTypes} values (unknown type names), propagates
     * {@link IllegalArgumentException} from {@link MessageType#parseTypes} — an impossible
     * condition in production since {@code ChannelCreateRequest} validates at creation time.
     *
     * <p><strong>Calling contract for custom implementations:</strong> when a custom policy
     * provides only {@code validate()} and leaves {@code advisory()} as the default null,
     * the calling sequence is: {@code validate()} → may throw →
     * {@code advisory()} → null (no advisory logged, because advisory() is called only
     * after validate() returns normally; if validate() throws, advisory() is never invoked).
     * This is the correct hard-enforcement-only mode.
     *
     * <p>Default: {@code null} — no advisory; defers entirely to {@code validate()}.
     */
    default String advisory(Channel channel, MessageType type) {
        return null;
    }
}
```

**Calling sequence in `MessageService.dispatch()`:**

1. `validate()` — hard gate; throws `MessageTypeViolationException` for COMMAND/QUERY
   violations; no-op for all others
2. `advisory()` — computes advisory text for non-COMMAND/QUERY violations; null for COMMAND/QUERY
   (they cannot reach this step without passing `validate()`)

---

## `StoredMessageTypePolicy` Changes

```java
@ApplicationScoped
public class StoredMessageTypePolicy implements MessageTypePolicy {

    /**
     * Hard-enforces COMMAND and QUERY only — both call commitmentService.open(); advisory
     * dispatch on the wrong channel creates orphan Commitments. No-op for all other types.
     */
    @Override
    public void validate(Channel channel, MessageType type) {
        if (type != MessageType.COMMAND && type != MessageType.QUERY) return;
        // Denial-first: explicit denial wins over allowedTypes
        if (channel.deniedTypes != null && !channel.deniedTypes.isBlank()) {
            if (MessageType.parseTypes(channel.deniedTypes).contains(type)) {
                throw MessageTypeViolationException.denied(channel.name, type, channel.deniedTypes);
            }
        }
        // Open channel (no allowedTypes restriction) passes after denial check
        if (channel.allowedTypes == null || channel.allowedTypes.isBlank()) return;
        if (!MessageType.parseTypes(channel.allowedTypes).contains(type)) {
            throw new MessageTypeViolationException(channel.name, type, channel.allowedTypes);
        }
    }

    /**
     * Advisory for non-obligation-creating types only. Returns null for COMMAND and QUERY
     * (those are hard-enforced by validate()). Denial-first: denial wins over allowedTypes.
     */
    @Override
    public String advisory(Channel channel, MessageType type) {
        if (type == MessageType.COMMAND || type == MessageType.QUERY) return null;
        if (channel.deniedTypes != null && !channel.deniedTypes.isBlank()) {
            if (MessageType.parseTypes(channel.deniedTypes).contains(type)) {
                return "Type advisory: channel '" + channel.name
                        + "' explicitly denies " + type
                        + " — denied: [" + channel.deniedTypes + "]. Message dispatched.";
            }
        }
        if (channel.allowedTypes == null || channel.allowedTypes.isBlank()) return null;
        if (!MessageType.parseTypes(channel.allowedTypes).contains(type)) {
            return "Type advisory: channel '" + channel.name
                    + "' allows [" + channel.allowedTypes + "] only, received " + type
                    + ". Message dispatched.";
        }
        return null;
    }
}
```

---

## `DispatchResult` Change

Add one field. `@JsonInclude(NON_EMPTY)` means it is absent from JSON when empty — backward
compatible for all existing JSON consumers. The compact constructor null-guards both list fields.

```java
public record DispatchResult(
        Long messageId,
        UUID channelId,
        String sender,
        MessageType type,
        String correlationId,
        Long inReplyTo,
        @JsonInclude(JsonInclude.Include.NON_EMPTY) List<UUID> artefactRefs,
        String target,
        UUID ledgerEntryId,
        UUID subjectId,
        UUID causedByEntryId,
        int parentReplyCount,
        @JsonInclude(JsonInclude.Include.NON_EMPTY) List<String> advisories
) {
    public DispatchResult {
        artefactRefs = artefactRefs == null ? List.of() : List.copyOf(artefactRefs);
        advisories   = advisories   == null ? List.of() : List.copyOf(advisories);
    }
}
```

### All direct `new DispatchResult(...)` construction sites

Every site gains a `List.of()` final argument, except the two in `MessageService` where the
`advisories` local is threaded through (including the LAST_WRITE early return — see below).

| File | Line | Action |
|---|---|---|
| `MessageService.java` | ~227 (LAST_WRITE) | Pass `advisories` local (populated before this branch) |
| `MessageService.java` | ~331 (normal) | Pass `advisories` local |
| `ReactiveMessageService.java` | ~250 (normal) | Pass `advisoriesRef.get()` (see reactive section) |
| `ReactiveMessageService.java` | ~337 (LAST_WRITE/overwrite) | Pass `advisoriesRef.get()` |
| `QhorusDashboardServiceTest.java` | 251 | Add `List.of()` |
| `ConnectorQhorusMeshBridgeTest.java` | 224 | Add `List.of()` |

---

## `MessageService.dispatch()` Change

`MessageService` has no Logger field. Add one as part of this change:

```java
import org.jboss.logging.Logger;
// in class body:
private static final Logger LOG = Logger.getLogger(MessageService.class);
```

`ReactiveMessageService` already has a Logger at line 71 — no change needed there.

Replace the current type-policy block (validate at line ~191) with:

```java
// ── Type policy ───────────────────────────────────────────────────────────
List<String> advisories = List.of();
if (ch != null) {
    // Hard gate: throws MessageTypeViolationException for COMMAND/QUERY violations.
    // No-op for all other types — they cannot create orphan Commitments.
    messageTypePolicy.validate(ch, dispatch.type());
    // Advisory: logs warning for non-COMMAND/QUERY violations; null for COMMAND/QUERY.
    final String adv = messageTypePolicy.advisory(ch, dispatch.type());
    if (adv != null) {
        LOG.warn(adv);
        advisories = List.of(adv);
    }
}
```

**LAST_WRITE early return (line ~227):** the type-policy block runs BEFORE the LAST_WRITE
branch. The `advisories` local is already populated when the LAST_WRITE path exits. Pass
`advisories` (not `List.of()`) to the LAST_WRITE `DispatchResult` construction:

```java
return new DispatchResult(last.id, ch.id, last.sender,
        last.messageType, last.correlationId, last.inReplyTo,
        ArtefactRefParser.parse(last.artefactRefs), last.target,
        null, null, null, 0, advisories);  // ← advisories from type-policy step
```

---

## `ReactiveMessageService.dispatch()` Change

The reactive chain uses lambda boundaries that prevent capturing mutable locals across steps.
Use `AtomicReference` declared outside the chain to capture the advisory from within the
`.invoke()` step where the channel is available:

```java
final AtomicReference<List<String>> advisoriesRef = new AtomicReference<>(List.of());

// Inside the Uni chain, at the type-policy step (where ch is available):
.invoke(ch -> {
    messageTypePolicy.validate(ch, dispatch.type()); // hard gate; may throw
    final String adv = messageTypePolicy.advisory(ch, dispatch.type());
    if (adv != null) {
        LOG.warn(adv);
        advisoriesRef.set(List.of(adv));
    }
})
// Later, when constructing DispatchResult:
.map(outcome -> new DispatchResult(..., advisoriesRef.get()))
```

The `advisoriesRef` is read-once at `DispatchResult` construction. `AtomicReference` ensures
cross-thread visibility — `ReactiveMessageService` uses `runSubscriptionOn(Infrastructure
.getDefaultWorkerPool())` for the trust-gate check, so the chain does not execute on a single
thread. Mutiny's sequential composition guarantees happens-before between the `.invoke()` step
that writes `advisoriesRef` and the `.map()` step that reads it.

The LAST_WRITE/overwrite path in `ReactiveMessageService` also reads `advisoriesRef.get()`.

---

## `QhorusMcpTools` and `ReactiveQhorusMcpTools`

Remove the pre-dispatch `messageTypePolicy.validate()` call from `sendMessage()` in both
classes. `MessageService` is now the single enforcement point.

**`QhorusMcpTools.sendMessage()`** — remove lines 570-571:
```java
// Type policy — client-side early rejection (MessageService.dispatch() enforces server-side)
messageTypePolicy.validate(ch, msgType);
```

**`ReactiveQhorusMcpTools.sendMessage()`** — remove lines 750-751 (identical comment + call).

`sendMessage()` returns `DispatchResult` directly (no intermediate response map). The
`advisories` field is already annotated `@JsonInclude(NON_EMPTY)` on `DispatchResult` — when
non-empty it appears in the serialized result automatically; no additional code needed.

For COMMAND/QUERY violations: `MessageTypeViolationException` (extends `IllegalStateException`)
propagates from `messageService.dispatch()` through `sendMessage()`, is wrapped by
`@WrapBusinessError` into `ToolCallException` — the LLM sees a hard error, same as before.

For non-COMMAND/QUERY advisory violations: `dispatch()` succeeds, `DispatchResult.advisories()`
is non-empty, the advisory string appears in the serialized MCP response. No retry required.

---

## `@Tool` Description Update

`QhorusMcpTools` line 323 and `ReactiveQhorusMcpTools` line 312 both embed the phrase
"Denial wins at dispatch time" and reference PP-20260604-a7ad99. Update to reflect hybrid
semantics:

```
Constraint enforcement is type-discriminated. COMMAND and QUERY are hard-enforced: a violation
throws and the message is not dispatched (these types create Commitments; wrong-channel dispatch
creates orphan obligations). All other types are advisory: a violation warning is returned in
DispatchResult.advisories and the message is dispatched. Denial wins over allowedTypes when both
are set. Constraints are prospective only — messages already in the channel are unaffected.
```

Remove the PP-20260604-a7ad99 protocol reference from the tool description (the protocol itself
will be updated separately).

---

## Testing

### `StoredMessageTypePolicyTest`

Type-by-type outcome for the existing tests under hybrid enforcement:

| Existing test | Dispatched type | Outcome |
|---|---|---|
| `singleType_rejectsOtherType()` — QUERY on EVENT-only | QUERY (obligation-creating) | **Passes unchanged** — `validate()` still throws |
| `multipleTypes_rejectsUnlisted()` — EVENT on QUERY+COMMAND+RESPONSE | EVENT (not obligation-creating) | **Flip** — `validate()` no-op; assert `advisory()` returns non-null |
| `deniedType_onOpenChannel_isRejected()` — EVENT denied | EVENT (not obligation-creating) | **Flip** — assert `advisory()` returns text containing "denies" |
| `deniedType_exceptionMessageIndicatesDenial()` | EVENT (not obligation-creating) | **Flip** — same advisory assertion |
| `violationMessage_containsChannelNameAndTypes()` — QUERY on EVENT-only | QUERY (obligation-creating) | **Passes unchanged** — `validate()` still throws |
| `unknownTypeName_throwsIllegalArgument()` — EVENT on `allowedTypes="RUBBISH"` | EVENT (not obligation-creating) | **Update** — `validate()` is a no-op for EVENT; `advisory()` calls `parseTypes("RUBBISH")` which throws IAE. Rewrite to call `advisory()` directly: `assertThrows(IAE, () -> policy.advisory(ch, EVENT))` |
| `nullDeniedTypes_hasNoEffect()` — `allowedTypes="QUERY"`, `deniedTypes=null`; asserts QUERY passes AND assertThrows for EVENT | EVENT (second assertion) | **Flip (second assertion only)** — `validate()` no-op for EVENT. `assertThrows` → `assertDoesNotThrow` + `assertNotNull(policy.advisory(ch, EVENT))` |
| All `nullAllowedTypes_*`, `blankAllowedTypes_*`, `openChannel_*`, `whitespace_*`, `blankDenied_*` | Various | **Pass unchanged** — no violation |

**New tests for hybrid enforcement:**

```java
void validate_command_onEventOnlyChannel_throws()
void validate_query_onEventOnlyChannel_throws()
void validate_status_onEventOnlyChannel_doesNotThrow()  // STATUS not obligation-creating
void validate_event_onDeniedChannel_doesNotThrow()       // EVENT not obligation-creating
void advisory_commandOnEventOnlyChannel_returnsNull()    // COMMAND → hard-enforce, no advisory
void advisory_statusOnEventOnlyChannel_returnsText()     // STATUS → advisory
void advisory_eventOnDeniedChannel_returnsText()         // EVENT denied → advisory
void advisory_commandOnOpenChannel_returnsNull()         // no violation
```

### `NormativeLayoutTypeEnforcementTest` (examples/normative-layout/)

| Test | Outcome after change | Action |
|---|---|---|
| `observeChannel_rejectsQuery_serverSide()` | PASSES — QUERY is obligation-creating, hard-enforce unchanged | None |
| `observeChannel_rejectsCommand_serverSide()` | PASSES — COMMAND is obligation-creating, hard-enforce unchanged | None |
| `oversightChannel_rejectsEvent_serverSide()` | FAILS — EVENT not obligation-creating; advisory only | Flip: assert dispatch succeeds + advisories non-empty |
| `violationException_messageContainsChannelNameAndType()` | FAILS — STATUS on observe is advisory only | Flip: dispatch succeeds; `DispatchResult.advisories()` is non-empty and the single advisory string contains the observe channel name and "STATUS" |
| All `permits*` and `workChannel_permitsAllNineTypes()` tests | PASS unchanged | None |

### `MessageServiceTypeEnforcementTest` (runtime/src/test/.../message/)

| Test | Outcome after change | Action |
|---|---|---|
| `serverSide_rejectsDisallowedType_bypassingMcpTool()` — QUERY on EVENT-only | PASSES — QUERY hard-enforced | None |
| `serverSide_violation_messageContainsChannelAndType()` — EVENT on QUERY+COMMAND channel | FAILS — EVENT is advisory only | Flip: dispatch succeeds + advisories contain channel name and "EVENT" |
| `serverSide_permitsAllowedType()` | PASSES | None |
| `serverSide_permitsAllTypes_whenConstraintIsNull()` | PASSES | None |
| `serverSide_multiTypeConstraint_permitsAllListed()` | PASSES | None |

### `ChannelServiceTest` (runtime/src/test/.../channel/)

| Test | Outcome | Action |
|---|---|---|
| `dispatch_deniedType_throwsViolation()` — EVENT dispatched to channel with `deniedTypes={EVENT}` | **Fails** — EVENT not obligation-creating, `validate()` no-op | Flip: dispatch succeeds + `DispatchResult.advisories()` non-empty |

### `NormativeLayoutRobustnessTest` (examples/normative-layout/)

| Test | Outcome | Action |
|---|---|---|
| `directMessageService_bypassingMcpTool_serverSideStillEnforces()` — COMMAND on observe | **Passes unchanged** — COMMAND hard-enforced | None |
| `allowedTypes_enforcedCorrectly()` — QUERY on allowedTypes={EVENT,STATUS}; EVENT and STATUS permitted | **Passes unchanged** — QUERY hard-enforced; EVENT/STATUS no violation | None |

### `DeniedTypesMcpTest` (runtime/src/test/.../message/)

| Test | Outcome | Action |
|---|---|---|
| `sendMessage_deniedType_throwsToolCallException()` — EVENT via MCP tool to `deniedTypes=EVENT` channel | **Fails** — EVENT advisory-only; no `ToolCallException` thrown | Flip: dispatch succeeds + `DispatchResult.advisories()` non-empty + advisory contains "denies" and "EVENT" |
| All other tests (`createChannel_*`, `sendMessage_nonDeniedType_passesOnDeniedChannel`) | **Pass unchanged** | None |

### `ChannelAllowedTypesTest` (runtime/src/test/.../mcp/)

| Test | Outcome | Action |
|---|---|---|
| `sendMessage_rejectsDisallowedType_clientSide()` — QUERY on EVENT-only via MCP | **Passes** — QUERY hard-enforced by MessageService (still throws) | Rename test to `sendMessage_rejectsDisallowedType_serverSide()` — the validate() call is now server-side only |
| `violationError_mentionsChannelAndType()` — EVENT on QUERY+COMMAND channel via MCP | **Fails** — EVENT advisory-only; dispatch succeeds | Flip: dispatch succeeds + `DispatchResult.advisories()` non-empty + advisory contains "EVENT" |
| All other tests | **Pass unchanged** | None |

### Other integration tests

All `MessageTypeViolationException` test usages have been enumerated in the tables above;
no additional flip sites remain.

### `ToolOverloadDiscoverabilityTest`

No changes expected — no `@Tool` method overloads are introduced.

---

## Required Updates

### Protocol update (same session)

`channel-type-policy-invariant.md` (PP-20260604-a7ad99):

- **Old framing:** "hard enforcement gates, not documentation labels."
- **New framing:** Type enforcement is discriminated by normative weight. COMMAND and QUERY are
  hard-enforced (both create Commitments; wrong-channel advisory dispatch causes orphan
  obligations). All other types are advisory (no Commitment created; violations produce an
  accurate audit record without orphan risk). `allowedTypes`/`deniedTypes` remain valid channel
  configuration — they declare intent and drive enforcement for the appropriate type category.
- The `observe`/`oversight`/`work` examples remain valid. EVENT on oversight is advisory (EVENTs
  are already excluded from `pollAfter` defaults; the advisory record is more informative than
  a hard block). COMMAND/QUERY on observe is hard-enforced unchanged.

### CLAUDE.md testing conventions (two stale notes)

1. *"MessageTypePolicy is injected into both QhorusMcpTools.sendMessage() (client-side early
   rejection) and MessageService.dispatch() (server-side enforcement)."* — After this change,
   the injection in QhorusMcpTools remains but the `validate()` call is removed. Update to:
   "MessageService.dispatch() is the single enforcement point."

2. *"StoredMessageTypePolicy.validate() runs denial-first: if channel.deniedTypes contains the
   type, MessageTypeViolationException.denied() is thrown."* — True only for COMMAND/QUERY.
   Update to reflect hybrid enforcement.

### ADR (same session)

Document the decision: "Why channel type enforcement is type-discriminated." Rationale:
obligation-creating types (COMMAND, QUERY) create Commitments; advisory dispatch on wrong
channel followed by LLM correction creates orphan Commitments. Hard enforcement is normatively
correct for these types. All other types are advisory — no Commitment is created, violations
are visible in the audit record, and `DispatchResult.advisories` surfaces them to callers.

---

## Out of Scope

- Ledger entry recording of advisory violations — deferred (casehub-ledger#126 track)
- Making `MessageTypePolicy` a consumer-facing SPI in `api/spi/` — the interface takes `Channel`
  (a JPA entity); moving it requires a context record abstraction. Track as follow-on if
  consumer policy customisation becomes a use case.

## wait_for_reply Timeout for Wrong-Channel Terminal Types — Known Limitation

`wait_for_reply` polls `findDoneByCorrelationId(channelId, correlationId)` and
`findResponseByCorrelationId(channelId, correlationId)`, both scoped by channel ID. Under
advisory enforcement, if an LLM dispatches DONE or RESPONSE to the wrong channel:

1. `CommitmentService.fulfill(correlationId)` resolves the Commitment correctly (by correlationId,
   channel-agnostic)
2. The DONE/RESPONSE message is recorded in the ledger on the wrong channel
3. Any caller doing `wait_for_reply` on the correct channel polls by (channelId, correlationId)
   — finds nothing — times out

The Commitment IS correctly resolved. The timeout is a caller-side observation gap.

**Why this stays advisory:** DONE/RESPONSE do not call `commitmentService.open()` — there is no
orphan Commitment risk. Hard-enforcing these types would reinstate the audit trail erasure problem
the design is solving.

**Self-correcting retry path:** the `DispatchResult.advisories()` field signals the mistake to
the DONE-sender immediately. If the sender retries DONE on the correct channel:
1. `CommitmentService.fulfill(correlationId)` is idempotent — `transition()` has a
   `filter(c -> !c.state.isTerminal())` guard, so a second `fulfill()` on an already-FULFILLED
   Commitment is a silent no-op.
2. The DONE message now exists on the correct channel.
3. `wait_for_reply` on the correct channel finds it and returns successfully.

The design is self-correcting through the advisory signal: the DONE-sender is told where the
mistake happened; retrying on the right channel resolves the `wait_for_reply` timeout without
any Commitment state corruption. The advisory goes to the DONE-sender; the orchestrator doing
`wait_for_reply` is the one that times out — these are distinct agents, and the advisory path
is the correct one for signalling the sender to correct itself.

**Scenario frequency:** requires the LLM to send DONE/RESPONSE to a constrained wrong channel
AND a caller simultaneously polling `wait_for_reply` on the correct channel. Advisory channels
are configured by operators; the normative layout channels are named clearly. The scenario is
observable (advisory in dispatch result + WARN log + ledger entry on wrong channel) rather than
silent.

---

## A2A Advisory Visibility — Deliberate Gap

`A2AResource` and `ReactiveA2AResource` map `DispatchResult` to A2A task state strings — they
do not include `DispatchResult.advisories()` in the A2A response. An A2A consumer that sends a
non-COMMAND/QUERY type to a constrained channel will receive a successful task response with no
advisory signal.

This is a deliberate non-decision: A2A protocol semantics differ from the MCP tool surface, and
A2A consumers are not expected to act on Qhorus-internal channel configuration advisories.
Advisory violations on A2A channels are still visible in the normative ledger (the WARN log and
the ledger entry exist) but the A2A response does not surface them. If a future use case requires
A2A advisory surfacing, add an extension attribute (A2A allows custom metadata fields) in a
separate issue.
