package io.casehub.qhorus.runtime.store.jpa;

import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;

import io.casehub.qhorus.runtime.store.CrossTenantWatchdogStore;
import io.casehub.qhorus.runtime.watchdog.Watchdog;

/**
 * JPA implementation of {@link CrossTenantWatchdogStore}.
 * Returns all watchdog registrations across all tenancies with no tenancyId filter.
 *
 * <p>Not injected directly — always accessed via {@code @CrossTenant} from
 * {@code CrossTenantProducer}, which enforces the cross-tenant admin guard.
 *
 * <p>Refs #260.
 */
@ApplicationScoped
public class JpaCrossTenantWatchdogStore implements CrossTenantWatchdogStore {

    @Override
    public List<Watchdog> listAll() {
        return Watchdog.listAll();
    }
}
