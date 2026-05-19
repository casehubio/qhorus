package io.casehub.qhorus.runtime.ledger;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;

import io.casehub.ledger.runtime.model.LedgerAttestation;
import io.casehub.ledger.runtime.model.LedgerEntry;
import io.casehub.ledger.runtime.repository.ReactiveLedgerEntryRepository;
import io.quarkus.arc.DefaultBean;
import io.smallrye.mutiny.Uni;

/**
 * Test stub satisfying the ReactiveLedgerEntryRepository CDI dependency when
 * quarkus.datasource.qhorus.reactive=false (i.e., all non-reactive @QuarkusTest runs).
 *
 * The real implementation (ReactiveMessageLedgerEntryRepository) is gated by
 * @IfBuildProperty(reactive=true) and is absent in H2/non-reactive test contexts.
 * Without this stub, casehub-ledger beans that inject ReactiveLedgerEntryRepository
 * (LedgerVerificationService, KeyRotationService) fail CDI validation at build time.
 *
 * No method is expected to be called in non-reactive tests — all throw.
 */
@DefaultBean
@ApplicationScoped
class StubReactiveLedgerEntryRepository implements ReactiveLedgerEntryRepository {

    @Override
    public Uni<LedgerEntry> save(final LedgerEntry entry) {
        throw new UnsupportedOperationException("reactive ledger not available in non-reactive tests");
    }

    @Override
    public Uni<List<LedgerEntry>> listAll() {
        throw new UnsupportedOperationException("reactive ledger not available in non-reactive tests");
    }

    @Override
    public Uni<List<LedgerEntry>> findBySubjectId(final UUID subjectId) {
        throw new UnsupportedOperationException("reactive ledger not available in non-reactive tests");
    }

    @Override
    public Uni<List<LedgerEntry>> findBySubjectIdAndTimeRange(
            final UUID subjectId, final Instant from, final Instant to) {
        throw new UnsupportedOperationException("reactive ledger not available in non-reactive tests");
    }

    @Override
    public Uni<Optional<LedgerEntry>> findLatestBySubjectId(final UUID subjectId) {
        throw new UnsupportedOperationException("reactive ledger not available in non-reactive tests");
    }

    @Override
    public Uni<Optional<LedgerEntry>> findEntryById(final UUID id) {
        throw new UnsupportedOperationException("reactive ledger not available in non-reactive tests");
    }

    @Override
    public Uni<List<LedgerEntry>> findAllEvents() {
        throw new UnsupportedOperationException("reactive ledger not available in non-reactive tests");
    }

    @Override
    public Uni<List<LedgerEntry>> findByActorId(final String actorId, final Instant from, final Instant to) {
        throw new UnsupportedOperationException("reactive ledger not available in non-reactive tests");
    }

    @Override
    public Uni<List<LedgerEntry>> findByActorRole(final String actorRole, final Instant from, final Instant to) {
        throw new UnsupportedOperationException("reactive ledger not available in non-reactive tests");
    }

    @Override
    public Uni<List<LedgerEntry>> findByTimeRange(final Instant from, final Instant to) {
        throw new UnsupportedOperationException("reactive ledger not available in non-reactive tests");
    }

    @Override
    public Uni<List<LedgerEntry>> findCausedBy(final UUID entryId) {
        throw new UnsupportedOperationException("reactive ledger not available in non-reactive tests");
    }

    @Override
    public Uni<LedgerAttestation> saveAttestation(final LedgerAttestation attestation) {
        throw new UnsupportedOperationException("reactive ledger not available in non-reactive tests");
    }

    @Override
    public Uni<List<LedgerAttestation>> findAttestationsByEntryId(final UUID ledgerEntryId) {
        throw new UnsupportedOperationException("reactive ledger not available in non-reactive tests");
    }

    @Override
    public Uni<Map<UUID, List<LedgerAttestation>>> findAttestationsForEntries(final Set<UUID> entryIds) {
        throw new UnsupportedOperationException("reactive ledger not available in non-reactive tests");
    }

    @Override
    public Uni<List<LedgerAttestation>> findAttestationsByEntryIdAndCapabilityTag(
            final UUID entryId, final String capabilityTag) {
        throw new UnsupportedOperationException("reactive ledger not available in non-reactive tests");
    }

    @Override
    public Uni<List<LedgerAttestation>> findAttestationsByEntryIdGlobal(final UUID entryId) {
        throw new UnsupportedOperationException("reactive ledger not available in non-reactive tests");
    }

    @Override
    public Uni<List<LedgerAttestation>> findAttestationsByAttestorIdAndCapabilityTag(
            final String attestorId, final String capabilityTag) {
        throw new UnsupportedOperationException("reactive ledger not available in non-reactive tests");
    }
}
