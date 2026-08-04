package io.casehub.qhorus.runtime.store.jpa;

import io.casehub.qhorus.runtime.channel.ThreadSummaryEntity;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.UUID;

@ApplicationScoped
class ThreadSummaryPanacheRepo
        implements PanacheRepositoryBase<ThreadSummaryEntity, UUID> {
}
