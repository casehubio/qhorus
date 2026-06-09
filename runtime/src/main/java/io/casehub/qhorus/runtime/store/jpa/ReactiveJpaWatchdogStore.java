package io.casehub.qhorus.runtime.store.jpa;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import io.casehub.qhorus.runtime.store.ReactiveWatchdogStore;
import io.casehub.qhorus.runtime.store.query.WatchdogQuery;
import io.casehub.qhorus.runtime.watchdog.Watchdog;
import io.quarkus.arc.properties.IfBuildProperty;
import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.smallrye.mutiny.Uni;

@IfBuildProperty(name = "casehub.qhorus.reactive.enabled", stringValue = "true")
@ApplicationScoped
public class ReactiveJpaWatchdogStore implements ReactiveWatchdogStore {

    @Inject
    WatchdogReactivePanacheRepo repo;

    @Override
    @WithTransaction
    public Uni<Watchdog> put(Watchdog watchdog) {
        return repo.persist(watchdog);
    }

    @Override
    public Uni<Optional<Watchdog>> find(UUID id) {
        return repo.findById(id).map(Optional::ofNullable);
    }

    @Override
    public Uni<List<Watchdog>> scan(WatchdogQuery q) {
        StringBuilder jpql = new StringBuilder("FROM Watchdog WHERE 1=1");
        List<Object> params = new ArrayList<>();
        int idx = 1;

        if (q.conditionType() != null) {
            jpql.append(" AND conditionType = ?").append(idx++);
            params.add(q.conditionType());
        }
        if (q.tenancyId() != null) {
            jpql.append(" AND tenancyId = ?").append(idx++);
            params.add(q.tenancyId());
        }

        return repo.list(jpql.toString(), params.toArray());
    }

    @Override
    @WithTransaction
    public Uni<Void> delete(UUID id) {
        return repo.deleteById(id).replaceWithVoid();
    }
}
