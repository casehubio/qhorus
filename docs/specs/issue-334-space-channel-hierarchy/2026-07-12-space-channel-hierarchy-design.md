# Space — Organizational Channel Hierarchy

**Issue:** #334  
**Epic:** #328 (conversation model enrichments)  
**Date:** 2026-07-12  
**Status:** Design approved

## Problem

Qhorus channels are grouped by naming convention (`case-{id}/work`, `case-{id}/observe`, `case-{id}/oversight`). This is:

- Not queryable — "all channels for case-123" requires name-pattern matching
- Not recursive — project-level grouping above cases is not expressible
- Not portable — every consumer must know the naming convention

## Current State

The following artifacts already exist as partial implementation scaffolding:

| Artifact | State | Notes |
|----------|-------|-------|
| **API layer** | | |
| `Space` record | Exists | Includes `metadata: Map<String, String>` (removed in this spec) |
| `SpaceStore` interface | Exists | Methods: `put`, `findById`, `findByParent`, `findRoots(String)`, `hasChildren`, `delete` |
| `ReactiveSpaceStore` interface | Exists | Mirrors blocking SpaceStore with `Uni<>` wrappers |
| `Channel.spaceId` | Exists | Field 14 of 17, with Builder setter |
| `ChannelCreateRequest.spaceId` | Exists | With Builder setter |
| `ChannelDetail.spaceId` | Exists | UUID field |
| **Runtime — JPA stores** | | |
| `SpaceEntity` JPA entity | Exists | Includes `metadata` field with `MetadataMapConverter` |
| `JpaSpaceStore` | Exists | 76 lines. Tenancy-scoped via `CurrentPrincipal`. All CRUD methods using old names (`findById`, `findByParent`, `findRoots(String)`) |
| `ReactiveJpaSpaceStore` | Exists | 62 lines. `@IfBuildProperty`-gated, uses `SpaceReactivePanacheRepo`. Mirrors blocking store methods (old names, no tenancy scoping on queries) |
| `SpaceReactivePanacheRepo` | Exists | `PanacheRepositoryBase<SpaceEntity, UUID>`, no custom methods |
| `ChannelEntity.spaceId` | Exists | `@Column(name = "space_id")`, mapped in both `fromDomain()` and `toDomain()` |
| `ChannelQuery.spaceId` / `topLevelOnly` | Exists | Factory methods `bySpaceId()` and `topLevel()`, `matches()` filtering |
| `JpaChannelStore.scan()` | Exists | Already generates `AND spaceId = ?N` and `AND spaceId IS NULL` clauses |
| **Runtime — in-memory stores** | | |
| `InMemorySpaceStore` | Exists | ConcurrentHashMap implementation |
| `InMemoryReactiveSpaceStore` | Exists | Delegates to InMemorySpaceStore |
| **Runtime — service layer** | | |
| `SpaceService` | Exists | 70 lines. Methods: `create(name, desc, parentId, metadata)`, `get`, `listChildren`, `listRoots`, `listChannels`, `delete` with child/channel guards. Uses old store method names and accepts `metadata` parameter |
| **Runtime — MCP tools** | | |
| `create_space` | Exists | In both `QhorusMcpTools` and `ReactiveQhorusMcpTools`. Accepts `metadata` parameter, no dual-identity resolution |
| `list_spaces` | Exists | In both tool classes. Lists roots or children by parent_space_id |
| `list_space_channels` | Exists | In both tool classes. UUID-only (no name resolution) |
| `delete_space` | Exists | In both tool classes. UUID-only, delegates to `SpaceService.delete()` |
| **Flyway migrations** | | |
| `V33__space.sql` | Exists | Creates space table: `name VARCHAR(255)`, `description VARCHAR(1024)`, `metadata TEXT`, standard `UNIQUE(tenancy_id, parent_space_id, name)` |
| `V34__channel_space_id.sql` | Exists | Adds `space_id UUID` to channel with FK and index |
| **Tests** | | |
| `SpaceStoreContractTest` | Exists | CRUD, listByParent, listRoots, hasChildren, delete, metadata |
| `SpaceServiceTest` | Exists | 12 tests covering create (root, child, invalid parent), get, listRoots, listChildren, listChannels, delete guards (children, channels), recursive nesting. Uses old API with `metadata` parameter |

**Not yet implemented:** SpaceCreateRequest (new API record), `SpaceService.findByName`/`rename`/`updateDescription`/`moveSpace`/`moveChannelToSpace`/`MAX_DEPTH` (new service methods), `SpaceStore.findByName`/`findByIds` (new store methods), `ChannelStore.hasChannelsInSpace` (new store method), MCP tools `get_space`/`rename_space`/`update_space_description`/`move_space`/`move_channel_to_space` (5 new tools), `resolveSpace()` dual-identity resolution, `ChannelDetail.spaceName` enrichment.

## Solution

Add a `Space` record as an organizational container for related channels. Spaces nest recursively via an adjacency list (`parentSpaceId`). Channels gain an optional `spaceId` field.

No generic metadata map — consumer-specific associations (caseId → spaceId) belong in the consumer layer (engine), not in qhorus. The existing `metadata: Map<String, String>` field on Space is deliberately removed: no production code reads `Space.metadata()`. The `MetadataMapConverter` and related test assertions (`put_preservesMetadata`, `put_emptyMetadata`) are also removed.

## Data Model

### Space (API record — existing, modified)

```java
// api/src/main/java/io/casehub/qhorus/api/channel/Space.java
public record Space(
        UUID id,
        String name,
        String description,
        UUID parentSpaceId,    // nullable — null = root space
        String tenancyId,
        Instant createdAt) {}
```

**Breaking change:** The existing `metadata: Map<String, String>` field is removed. The compact constructor's `metadata` defensive copy is removed.

### SpaceCreateRequest (API record — new)

```java
// api/src/main/java/io/casehub/qhorus/api/channel/SpaceCreateRequest.java
public record SpaceCreateRequest(
        String name,
        String description,    // nullable
        UUID parentSpaceId     // nullable — null = root space
) {
    // Compact constructor validates:
    // - name not blank, trimmed, max 200 chars
    // - UUID-shaped names rejected (dual-identity resolution)
    // - description trimmed if non-null
}
```

Name validation: free-form text (not slug-constrained like channels). Unique within parent scope (see uniqueness constraints in Flyway Migration).

### Channel additions (existing — minimal changes)

All channel-side `spaceId` wiring already exists:

- `Channel` record — `spaceId: UUID?` field with Builder setter *(exists)*
- `ChannelCreateRequest` — `spaceId` field with Builder setter *(exists)*
- `ChannelEntity` — `@Column(name = "space_id")` with correct `fromDomain()` / `toDomain()` mapping *(exists)*
- `ChannelQuery` — `spaceId` and `topLevelOnly` fields with factory methods and `matches()` filtering *(exists)*
- `JpaChannelStore.scan()` — already generates `AND spaceId = ?N` and `AND spaceId IS NULL` clauses *(exists)*

**New in this spec:**
- `ChannelDetail` — gains `spaceName: String?` for MCP display enrichment. Enriched via batch pre-fetch: `list_channels` collects distinct spaceIds from the result set, loads all matching spaces in a single `findByIds` query, and builds a `Map<UUID, String>` (spaceId → name) for resolution. Same pattern as existing connector binding batch enrichment in `QhorusMcpToolsBase`.
- `ChannelStore` — gains `hasChannelsInSpace(UUID spaceId)` (see Store Layer)

### JPA Entity (existing — modified)

```java
// runtime/src/main/java/io/casehub/qhorus/runtime/channel/SpaceEntity.java
@Entity(name = "Space")
@Table(name = "space")  // no @UniqueConstraint — partial indexes in migration
public class SpaceEntity extends PanacheEntityBase {
    @Id public UUID id;
    @Column(nullable = false) public String name;
    public String description;
    @Column(name = "parent_space_id") public UUID parentSpaceId;
    @Column(name = "tenancy_id", nullable = false, updatable = false)
    public String tenancyId = TenancyConstants.DEFAULT_TENANT_ID;
    @Column(name = "created_at", nullable = false, updatable = false)
    public Instant createdAt;

    // @PrePersist for id and createdAt defaults
    // fromDomain(Space) / toDomain() following ChannelEntity pattern
}
```

**Changes from existing SpaceEntity:**
- `metadata` field and `MetadataMapConverter` usage removed from `fromDomain()` / `toDomain()`
- `@UniqueConstraint` annotation dropped — JPA cannot express partial indexes. Uniqueness is enforced by migration-defined partial indexes (see Flyway Migration). The existing `@UniqueConstraint(columnNames = {"tenancy_id", "parent_space_id", "name"})` is insufficient because PostgreSQL treats NULLs as distinct in UNIQUE constraints, allowing duplicate root space names within a tenancy.

## Store Layer

### SpaceStore (existing — renamed methods + new methods)

```java
// api/src/main/java/io/casehub/qhorus/api/store/SpaceStore.java
public interface SpaceStore {
    Space put(Space space);
    Optional<Space> find(UUID id);           // renamed from findById
    List<Space> findByName(String name);     // new — names not globally unique
    List<Space> listByParent(UUID parentSpaceId);  // renamed from findByParent
    List<Space> listRoots();                 // renamed from findRoots(String tenancyId)
    boolean hasChildren(UUID spaceId);
    void delete(UUID id);

    default List<Space> findByIds(Collection<UUID> ids) {   // new — batch lookup
        if (ids == null || ids.isEmpty()) return List.of();
        return ids.stream()
                .map(this::find)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .toList();
    }
}
```

**Renames** (aligning with ChannelStore conventions):
- `findById(UUID)` → `find(UUID)` — consistent with `ChannelStore.find()`
- `findByParent(UUID)` → `listByParent(UUID)` — `list*` prefix for collection returns
- `findRoots(String tenancyId)` → `listRoots()` — tenancy scoped via `CurrentPrincipal` injection, consistent with how `ChannelStore` and `JpaChannelStore` scope all queries by tenancy

**New:** `findByName(String name)` returns `List<Space>` because space names are unique only within a parent, not globally within a tenancy. Multiple spaces with the same name under different parents are all returned.

### ReactiveSpaceStore (existing — matching renames + new methods)

```java
// api/src/main/java/io/casehub/qhorus/api/store/ReactiveSpaceStore.java
public interface ReactiveSpaceStore {
    Uni<Space> put(Space space);
    Uni<Optional<Space>> find(UUID id);           // renamed from findById
    Uni<List<Space>> findByName(String name);     // new
    Uni<List<Space>> listByParent(UUID parentSpaceId);  // renamed from findByParent
    Uni<List<Space>> listRoots();                 // renamed from findRoots(String tenancyId)
    Uni<Boolean> hasChildren(UUID spaceId);
    Uni<Void> delete(UUID id);
    Uni<List<Space>> findByIds(Collection<UUID> ids);   // new — batch lookup
}
```

### ChannelStore addition

```java
boolean hasChannelsInSpace(UUID spaceId);
```

Existence check for the delete guard. Semantics: returns true if at least one channel has this spaceId. Implementation uses `EXISTS`-style query (`SELECT 1 FROM channel WHERE space_id = ?1 AND tenancy_id = ?2 LIMIT 1`) — O(1) on indexed `space_id` column regardless of channel table size.

### ChannelQuery (existing — no changes needed)

The `spaceId` and `topLevelOnly` fields, factory methods (`bySpaceId()`, `topLevel()`), `matches()` filtering, and `JpaChannelStore.scan()` JPQL generation all already exist. No changes needed in this spec.

### Implementations

- **JpaSpaceStore** — `runtime/store/jpa/`, tenancy-scoped via `CurrentPrincipal`. Rename methods (`findById` → `find`, `findByParent` → `listByParent`, `findRoots(String)` → `listRoots()`), remove `tenancyId` parameter from `findRoots` (use injected `CurrentPrincipal`). Add `findByName`, override `findByIds` with `id IN ?1 AND tenancy_id = ?2` for true batch performance *(existing, modified)*
- **ReactiveJpaSpaceStore** — `runtime/store/jpa/`, `@IfBuildProperty`-gated. Same renames as JpaSpaceStore (`findById` → `find`, `findByParent` → `listByParent`, `findRoots(String)` → `listRoots()`). Add `findByName`, `findByIds`. Add tenancy scoping to queries (currently missing — e.g. `findByParent` and `hasChildren` don't filter by tenancy) *(existing, modified)*
- **SpaceReactivePanacheRepo** — `PanacheRepositoryBase<SpaceEntity, UUID>` *(existing, no changes)*
- **InMemorySpaceStore** — update for renamed methods, new `findByName` and `findByIds` *(existing, modified)*
- **InMemoryReactiveSpaceStore** — update for renamed methods, new `findByName` and `findByIds` *(existing, modified)*

## Service Layer (existing — modified)

SpaceService already exists with `create`, `get`, `listChildren`, `listRoots`, `listChannels`, `delete` (with child/channel guards). This spec modifies the existing class: changes `create` to accept `SpaceCreateRequest` (removing `metadata` parameter), adds `MAX_DEPTH` enforcement, and adds new methods.

```java
// runtime/src/main/java/io/casehub/qhorus/runtime/channel/SpaceService.java
@ApplicationScoped
public class SpaceService {
    // Injections: CurrentPrincipal, SpaceStore, ChannelStore

    static final int MAX_DEPTH = 10;                          // new

    Space create(SpaceCreateRequest request);                  // modified — was create(name, desc, parentId, metadata)
    // Validates parent exists if parentSpaceId non-null (existing)
    // Validates depth limit: walks ancestor chain, fails if depth >= MAX_DEPTH (new)
    // Builds Space with tenancyId from currentPrincipal (existing)

    Optional<Space> findById(UUID id);                         // modified — was get(UUID), now returns Optional
    Optional<Space> findByName(String name);                   // new
    // Delegates to SpaceStore.findByName(name).
    // If 0 matches → Optional.empty()
    // If 1 match → Optional.of(match)
    // If >1 matches → throws IllegalStateException("Ambiguous space name, use UUID")
    List<Space> listChildren(UUID parentSpaceId);              // existing — no change
    List<Space> listRoots();                                   // existing — no change
    List<Channel> listChannels(UUID spaceId);                  // existing — no change

    void delete(UUID spaceId);                                 // existing — modify to use hasChannelsInSpace
    // Guard: fails if hasChildren(spaceId) OR hasChannelsInSpace(spaceId)
    // (existing implementation uses channelStore.scan() — change to hasChannelsInSpace for efficiency)

    Space rename(UUID spaceId, String newName);                // new

    Space updateDescription(UUID spaceId, String newDescription);  // new
    // Updates description field. Null clears the description.

    Space moveSpace(UUID spaceId, UUID newParentSpaceId);      // new
    // Validates: newParent exists (if non-null)
    // Validates: no cycle (walks ancestor chain from newParent)
    // Validates: resulting subtree depth does not exceed MAX_DEPTH

    Channel moveChannelToSpace(UUID channelId, UUID spaceId);  // new
    // Validates: space exists (if non-null — null removes from space)
    // Same-tenancy enforcement
}
```

No API-level interface — same pattern as TopicService, ReactionService, PresenceService.

### Cycle detection

`moveSpace` walks up the ancestor chain from `newParentSpaceId` checking that `spaceId` is not an ancestor. Simple loop — practical depth is 2-3 levels, bounded by MAX_DEPTH (10), no recursive CTE needed.

**Concurrency note:** Under READ COMMITTED isolation, two concurrent `moveSpace` calls can each pass cycle detection against stale snapshots and both commit, creating a cycle (e.g., TX A moves X under Y while TX B moves Y under X — neither sees the other's uncommitted write). This is a TOCTOU race inherent to check-then-act on recursive structures.

**Mitigations already in place:**
- MAX_DEPTH (10) bounds ancestor walks, so a cycle cannot cause infinite loops
- Subsequent `moveSpace` or `create` calls on affected spaces would detect and fail on the cycle
- The system degrades gracefully (bounded walk, clear error) rather than hanging

**Why not pessimistic locking:** `SELECT ... FOR UPDATE` on ancestors does not prevent this race — in the problematic scenario, neither space is in the other's ancestor chain at check time, so the locks don't overlap. Full prevention requires serializing all tree mutations within a tenancy (e.g., PostgreSQL advisory lock via `pg_advisory_xact_lock`). This adds persistence-specific concerns to the service layer for a race that requires two administrators to simultaneously move spaces into each other's ancestor chains — an extremely narrow window on a rare administrative operation. If the race proves to be a real problem in production, a tenancy-scoped advisory lock is the recommended fix.

### Delete semantics

Strict guard: fails if the space has channels or child spaces. The consumer must explicitly move channels out and delete child spaces bottom-up before deleting the parent. No silent orphaning or cascading.

## MCP Tool Surface (4 existing, 5 new)

9 tools total in `QhorusMcpTools` (and reactive mirror). 4 already exist; 5 are new.

| Tool | Status | Parameters | Returns |
|------|--------|-----------|---------|
| `create_space` | **existing, modified** | `name`, `description?`, `parent_space_id?` | Space record |
| `get_space` | **new** | `space` (UUID or name) | Space record |
| `list_spaces` | **existing, no change** | `parent_space_id?` (null = roots) | List of spaces |
| `list_space_channels` | **existing, modified** | `space` (UUID or name) | List of ChannelDetail |
| `rename_space` | **new** | `space`, `new_name` | Space record |
| `update_space_description` | **new** | `space`, `description` | Space record |
| `move_space` | **new** | `space`, `parent_space_id?` (null = root) | Space record |
| `move_channel_to_space` | **new** | `channel`, `space_id?` (null = remove) | ChannelDetail |
| `delete_space` | **existing, modified** | `space` (UUID or name) | Confirmation or error |

**Changes to existing tools:**
- `create_space` — removes `metadata` parameter, signature changes to accept `SpaceCreateRequest` fields
- `list_space_channels` — changes from UUID-only `space_id` to dual-identity `space` parameter (UUID or name via `resolveSpace()`)
- `delete_space` — changes from UUID-only `space_id` to dual-identity `space` parameter

**`resolveSpace(String)`** — package-private in `QhorusMcpToolsBase`. UUID parse first, name lookup fallback. If name matches multiple spaces, fails with "Ambiguous space name — use UUID". Same pattern as `resolveChannel()`.

**Existing tool updates:**
- `create_channel` — new optional `space_id` parameter; validates space exists and same tenancy
- `list_channels` — `ChannelDetail` now includes `spaceName` (enriched via batch pre-fetch)

## Flyway Migration (existing — replaced in-place)

V33 and V34 already exist as scaffolding migrations. Since no production database exists, both are replaced in-place with updated schemas.

**V33__space.sql** (existing — replaced):

Changes from existing V33:
- `name` column: `VARCHAR(255)` → `VARCHAR(200)` (consistent with SpaceCreateRequest validation)
- `description` column: `VARCHAR(1024)` → `TEXT` (no practical length limit for descriptions)
- `metadata TEXT` column: removed (no production consumers)
- Uniqueness: standard `UNIQUE(tenancy_id, parent_space_id, name)` → partial indexes (PostgreSQL treats NULLs as distinct in UNIQUE constraints, so the standard constraint allows duplicate root space names)

```sql
CREATE TABLE space (
    id UUID PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    description TEXT,
    parent_space_id UUID,
    tenancy_id VARCHAR(255) NOT NULL DEFAULT '278776f9-e1b0-46fb-9032-8bddebdcf9ce',
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_space_parent FOREIGN KEY (parent_space_id) REFERENCES space(id)
);

-- Parent-scoped name uniqueness via partial indexes
-- JPA @UniqueConstraint cannot express partial indexes;
-- PostgreSQL treats NULLs as distinct in UNIQUE constraints,
-- so a single composite index would allow duplicate root space names.
CREATE UNIQUE INDEX uq_space_name_under_parent
    ON space(tenancy_id, parent_space_id, name)
    WHERE parent_space_id IS NOT NULL;
CREATE UNIQUE INDEX uq_space_name_root
    ON space(tenancy_id, name)
    WHERE parent_space_id IS NULL;

CREATE INDEX idx_space_parent ON space(parent_space_id);
CREATE INDEX idx_space_tenancy ON space(tenancy_id);
```

**V34__channel_space_id.sql** (existing — no changes needed):

The existing V34 already has the correct schema:
```sql
ALTER TABLE channel ADD COLUMN space_id UUID;
ALTER TABLE channel ADD CONSTRAINT fk_channel_space
    FOREIGN KEY (space_id) REFERENCES space(id);
CREATE INDEX idx_channel_space ON channel(space_id);
```

No ON DELETE CASCADE on either FK — delete guards prevent orphan situations.

## Testing Strategy

### Unit tests (CDI-free)

1. **SpaceStoreContractTest** *(existing, modified)* — update for renamed methods, remove metadata tests. Add `findByName`, `findByIds`. Existing tests cover: CRUD, listByParent, listRoots, hasChildren, delete.
2. **SpaceCreateRequest validation** *(new)* — blank, over-length, UUID-shaped, trim.
3. **SpaceServiceTest** *(existing, extended)* — 12 existing tests cover: create (root, child, invalid parent), get, listRoots, listChildren, listChannels, delete guards (children, channels), recursive nesting. Update existing tests to use `SpaceCreateRequest` instead of `metadata` parameter. Add new tests for: MAX_DEPTH enforcement, moveSpace cycle detection, moveSpace depth limit, moveChannelToSpace tenancy enforcement, rename, updateDescription, findByName ambiguity.
4. **ChannelQuery extension** — existing tests sufficient, no changes needed.

### Integration tests (`@QuarkusTest`)

5. **SpaceService integration** — JPA round-trip with `@TestTransaction`.
6. **MCP tool integration** — create/get/list/delete/move/rename/updateDescription tools, dual-identity resolution, ambiguous name resolution.
7. **Channel-Space integration** — create_channel with space_id, ChannelDetail includes spaceName, list_space_channels, delete_channel doesn't affect space.

### Edge cases

- Non-existent parentSpaceId → error
- Delete space with channels → error with count
- Delete space with children → error with count
- Move space to own descendant → cycle error
- Move space to itself → cycle error
- Cross-tenancy move → error
- Dual-identity: get_space by UUID and name
- Ambiguous name resolution → error with guidance to use UUID
- Create space exceeding MAX_DEPTH → error
- Move space causing subtree to exceed MAX_DEPTH → error
- Same name under same parent → constraint violation
- Same name under different parents → allowed

## Out of Scope

- **Normative triple auto-creation** — consumer concern (engine/claudony creates space and assigns channels)
- **UI rendering** — no UI in qhorus
- **Generic metadata map** — consumer-specific mappings (caseId → spaceId) belong in the consumer layer
- **Space lifecycle events** — no event notification for space creation/deletion/moves in this iteration. The platform does not currently have a channel lifecycle event system either, so this is consistent. Deferred to a future spec if consumers need to react to space lifecycle changes (e.g., engine syncing case-space mappings). A GitHub issue should be filed to track this.
