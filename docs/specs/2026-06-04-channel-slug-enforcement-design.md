# Channel Slug Enforcement — Design Spec
**Issue:** qhorus#236  
**Date:** 2026-06-04  
**Status:** Approved (rev 4 — post-review)

---

## Problem

Channel names have no format constraint beyond DB uniqueness. A channel can be named `'Billing Output'`, `'xp9qr'`, or a UUID-shaped string. This undermines the dual-identity model (PP-20260604-dualid): the semantic slug is the human-readable stable identity — that guarantee is hollow if names can be arbitrary strings.

Additionally, `resolveChannel()` currently parses UUID-first. A channel named with a UUID-shaped string (e.g. `a81b4c6d-1234-5678-abcd-ef0123456789`) routes via UUID ID rather than by name, making name-based lookup silently unreachable. Roughly 37% of random UUIDs start with `a–f` and pass the segment pattern.

---

## Invariant

Every channel name is a `/`-delimited path where **every segment** matches:

```
[a-z][a-z0-9]*(-[a-z0-9]+)*
```

- Starts with a lowercase letter
- Hyphens are separators between alphanumeric groups — no leading, trailing, or consecutive hyphens
- Max segment length: 80 chars
- Max total name length: 200 chars
- Path separator: `/`
- UUID-shaped names are explicitly rejected (see §ChannelSlugValidator)

**Depth:** Path depth is intentionally unconstrained. The 200-char total is the practical bound. Typical patterns use 1–3 segments. Single-segment names like `billing` are valid.

This applies to **every segment** — including leaf segments of auto-created hierarchical names.

---

## `ChannelSlugValidator`

A new **`public final class`** `ChannelSlugValidator` in `runtime/channel/`. Public visibility serves two purposes: (1) `QhorusMcpToolsBase` (in `runtime/mcp`) can call `tryParseUuid()` directly, eliminating the duplicate copy currently in that class; (2) consumers can call `validateSlugPath()` to pre-validate a channel name before calling `create_channel`. Both are intentional exposures. Note: the `runtime` Maven module is on the consumer classpath — all public classes in it are accessible to consumers. Making `ChannelSlugValidator` public is a deliberate choice to expose it for pre-validation, not a side effect of module placement.

```java
public final class ChannelSlugValidator {

    public static final Pattern SEGMENT_PATTERN =
        Pattern.compile("[a-z][a-z0-9]*(-[a-z0-9]+)*");
    public static final int MAX_SEGMENT_LENGTH = 80;
    public static final int MAX_NAME_LENGTH = 200;

    /**
     * Validates that {@code name} is a well-formed channel slug path.
     * Throws {@link IllegalArgumentException} on any violation.
     */
    public static void validateSlugPath(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException(
                "Channel name must not be null or blank");
        }
        if (name.length() > MAX_NAME_LENGTH) {
            throw new IllegalArgumentException(
                "Channel name exceeds " + MAX_NAME_LENGTH + " chars: '" + name + "'");
        }
        // Reject UUID-shaped names — resolveChannel() uses tryParseUuid() for routing;
        // a UUID-shaped name makes name-based lookup silently unreachable.
        // Use a flag; throwing inside the try block would be caught by the same catch.
        boolean isUuid;
        try {
            UUID.fromString(name);
            isUuid = true;
        } catch (IllegalArgumentException ignored) {
            isUuid = false;
        }
        if (isUuid) {
            throw new IllegalArgumentException(
                "Channel name must not be UUID-shaped: '" + name + "'");
        }
        for (String segment : name.split("/", -1)) {
            if (segment.length() > MAX_SEGMENT_LENGTH) {
                throw new IllegalArgumentException(
                    "Segment '" + segment + "' exceeds " + MAX_SEGMENT_LENGTH +
                    " chars in channel name '" + name + "'");
            }
            if (!SEGMENT_PATTERN.matcher(segment).matches()) {
                throw new IllegalArgumentException(
                    "Invalid channel name segment '" + segment +
                    "' — must match [a-z][a-z0-9]*(-[a-z0-9]+)*. " +
                    "Full name: '" + name + "'");
            }
        }
    }

    /**
     * Returns true iff {@code segment} is a valid single slug segment.
     * Rejects UUID-shaped strings for consistency with {@link #validateSlugPath}.
     */
    public static boolean isValidSegment(String segment) {
        if (segment == null || segment.isBlank()
                || segment.length() > MAX_SEGMENT_LENGTH) {
            return false;
        }
        if (!SEGMENT_PATTERN.matcher(segment).matches()) {
            return false;
        }
        // Reject UUID-shaped segments — a UUID-shaped literal in a channelNamePattern
        // would pass this check but fail validateSlugPath() at first-contact time.
        try {
            UUID.fromString(segment);
            return false;
        } catch (IllegalArgumentException ignored) {
            return true;
        }
    }

    /** Returns the UUID if {@code s} parses as one, null otherwise. */
    public static UUID tryParseUuid(String s) {
        try {
            return UUID.fromString(s);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private ChannelSlugValidator() {}
}
```

`ChannelCreateRequest`'s compact constructor calls `ChannelSlugValidator.validateSlugPath(name)` as its first check, before connector binding and type validation. `QhorusMcpToolsBase` replaces its private `tryParseUuid()` with calls to `ChannelSlugValidator.tryParseUuid()`.

---

## `resolveChannel()` — Name-First Resolution

`QhorusMcpToolsBase.resolveChannel()` changes from UUID-first to name-first. The **return type stays `UUID`** — no cascade to the ~53 `@Tool` method callers. The method continues to use `channelService` (already injected in `QhorusMcpToolsBase`), not `channelStore` — no new injection required.

```java
UUID resolveChannel(final String channel) {
    UUID parsedUuid = ChannelSlugValidator.tryParseUuid(channel);
    if (parsedUuid == null) {
        // Not UUID-shaped — look up by name only.
        return channelService.findByName(channel)
            .orElseThrow(() -> new IllegalArgumentException(
                "Channel not found: " + channel))
            .id;
    }
    // UUID-shaped input — look up by ID.
    // The slug invariant blocks UUID-named channels, so UUID-shaped inputs
    // are always channel IDs, never channel names.
    return channelService.findById(parsedUuid)
        .orElseThrow(() -> new IllegalArgumentException(
            "Channel not found: " + channel))
        .id;
}
```

The UUID parse is now separated from the store lookup. The `orElseThrow` exception on the UUID-not-found path is no longer in a catch block that also handles parse failure — the two error paths are unambiguous.

**Reactive form** (`ReactiveQhorusMcpTools`):

The reactive `resolveChannel()` uses `reactiveChannelService`. The blocking `ChannelService.findByName()` returns `Optional<Channel>`; the reactive seam mirrors this as `Uni<Optional<Channel>>`. Verify the exact return type against `ReactiveChannelService` during implementation and use the appropriate unwrapping pattern. Expected form:

```java
Uni<UUID> resolveChannel(final String channel) {
    UUID parsedUuid = ChannelSlugValidator.tryParseUuid(channel);
    if (parsedUuid == null) {
        return reactiveChannelService.findByName(channel)
            .map(opt -> opt.orElseThrow(() -> new IllegalArgumentException(
                "Channel not found: " + channel)))
            .map(ch -> ch.id);
    }
    return reactiveChannelService.findById(parsedUuid)
        .map(opt -> opt.orElseThrow(() -> new IllegalArgumentException(
            "Channel not found: " + channel)))
        .map(ch -> ch.id);
}
```

If `ReactiveChannelService.findByName()` returns `Uni<Channel>` (null on miss) rather than `Uni<Optional<Channel>>`, replace `.map(opt -> opt.orElseThrow(...))` with `.onItem().ifNull().failWith(...)`.

---

## `ConfiguredAutoChannelPolicy` — Segment Sanitisation

Auto-created connector channels embed external identifiers (phone numbers, email addresses, external keys) in path segments. Two distinct sanitisation functions handle the two kinds of input.

### `slugifyConnectorId(String connectorId)` — hash-free normalisation

Connector IDs are developer-defined controlled strings, not arbitrary user input. They should be valid slugs by convention. `slugifyConnectorId()` normalises them without appending a hash:

1. Lowercase
2. Replace any run of `[^a-z0-9]` with a single `-`
3. Strip leading and trailing `-`
4. If result starts with a digit, prepend `id-`
5. Truncate to 80 chars; strip trailing `-`
6. If empty, throw `IllegalArgumentException`

For conformant connector IDs (`twilio-sms-inbound`), this returns the input unchanged — **existing auto-created channel names are preserved**.

**Formal contract:** connector IDs must be valid slug segments. Known IDs conform. Enforcement at connector registration is a follow-on concern. Two non-conformant connector IDs that slugify to the same output (e.g., `"My Connector"` and `"my_connector"` → both `my-connector`) will be indistinguishable in channel names — unlike `sanitiseSegment()`, no hash is appended to distinguish them. Connector IDs that require slugification should be made unique at source.

### `sanitiseSegment(String raw)` — hash-bearing normalisation for user-provided keys

`lookupKey` values are arbitrary external identifiers (phone numbers, email addresses). Hash is required to prevent silent merges when two different raw inputs produce the same sanitised prefix.

Algorithm:

1. Lowercase the input
2. Replace any run of `[^a-z0-9]` with a single `-`
3. Strip leading and trailing `-`
4. If result starts with a digit, prepend `id-`
5. Truncate to **71 chars**; strip trailing `-` (leaves 9 chars for the hash suffix: `-` + 8 hex)
6. If empty after step 5, throw `IllegalArgumentException`
7. Append `-` + first **8 hex chars** of SHA-256 of the **lowercased** input (the string after step 1)

The hash is computed on the lowercased form so that case variants of the same external identifier (e.g., `User@Example.COM` and `user@example.com`) produce the same channel name.

### Why always append the hash

Appended unconditionally — even when the raw input is already a valid slug. At sanitisation time it is impossible to know whether another distinct raw input produces the same sanitised prefix. Unconditional appending of the raw-key hash (32 bits, ~4.3 billion values) guarantees that every distinct raw key produces a distinct channel name. Birthday-paradox collision probability at 100,000 identifiers: < 0.1%.

6 hex chars (24 bits) would reach ~1% collision at 6,000 identifiers — unacceptable for contact-centre deployments. 8 hex chars provides negligible collision at realistic scales with only 2 additional chars.

### Examples

| Raw input | Function | Result |
|-----------|----------|--------|
| `twilio-sms-inbound` | `slugifyConnectorId` | `twilio-sms-inbound` (unchanged) |
| `My Connector` | `slugifyConnectorId` | `my-connector` |
| `+14155552671` | `sanitiseSegment` | `id-14155552671-3fa2b100` |
| `user@example.com` | `sanitiseSegment` | `user-example-com-9c4a7253` |
| `User@Example.COM` | `sanitiseSegment` | `user-example-com-9c4a7253` (same — hash of lowercased form) |
| `550e8400-e29b-41d4-a716-446655440000` | `sanitiseSegment` | `id-550e8400-e29b-41d4-a716-446655440000-7b2e9100` |

UUID example walkthrough: steps 1–3 leave the UUID unchanged (36 chars). Step 4: starts with `5` → `id-550e8400-e29b-41d4-a716-446655440000` (39 chars). Step 5: 39 ≤ 71, no truncation. Step 7: append `-7b2e9100` → 48 chars total. Full UUID preserved in the name.

### Application

- Default fallback: `"connector/" + slugifyConnectorId(connectorId) + "/" + sanitiseSegment(lookupKey)`
- Configured `channelNamePattern`: `{connectorId}` → `slugifyConnectorId()`; `{lookupKey}` → `sanitiseSegment()`

Custom `AutoChannelPolicy` implementations own their own naming. Invalid names throw at `ChannelCreateRequest` construction.

---

## Startup Validation of Configured Patterns

`ConfiguredAutoChannelPolicy` validates each configured `channelNamePattern` at `@PostConstruct`.

For each configured pattern:
1. Split on `/`
2. For each segment, substitute all `{...}` placeholders with `a`: `segment.replaceAll("\\{[^}]+}", "a")`
3. Validate the substituted string with `ChannelSlugValidator.isValidSegment()`
4. Throw `IllegalStateException` at startup if validation fails

This catches mixed-literal segments such as `Billing-{lookupKey}` (uppercase `B` detected after substituting `{lookupKey}` → `a`, yielding `Billing-a`). Pure-placeholder segments like `{lookupKey}` substitute to `a`, which passes — runtime sanitisation handles the actual value.

**Limitation:** startup validation does not catch segment-length overflow. A literal prefix long enough that the runtime sanitised value pushes the combined segment past 80 chars will pass startup and fail `validateSlugPath()` at first-contact time. This is acceptable — the failure is caught before any channel is created — but it is not caught early. Pattern authors should keep literal prefixes short.

---

## Immutability

Channel names are immutable after creation. `Channel.name` gets:

```java
@Column(nullable = false, updatable = false)
public String name;
```

`updatable = false` makes Hibernate enforce immutability — no `UPDATE` will be generated for this column. No `rename_channel` tool exists and none will be added without a migration strategy covering bindings, commitment references, and audit entries (PP-20260604-dualid).

---

## V17 Flyway Migration

```sql
ALTER TABLE channel
  ADD CONSTRAINT chk_channel_name_slug
  CHECK (name SIMILAR TO '[a-z][a-z0-9]*(-[a-z0-9]+)*(/[a-z][a-z0-9]*(-[a-z0-9]+)*)*'
         AND LENGTH(name) <= 200);
```

`SIMILAR TO` works in both PostgreSQL and H2 `MODE=PostgreSQL`. Java validation is the primary enforcement gate; the DB constraint is a backstop.

**Known gap:** `SIMILAR TO` cannot express `{0,79}` quantifiers, so per-segment max length (80 chars) is enforced by Java only. The DB constraint guarantees character-class shape and total name length. This is documented and accepted — a raw SQL insert could violate per-segment length while satisfying the DB constraint.

The constraint is prospective. Test infra uses `drop-and-create`. H2 `SIMILAR TO` parity must be verified during `FlywayMigrationSchemaTest` execution.

---

## MCP Tool Update

`create_channel` tool — `name` argument description updated to:

> Unique channel name. Each `/`-delimited segment must match `[a-z][a-z0-9]*(-[a-z0-9]+)*` — lowercase letters and digits, hyphens only between alphanumeric groups. No leading, trailing, or consecutive hyphens. Max 80 chars per segment, 200 chars total. UUID-shaped names are rejected. Examples: `"billing-output"`, `"case-abc/work"`.

**Error surface:** `@WrapBusinessError` wraps `IllegalArgumentException` from `validateSlugPath()` into `ToolCallException`. Verify during TDD that the cause message surfaces to MCP tool callers with enough detail to self-correct.

---

## Test Impact

Existing test channel names pass the pattern:
- `case-abc/work`, `case-abc/observe`, `case-abc/oversight` — ✅
- `barrier-test-<uuid>` — UUID chars `[0-9a-f-]` ⊂ `[a-z0-9-]`; groups separated by hyphens match `(-[a-z0-9]+)*` ✅
- `server-enforce-<nanotime>`, `del-tool-empty-<nanotime>` — digit suffixes after letter prefix ✅
- `my-channel`, `no-binding-ch` — ✅

**Tests requiring update — both segments change:**

`ChannelServiceFindOrCreateTest` asserts:
```
connector/twilio-sms-inbound/+14155552671
```
After this change (connector segment via `slugifyConnectorId` — unchanged; lookup key via `sanitiseSegment`):
```
connector/twilio-sms-inbound/id-14155552671-<8-hex-hash>
```

The connector segment (`twilio-sms-inbound`) is unchanged by `slugifyConnectorId()` because it is already a valid slug. Only the phone key segment changes. Tests that update only the phone key segment are correct; tests that update the connector segment too are wrong. Both facts must be verified explicitly during implementation.

Also update: connector-backend tests constructing phone-keyed `AutoChannelSpec` names. Audit any test assigning `ch.name` directly to a non-conformant value.

---

## Platform Coherence

- **PP-20260604-dualid** — names immutable ✅; UUID-exclusion closes the UUID-shape collision gap stated in that protocol
- **PP-20260604-c19f7c** — validation delegated through `ChannelCreateRequest` compact constructor ✅
- **Flyway V17** — next available in the qhorus domain range ✅
- **No SPI changes** — `AutoChannelPolicy` contract unchanged; validation is downstream
- **No cross-repo impact** — `ChannelCreateRequest` is in `runtime`; consumers see `IllegalArgumentException` on invalid names, same as today for type violations
- **`resolveChannel()` return type** — stays `UUID`; no cascade to ~53 `@Tool` callers
- **No injection change** — `resolveChannel()` continues using `channelService`; no new `ChannelStore` injection in `QhorusMcpToolsBase`
