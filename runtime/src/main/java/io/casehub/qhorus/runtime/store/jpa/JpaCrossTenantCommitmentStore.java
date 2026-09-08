package io.casehub.qhorus.runtime.store.jpa;

import io.casehub.qhorus.api.message.Commitment;
import io.casehub.qhorus.api.message.CommitmentState;
import io.casehub.qhorus.api.store.CrossTenantCommitmentStore;
import io.casehub.qhorus.runtime.message.CommitmentEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class JpaCrossTenantCommitmentStore implements CrossTenantCommitmentStore {

    @Inject
    CommitmentPanacheRepo repo;

    @Override
    public List<Commitment> findAllOpen() {
        return repo.<CommitmentEntity>list(
                "state IN ?1 ORDER BY expiresAt ASC NULLS LAST",
                List.of(CommitmentState.OPEN, CommitmentState.ACKNOWLEDGED))
                .stream().map(CommitmentEntity::toDomain).toList();
    }

    @Override
    public List<Commitment> findOpenByChannel(UUID channelId) {
        return repo.<CommitmentEntity>list(
                "channelId = ?1 AND state NOT IN ?2",
                channelId, terminalStates())
                .stream().map(CommitmentEntity::toDomain).toList();
    }

    @Override
    public List<Commitment> findAllByCorrelationId(String correlationId) {
        return repo.<CommitmentEntity>list(
                           "correlationId = ?1 ORDER BY createdAt ASC", correlationId)
                   .stream().map(CommitmentEntity::toDomain).toList();
    }

    @Override
    public List<Commitment> findOpenByObligor(String obligor) {
        return repo.<CommitmentEntity>list(
                           "obligor = ?1 AND state IN ?2 ORDER BY createdAt ASC",
                           obligor, List.of(CommitmentState.OPEN, CommitmentState.ACKNOWLEDGED))
                   .stream().map(CommitmentEntity::toDomain).toList();
    }

    @Override
    public java.util.Optional<Commitment> findLatestDelegatedByObligor(String obligor) {
        return repo.<CommitmentEntity>find(
                           "obligor = ?1 AND state = ?2 ORDER BY resolvedAt DESC",
                           obligor, CommitmentState.DELEGATED)
                   .firstResultOptional()
                   .map(CommitmentEntity::toDomain);
    }

    @Override
    public long countOpenByObligor(String obligor) {
        return repo.count("obligor = ?1 AND state IN ?2",
                          obligor, List.of(CommitmentState.OPEN, CommitmentState.ACKNOWLEDGED));
    }

    @SuppressWarnings("unchecked")
    @Override
    public java.util.Map<String, Long> findObligorsExceedingCount(int minCount) {
        List<Object[]> rows = repo.getEntityManager().createQuery(
                                          "SELECT c.obligor, COUNT(c) FROM CommitmentEntity c " +
                                          "WHERE c.state IN ?1 GROUP BY c.obligor HAVING COUNT(c) >= ?2")
                                  .setParameter(1, List.of(CommitmentState.OPEN, CommitmentState.ACKNOWLEDGED))
                                  .setParameter(2, (long) minCount)
                                  .getResultList();
        java.util.Map<String, Long> result = new java.util.LinkedHashMap<>();
        for (Object[] row : rows) {
            result.put((String) row[0], (Long) row[1]);
        }
        return result;
    }


    @Override
    @Transactional
    public void expireOverdue(Instant cutoff) {
        List<CommitmentEntity> overdue = repo.list(
                "expiresAt < ?1 AND state NOT IN ?2",
                cutoff, terminalStates());
        Instant now = Instant.now();
        overdue.forEach(c -> {
            c.state = CommitmentState.EXPIRED;
            c.resolvedAt = now;
            repo.getEntityManager().merge(c);
        });
    }

    private List<CommitmentState> terminalStates() {
        return List.of(CommitmentState.FULFILLED, CommitmentState.DECLINED,
                CommitmentState.FAILED, CommitmentState.DELEGATED, CommitmentState.EXPIRED);
    }
}
