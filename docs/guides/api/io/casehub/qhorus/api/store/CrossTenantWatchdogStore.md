# io.casehub.qhorus.api.store.CrossTenantWatchdogStore

**Package:** `io.casehub.qhorus.api.store`

**Kind:** `interface`

Cross-tenant view of watchdog registrations, used by the scheduler to evaluate
all conditions across every tenancy in a single pass.

<p>Obtain via CDI injection:
<pre>`@Inject CrossTenantWatchdogStore store;`</pre>

<p>Refs #260.

## Methods

### `public abstract java.util.List<io.casehub.qhorus.api.watchdog.Watchdog> listAll()`

All watchdog registrations across every tenancy.
