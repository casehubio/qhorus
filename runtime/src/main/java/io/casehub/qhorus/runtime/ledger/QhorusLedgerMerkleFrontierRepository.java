package io.casehub.qhorus.runtime.ledger;

import io.casehub.ledger.runtime.repository.jpa.JpaLedgerMerkleFrontierRepository;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Qhorus's concrete {@link io.casehub.ledger.runtime.repository.LedgerMerkleFrontierRepository} bean.
 *
 * <p>Inherits all behaviour from {@link JpaLedgerMerkleFrontierRepository}: frontier
 * read, delete-and-replace for the Merkle tree append operation.
 *
 * <p>Plain {@code @ApplicationScoped} — displaces both the {@code @DefaultBean}
 * no-op producer in {@code LedgerCoreProducer} and the {@code @DefaultBean}
 * {@code JpaLedgerMerkleFrontierRepository} from casehub-ledger (a normal bean
 * beats {@code @DefaultBean}). Refs qhorus#255, qhorus#490, qhorus#491.
 */
@ApplicationScoped
class QhorusLedgerMerkleFrontierRepository extends JpaLedgerMerkleFrontierRepository {
    @jakarta.inject.Inject
    @io.casehub.ledger.jpa.LedgerPersistenceUnit
    jakarta.persistence.EntityManager em;

    java.util.List<io.casehub.ledger.api.model.LedgerMerkleFrontier> findBySubjectIdForUpdate(java.util.UUID subjectId, String tenancyId) {
        return java.util.List.copyOf(em.createQuery(
                                               "SELECT f FROM LedgerMerkleFrontier f WHERE f.subjectId = :subjectId AND f.tenancyId = :tenancyId ORDER BY f.level ASC",
                                               io.casehub.ledger.jpa.LedgerMerkleFrontier.class)
                                       .setParameter("subjectId", subjectId)
                                       .setParameter("tenancyId", tenancyId)
                                       .setLockMode(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
                                       .getResultList());
    }
}
