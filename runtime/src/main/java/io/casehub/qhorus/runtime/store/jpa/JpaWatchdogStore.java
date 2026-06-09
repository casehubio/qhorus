package io.casehub.qhorus.runtime.store.jpa;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import io.casehub.platform.api.identity.CurrentPrincipal;
import io.casehub.qhorus.runtime.store.WatchdogStore;
import io.casehub.qhorus.runtime.store.query.WatchdogQuery;
import io.casehub.qhorus.runtime.watchdog.Watchdog;

@ApplicationScoped
public class JpaWatchdogStore implements WatchdogStore {

    @Inject
    CurrentPrincipal currentPrincipal;

    @Override
    @Transactional
    public Watchdog put(Watchdog watchdog) {
        watchdog.persistAndFlush();
        return watchdog;
    }

    @Override
    public Optional<Watchdog> find(UUID id) {
        return Watchdog.find("id = ?1 AND tenancyId = ?2", id, currentPrincipal.tenancyId())
                .firstResultOptional();
    }

    @Override
    public List<Watchdog> scan(WatchdogQuery q) {
        StringBuilder jpql = new StringBuilder("FROM Watchdog WHERE tenancyId = ?1");
        List<Object> params = new ArrayList<>();
        params.add(currentPrincipal.tenancyId());
        int idx = 2;

        if (q.conditionType() != null) {
            jpql.append(" AND conditionType = ?").append(idx++);
            params.add(q.conditionType());
        }

        return Watchdog.list(jpql.toString(), params.toArray());
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        Watchdog.delete("id = ?1 AND tenancyId = ?2", id, currentPrincipal.tenancyId());
    }
}
