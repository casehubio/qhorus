package io.casehub.qhorus.runtime.ledger;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import io.casehub.ledger.runtime.model.LedgerAttestation;
import io.casehub.ledger.runtime.model.LedgerEntry;
import io.casehub.ledger.runtime.privacy.ActorIdentityProvider;
import io.casehub.ledger.runtime.repository.ReactiveLedgerEntryRepository;
import io.casehub.platform.api.identity.TenancyConstants;
import io.quarkus.arc.properties.IfBuildProperty;
import io.smallrye.mutiny.Uni;

/**
 * Reactive implementation of {@link ReactiveLedgerEntryRepository} for qhorus.
 *
 * <p>All queries target the {@code LedgerEntry} base class via raw Hibernate Reactive
 * JPQL — never through {@link MessageReactivePanacheRepo} (which scopes to
 * {@code MessageLedgerEntry} only). Session access uses {@code repo.getSession()}.
 *
 * <p>Gated by {@code @IfBuildProperty} because this class injects
 * {@link MessageReactivePanacheRepo}, which is itself gated. Removing the gate
 * would cause a CDI build-time unsatisfied injection failure in non-reactive builds.
 *
 * <p>In non-reactive builds, {@link StubReactiveLedgerEntryRepository} ({@code @DefaultBean})
 * satisfies the {@link ReactiveLedgerEntryRepository} injection point instead.
 *
 * <p><strong>Sequence number:</strong> {@code save()} calls {@code session.persist()} without
 * sequence assignment. Sequence stays in {@link ReactiveLedgerWriteService#record} until
 * migrated to {@code LedgerSequenceAllocator} — tracked in qhorus#256.
 *
 * <p><strong>Tenancy:</strong> {@code tenancyId} parameters are accepted but not yet applied
 * to query filters — full tenant isolation wiring is tracked in qhorus#260 Task 14.
 *
 * <p>Refs qhorus#253, qhorus#260.
 */
@IfBuildProperty(name = "casehub.qhorus.reactive.enabled", stringValue = "true")
@ApplicationScoped
public class ReactiveLedgerEntryJpaRepository implements ReactiveLedgerEntryRepository {

    @Inject
    MessageReactivePanacheRepo repo; // session access only — never used for typed Panache queries

    @Inject
    ActorIdentityProvider actorIdentityProvider;

    @Override
    public Uni<LedgerEntry> save(final LedgerEntry entry, final String tenancyId) {
        entry.tenancyId = tenancyId != null ? tenancyId : TenancyConstants.DEFAULT_TENANT_ID;
        return repo.getSession()
                .flatMap(session -> session.persist(entry).replaceWith(entry));
    }

    @Override
    public Uni<Optional<LedgerEntry>> findLatestBySubjectId(final UUID subjectId, final String tenancyId) {
        // TODO: apply tenancyId filter (qhorus#260 Task 14)
        return repo.getSession()
                .flatMap(session -> session
                        .createQuery(
                                "SELECT e FROM LedgerEntry e WHERE e.subjectId = :sid " +
                                        "ORDER BY e.sequenceNumber DESC",
                                LedgerEntry.class)
                        .setParameter("sid", subjectId)
                        .setMaxResults(1)
                        .getSingleResultOrNull()
                        .map(Optional::ofNullable));
    }

    @Override
    @SuppressWarnings("unchecked")
    public Uni<List<LedgerEntry>> findBySubjectId(final UUID subjectId, final String tenancyId) {
        // TODO: apply tenancyId filter (qhorus#260 Task 14)
        return repo.getSession()
                .flatMap(session -> session
                        .createQuery(
                                "SELECT e FROM LedgerEntry e WHERE e.subjectId = :sid " +
                                        "ORDER BY e.sequenceNumber ASC",
                                LedgerEntry.class)
                        .setParameter("sid", subjectId)
                        .getResultList());
    }

    @Override
    @SuppressWarnings("unchecked")
    public Uni<List<LedgerEntry>> findBySubjectIdAndTimeRange(final UUID subjectId,
            final Instant from, final Instant to, final String tenancyId) {
        // TODO: apply tenancyId filter (qhorus#260 Task 14)
        return repo.getSession()
                .flatMap(session -> session
                        .createQuery(
                                "SELECT e FROM LedgerEntry e WHERE e.subjectId = :sid " +
                                        "AND e.occurredAt >= :from AND e.occurredAt <= :to " +
                                        "ORDER BY e.occurredAt ASC",
                                LedgerEntry.class)
                        .setParameter("sid", subjectId)
                        .setParameter("from", from)
                        .setParameter("to", to)
                        .getResultList());
    }

    @Override
    public Uni<Optional<LedgerEntry>> findEntryById(final UUID id, final String tenancyId) {
        // TODO: apply tenancyId filter (qhorus#260 Task 14)
        // find() on the abstract base class is correct — Hibernate Reactive resolves the concrete subtype
        return repo.getSession()
                .flatMap(session -> session.find(LedgerEntry.class, id).map(Optional::ofNullable));
    }

    @Override
    @SuppressWarnings("unchecked")
    public Uni<List<LedgerEntry>> findByActorId(final String actorId,
            final Instant from, final Instant to, final String tenancyId) {
        // TODO: apply tenancyId filter (qhorus#260 Task 14)
        return repo.getSession()
                .flatMap(session -> session
                        .createQuery(
                                "SELECT e FROM LedgerEntry e WHERE e.actorId = :aid " +
                                        "AND e.occurredAt >= :from AND e.occurredAt <= :to " +
                                        "ORDER BY e.occurredAt ASC",
                                LedgerEntry.class)
                        .setParameter("aid", actorId)
                        .setParameter("from", from)
                        .setParameter("to", to)
                        .getResultList());
    }

    @Override
    @SuppressWarnings("unchecked")
    public Uni<List<LedgerEntry>> findByActorRole(final String actorRole,
            final Instant from, final Instant to, final String tenancyId) {
        // TODO: apply tenancyId filter (qhorus#260 Task 14)
        return repo.getSession()
                .flatMap(session -> session
                        .createQuery(
                                "SELECT e FROM LedgerEntry e WHERE e.actorRole = :role " +
                                        "AND e.occurredAt >= :from AND e.occurredAt <= :to " +
                                        "ORDER BY e.occurredAt ASC",
                                LedgerEntry.class)
                        .setParameter("role", actorRole)
                        .setParameter("from", from)
                        .setParameter("to", to)
                        .getResultList());
    }

    @Override
    @SuppressWarnings("unchecked")
    public Uni<List<LedgerEntry>> findCausedBy(final UUID entryId, final String tenancyId) {
        // TODO: apply tenancyId filter (qhorus#260 Task 14)
        return repo.getSession()
                .flatMap(session -> session
                        .createQuery(
                                "SELECT e FROM LedgerEntry e WHERE e.causedByEntryId = :eid " +
                                        "ORDER BY e.sequenceNumber ASC",
                                LedgerEntry.class)
                        .setParameter("eid", entryId)
                        .getResultList());
    }

    @Override
    public Uni<LedgerAttestation> saveAttestation(final LedgerAttestation attestation, final String tenancyId) {
        // TODO: apply tenancyId to attestation before persist (qhorus#260 Task 14)
        if (attestation.attestorId != null) {
            attestation.attestorId = actorIdentityProvider.tokenise(attestation.attestorId);
        }
        return repo.getSession()
                .flatMap(session -> session.persist(attestation).replaceWith(attestation));
    }

    @Override
    public Uni<List<LedgerAttestation>> findAttestationsByEntryId(final UUID ledgerEntryId, final String tenancyId) {
        // TODO: apply tenancyId filter (qhorus#260 Task 14)
        return repo.getSession()
                .flatMap(session -> session
                        .createNamedQuery("LedgerAttestation.findByEntryId", LedgerAttestation.class)
                        .setParameter("entryId", ledgerEntryId)
                        .getResultList());
    }

    @Override
    public Uni<List<LedgerAttestation>> findAttestationsByEntryIdAndCapabilityTag(
            final UUID entryId, final String capabilityTag, final String tenancyId) {
        // TODO: apply tenancyId filter (qhorus#260 Task 14)
        return repo.getSession()
                .flatMap(session -> session
                        .createNamedQuery("LedgerAttestation.findByEntryIdAndCapabilityTag",
                                LedgerAttestation.class)
                        .setParameter("entryId", entryId)
                        .setParameter("capabilityTag", capabilityTag)
                        .getResultList());
    }

    @Override
    public Uni<List<LedgerAttestation>> findAttestationsByEntryIdGlobal(final UUID ledgerEntryId, final String tenancyId) {
        // TODO: apply tenancyId filter (qhorus#260 Task 14)
        return repo.getSession()
                .flatMap(session -> session
                        .createNamedQuery("LedgerAttestation.findGlobalByEntryId", LedgerAttestation.class)
                        .setParameter("entryId", ledgerEntryId)
                        .getResultList());
    }

    @Override
    public Uni<List<LedgerAttestation>> findAttestationsByAttestorIdAndCapabilityTag(
            final String attestorId, final String capabilityTag, final String tenancyId) {
        // TODO: apply tenancyId filter (qhorus#260 Task 14)
        return repo.getSession()
                .flatMap(session -> session
                        .createNamedQuery("LedgerAttestation.findByAttestorIdAndCapabilityTag",
                                LedgerAttestation.class)
                        .setParameter("attestorId", actorIdentityProvider.tokeniseForQuery(attestorId))
                        .setParameter("capabilityTag", capabilityTag)
                        .getResultList());
    }
}
