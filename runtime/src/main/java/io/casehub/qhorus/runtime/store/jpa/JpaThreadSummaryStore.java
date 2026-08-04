package io.casehub.qhorus.runtime.store.jpa;

import io.casehub.qhorus.api.channel.ThreadSummary;
import io.casehub.qhorus.api.store.ThreadSummaryStore;
import io.casehub.qhorus.runtime.channel.ThreadSummaryEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class JpaThreadSummaryStore implements ThreadSummaryStore {

    @Inject
    ThreadSummaryPanacheRepo repo;

    @Override
    @Transactional
    public ThreadSummary save(ThreadSummary summary) {
        Optional<ThreadSummaryEntity> existing = repo.find(
                "channelId = ?1 AND correlationId = ?2",
                summary.channelId(), summary.correlationId())
                .firstResultOptional();

        ThreadSummaryEntity e = ThreadSummaryEntity.fromDomain(summary);
        if (existing.isPresent()) {
            e.id = existing.get().id;
            e = repo.getEntityManager().merge(e);
        } else {
            repo.persist(e);
        }
        return e.toDomain();
    }

    @Override
    public Optional<ThreadSummary> findByCorrelationId(UUID channelId,
                                                        String correlationId) {
        return repo.find("channelId = ?1 AND correlationId = ?2",
                         channelId, correlationId)
                   .firstResultOptional()
                   .map(ThreadSummaryEntity::toDomain);
    }

    @Override
    public List<ThreadSummary> findByChannel(UUID channelId) {
        return repo.find("channelId", channelId).list().stream()
                   .map(ThreadSummaryEntity::toDomain)
                   .toList();
    }

    @Override
    @Transactional
    public void delete(UUID channelId, String correlationId) {
        repo.delete("channelId = ?1 AND correlationId = ?2",
                    channelId, correlationId);
    }
}
