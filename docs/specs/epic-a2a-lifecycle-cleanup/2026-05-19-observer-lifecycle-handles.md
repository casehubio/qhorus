# MessageObserverDispatcher — Instance.handles() for proper @Dependent lifecycle (#167)

**Date:** 2026-05-19
**Issue:** casehubio/qhorus#167
**Epic:** epic-a2a-lifecycle-cleanup

## Problem

`MessageObserverDispatcher.dispatch()` accepts `Iterable<MessageObserver>`. Both
callers (`MessageService`, `ReactiveMessageService`) pass `Instance<MessageObserver>`
directly, which satisfies the `Iterable<T>` contract. When CDI iterates an
`Instance<T>`, each element is produced via `Instance.get()`. For `@Dependent`-scoped
beans, every `get()` creates a new contextual instance that must be paired with a
`destroy()` call — but the dispatcher never destroys them. One instance leaks per
message persisted, accumulating for the JVM lifetime.

## Design

**Change `dispatch()` to accept `Iterable<InstanceHandle<MessageObserver>>`.**

`InstanceHandle<T>` (Quarkus Arc) pairs a bean instance with its lifecycle: `get()`
retrieves the instance, `close()` destroys it (`@Dependent`) or is a no-op (normal
scopes). Callers replace `observers` with `observers.handles()` — one word each.

```java
static void dispatch(..., Iterable<InstanceHandle<MessageObserver>> handles) {
    for (final InstanceHandle<MessageObserver> handle : handles) {
        final MessageObserver observer = handle.get();
        try {
            observer.onMessage(event);
        } catch (Exception e) {
            LOG.warnf(..., observer.getClass().getSuperclass().getSimpleName(), ...);
        } finally {
            handle.close();
        }
    }
}
```

The dispatcher stays CDI-agnostic (no `Instance` in its imports). Tests supply
`InstanceHandle<MessageObserver>` implementations with a no-op `close()` via a
package-private factory:

```java
static <T> InstanceHandle<T> handle(T instance) {
    return new InstanceHandle<>() {
        @Override public T get() { return instance; }
        @Override public void close() {}
    };
}
```

## Consequences

- `@Dependent`-scoped `MessageObserver` implementations now work correctly.
- The `@ApplicationScoped` constraint (PP-20260518-837246) is removed — any normal
  CDI scope is valid for `MessageObserver` implementations.
- CLAUDE.md testing convention and protocol updated accordingly.

## No deferred concerns

All concerns captured in the GitHub issue or resolved in design.
