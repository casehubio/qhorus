# 0010 — Watchdog alert event payload: sealed hierarchy over open map

Date: 2026-05-27
Status: Accepted

## Context and Problem Statement

`WatchdogAlertEvent` must carry condition-specific diagnostic data to observers (missing
barrier contributors, pending approval counts, stale agent IDs, etc.). The five watchdog
condition types each have a different set of relevant fields. A representation must balance
type safety, observer usability, and evolution cost.

## Decision Drivers

* Observers (ConnectorAlertBridge) need structured data to compose meaningful notifications
* Misspelled or missing keys in an open map are silent runtime failures with no compiler signal
* The set of condition types is closed (5 variants, same as the existing `conditionType` string)
* Pattern-matching switches on the context type must be exhaustive — new condition types must
  not silently fall through

## Considered Options

* **Option A** — `Map<String, String>` with documented key contracts per condition type
* **Option B** — Sealed `AlertContext` interface permitting 5 typed records
* **Option C** — Single flat `AlertContext` record with all possible fields (nullable for inapplicable conditions)

## Decision Outcome

Chosen option: **Option B — sealed `AlertContext` hierarchy**, because the condition set is
closed and stable, making sealed types the correct Java 21 construct. Misspelled keys and
missing required fields become compile-time errors. Exhaustive switch arms on the context type
are compiler-enforced — new condition types added without a corresponding record cause a
compile failure immediately.

### Positive Consequences

* Compiler enforces completeness: no silent miss when a new condition type is added
* Observer code (buildBody switch in ConnectorAlertBridge) is exhaustive with no default arm
* Each context record is self-documenting — fields are named, typed, and inspectable by IDEs

### Negative Consequences / Tradeoffs

* 6 new types in `casehub-qhorus-api` (the sealed interface + 5 records)
* Adding a new condition type requires updating all exhaustive switches across all consumers

## Pros and Cons of the Options

### Option A — `Map<String, String>`

* ✅ Zero new types; easy to extend without consumer changes
* ❌ Misspelled keys are silent runtime failures
* ❌ Missing required keys undetectable at compile time
* ❌ Observers must read documentation to know what keys to expect

### Option B — Sealed `AlertContext` hierarchy

* ✅ Compile-time safety on field names and types
* ✅ Exhaustive switch arms enforced by the compiler
* ✅ IDE discoverability and autocomplete for observers
* ❌ New condition type requires updating all switches (acceptable — closed set)

### Option C — Single flat record

* ✅ One type; no sealed hierarchy needed
* ❌ All inapplicable fields are null — no compile-time signal that they should be absent
* ❌ Observers cannot pattern-match by condition type cleanly

## Links

* casehubio/qhorus#200 — implementation issue
* `api/src/main/java/io/casehub/qhorus/api/watchdog/AlertContext.java`
* Follows the `MessageType` enum precedent (closed set → typed enum/sealed, not open string)
