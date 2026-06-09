package io.casehub.qhorus.runtime.ledger;

import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import jakarta.transaction.Transactional.TxType;

import io.casehub.ledger.runtime.persistence.LedgerPersistenceUnit;

/**
 * Atomic sequence number allocator for the qhorus ledger.
 *
 * <p>Runs the MERGE in its own {@code REQUIRES_NEW} transaction so the allocation
 * commits immediately and is visible to all concurrent writers. This eliminates the
 * TOCTOU race that occurs if two concurrent REQUIRES_NEW transactions both evaluate
 * the MERGE WHEN-NOT-MATCHED branch before either commits:
 * <pre>
 *   T1: NOT-MATCHED → INSERT  (T2 cannot see T1's uncommitted row in H2)
 *   T2: NOT-MATCHED → INSERT  → PK violation
 * </pre>
 *
 * <p>With REQUIRES_NEW here, T1's INSERT commits immediately. When the caller holds
 * a synchronized lock across this call, T2 cannot enter until T1's REQUIRES_NEW has
 * committed — T2's MERGE then sees MATCHED and UPDATEs correctly. Refs qhorus#256.
 */
@ApplicationScoped
class QhorusSequenceAllocator {

    @Inject
    @LedgerPersistenceUnit
    EntityManager em;

    @Transactional(TxType.REQUIRES_NEW)
    int nextSequenceNumber(final UUID subjectId) {
        em.createNativeQuery("""
                MERGE INTO ledger_subject_sequence AS t \
                USING (SELECT CAST(?1 AS UUID) AS sid) AS s ON t.subject_id = s.sid \
                WHEN MATCHED THEN UPDATE SET next_seq = t.next_seq + 1 \
                WHEN NOT MATCHED THEN INSERT (subject_id, next_seq) VALUES (s.sid, 2)\
                """)
                .setParameter(1, subjectId)
                .executeUpdate();
        em.flush();
        final Number nextSeq = (Number) em.createNativeQuery(
                "SELECT next_seq - 1 FROM ledger_subject_sequence WHERE subject_id = ?1")
                .setParameter(1, subjectId)
                .getSingleResult();
        return nextSeq.intValue();
    }
}
