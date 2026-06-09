package io.casehub.qhorus.runtime.store.jpa;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import io.casehub.qhorus.api.message.CommitmentState;
import io.casehub.qhorus.runtime.message.Commitment;
import io.casehub.qhorus.runtime.store.CrossTenantCommitmentStore;

/**
 * JPA implementation of {@link CrossTenantCommitmentStore}.
 * Queries commitments across all tenancies with no tenancyId filter applied.
 *
 * <p>Not injected directly — always accessed via {@code @CrossTenant} from
 * {@code CrossTenantProducer}, which enforces the cross-tenant admin guard.
 *
 * <p>Refs #260.
 */
@ApplicationScoped
public class JpaCrossTenantCommitmentStore implements CrossTenantCommitmentStore {

    @Inject
    CommitmentPanacheRepo repo;

    @Override
    public List<Commitment> findAllOpen() {
        return repo.list(
                "state IN ?1 ORDER BY expiresAt ASC NULLS LAST",
                List.of(CommitmentState.OPEN, CommitmentState.ACKNOWLEDGED));
    }

    @Override
    public List<Commitment> findOpenByChannel(UUID channelId) {
        return repo.list(
                "channelId = ?1 AND state NOT IN ?2",
                channelId, terminalStates());
    }

    @Override
    @Transactional
    public void expireOverdue(Instant cutoff) {
        List<Commitment> overdue = repo.list(
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
