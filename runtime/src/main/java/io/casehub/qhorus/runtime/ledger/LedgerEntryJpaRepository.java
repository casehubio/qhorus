package io.casehub.qhorus.runtime.ledger;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

import io.casehub.ledger.runtime.model.LedgerAttestation;
import io.casehub.ledger.runtime.model.LedgerEntry;
import io.casehub.ledger.runtime.repository.LedgerEntryRepository;
import io.casehub.platform.api.identity.TenancyConstants;
import io.quarkus.hibernate.orm.PersistenceUnit;

/**
 * Qhorus's blocking implementation of {@link LedgerEntryRepository}.
 *
 * <p>All queries target the {@code LedgerEntry} base class, so results span every
 * registered {@code LedgerEntry} subtype (dtype-agnostic). This is the correct
 * behaviour for any cross-dtype concern such as sequence-number assignment.
 *
 * <p>This class is {@code @ApplicationScoped} without any {@code @Priority}, so it
 * becomes the sole active {@code LedgerEntryRepository} bean once
 * {@link MessageLedgerEntryRepository} no longer implements the interface.
 * The library-supplied {@code JpaLedgerEntryRepository} carries {@code @Alternative}
 * (no {@code @Priority}) and therefore remains dormant.
 *
 * <p><strong>Sequence number:</strong> {@code save()} is a plain {@code em.persist()}.
 * Sequence assignment stays in {@link LedgerWriteService#record} until it is
 * migrated to {@code LedgerSequenceAllocator} — tracked in qhorus#256.
 *
 * <p><strong>Tenancy:</strong> {@code tenancyId} parameters are accepted but not yet applied
 * to query filters — full tenant isolation wiring is tracked in qhorus#260 Task 14.
 * Cross-tenant methods (listAll, findAllEvents, findEventsByActorId, findByTimeRange,
 * findAttestationsForEntries) have been removed from this interface; they now live in
 * {@code CrossTenantLedgerEntryRepository}.
 *
 * <p>Refs qhorus#253, qhorus#260.
 */
@ApplicationScoped
public class LedgerEntryJpaRepository implements LedgerEntryRepository {

    @Inject
    @PersistenceUnit("qhorus")
    EntityManager em;

    @Override
    public LedgerEntry save(final LedgerEntry entry, final String tenancyId) {
        entry.tenancyId = tenancyId != null ? tenancyId : TenancyConstants.DEFAULT_TENANT_ID;
        em.persist(entry);
        return entry;
    }

    @Override
    public Optional<LedgerEntry> findLatestBySubjectId(final UUID subjectId, final String tenancyId) {
        // TODO: apply tenancyId filter (qhorus#260 Task 14)
        return em.createQuery(
                "SELECT e FROM LedgerEntry e WHERE e.subjectId = :sid ORDER BY e.sequenceNumber DESC",
                LedgerEntry.class)
                .setParameter("sid", subjectId)
                .setMaxResults(1)
                .getResultStream()
                .findFirst();
    }

    @Override
    public List<LedgerEntry> findBySubjectId(final UUID subjectId, final String tenancyId) {
        // TODO: apply tenancyId filter (qhorus#260 Task 14)
        return em.createQuery(
                "SELECT e FROM LedgerEntry e WHERE e.subjectId = :sid ORDER BY e.sequenceNumber ASC",
                LedgerEntry.class)
                .setParameter("sid", subjectId)
                .getResultList();
    }

    @Override
    public List<LedgerEntry> findBySubjectIdAndTimeRange(final UUID subjectId,
            final Instant from, final Instant to, final String tenancyId) {
        // TODO: apply tenancyId filter (qhorus#260 Task 14)
        return em.createQuery(
                "SELECT e FROM LedgerEntry e WHERE e.subjectId = :sid " +
                        "AND e.occurredAt >= :from AND e.occurredAt <= :to ORDER BY e.occurredAt ASC",
                LedgerEntry.class)
                .setParameter("sid", subjectId)
                .setParameter("from", from)
                .setParameter("to", to)
                .getResultList();
    }

    @Override
    public Optional<LedgerEntry> findEntryById(final UUID id, final String tenancyId) {
        // TODO: apply tenancyId filter (qhorus#260 Task 14)
        // em.find() on the abstract base class is correct for JOINED inheritance —
        // Hibernate resolves the concrete subtype automatically.
        return Optional.ofNullable(em.find(LedgerEntry.class, id));
    }

    @Override
    public List<LedgerEntry> findByActorId(final String actorId,
            final Instant from, final Instant to, final String tenancyId) {
        // TODO: apply tenancyId filter (qhorus#260 Task 14)
        return em.createQuery(
                "SELECT e FROM LedgerEntry e WHERE e.actorId = :aid " +
                        "AND e.occurredAt >= :from AND e.occurredAt <= :to ORDER BY e.occurredAt ASC",
                LedgerEntry.class)
                .setParameter("aid", actorId)
                .setParameter("from", from)
                .setParameter("to", to)
                .getResultList();
    }

    @Override
    public List<LedgerEntry> findByActorRole(final String actorRole,
            final Instant from, final Instant to, final String tenancyId) {
        // TODO: apply tenancyId filter (qhorus#260 Task 14)
        return em.createQuery(
                "SELECT e FROM LedgerEntry e WHERE e.actorRole = :role " +
                        "AND e.occurredAt >= :from AND e.occurredAt <= :to ORDER BY e.occurredAt ASC",
                LedgerEntry.class)
                .setParameter("role", actorRole)
                .setParameter("from", from)
                .setParameter("to", to)
                .getResultList();
    }

    @Override
    public List<LedgerEntry> findCausedBy(final UUID entryId, final String tenancyId) {
        // TODO: apply tenancyId filter (qhorus#260 Task 14)
        return em.createQuery(
                "SELECT e FROM LedgerEntry e WHERE e.causedByEntryId = :eid ORDER BY e.sequenceNumber ASC",
                LedgerEntry.class)
                .setParameter("eid", entryId)
                .getResultList();
    }

    @Override
    public LedgerAttestation saveAttestation(final LedgerAttestation attestation, final String tenancyId) {
        // TODO: apply tenancyId to attestation before persist (qhorus#260 Task 14)
        em.persist(attestation);
        return attestation;
    }

    @Override
    public List<LedgerAttestation> findAttestationsByEntryId(final UUID ledgerEntryId, final String tenancyId) {
        // TODO: apply tenancyId filter (qhorus#260 Task 14)
        return em.createNamedQuery("LedgerAttestation.findByEntryId", LedgerAttestation.class)
                .setParameter("entryId", ledgerEntryId)
                .getResultList();
    }

    @Override
    public List<LedgerAttestation> findAttestationsByEntryIdAndCapabilityTag(
            final UUID ledgerEntryId, final String capabilityTag, final String tenancyId) {
        // TODO: apply tenancyId filter (qhorus#260 Task 14)
        return em.createNamedQuery("LedgerAttestation.findByEntryIdAndCapabilityTag", LedgerAttestation.class)
                .setParameter("entryId", ledgerEntryId)
                .setParameter("capabilityTag", capabilityTag)
                .getResultList();
    }

    @Override
    public List<LedgerAttestation> findAttestationsByEntryIdGlobal(final UUID ledgerEntryId, final String tenancyId) {
        // TODO: apply tenancyId filter (qhorus#260 Task 14)
        return em.createNamedQuery("LedgerAttestation.findGlobalByEntryId", LedgerAttestation.class)
                .setParameter("entryId", ledgerEntryId)
                .getResultList();
    }

    @Override
    public List<LedgerAttestation> findAttestationsByAttestorIdAndCapabilityTag(
            final String attestorId, final String capabilityTag, final String tenancyId) {
        // TODO: apply tenancyId filter (qhorus#260 Task 14)
        return em.createNamedQuery("LedgerAttestation.findByAttestorIdAndCapabilityTag", LedgerAttestation.class)
                .setParameter("attestorId", attestorId)
                .setParameter("capabilityTag", capabilityTag)
                .getResultList();
    }
}
