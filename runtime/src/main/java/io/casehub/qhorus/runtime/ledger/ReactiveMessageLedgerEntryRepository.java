package io.casehub.qhorus.runtime.ledger;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import io.casehub.platform.api.identity.TenancyConstants;
import io.quarkus.arc.properties.IfBuildProperty;
import io.smallrye.mutiny.Uni;

/**
 * Reactive qhorus-scoped repository — all queries target {@code MessageLedgerEntry} only.
 *
 * <p>These methods serve qhorus-specific reactive concerns: causal chain resolution,
 * inReplyTo lookup, and subject propagation for qhorus messages. They must not be used
 * for cross-dtype operations.
 *
 * <p>Cross-dtype reactive operations now live in {@link ReactiveLedgerEntryJpaRepository}.
 *
 * <p>All query methods except {@link #findByMessageId} accept a {@code tenancyId} and scope
 * results to that tenant. Null is normalised to {@link TenancyConstants#DEFAULT_TENANT_ID}.
 *
 * <p>The {@code @IfBuildProperty} gate is mandatory: this class injects
 * {@link MessageReactivePanacheRepo}, which is itself gated. Without the gate, CDI
 * build-time validation fails with an unsatisfied injection point in non-reactive builds.
 *
 * <p>Refs qhorus#253, #263.
 */
@IfBuildProperty(name = "casehub.qhorus.reactive.enabled", stringValue = "true")
@ApplicationScoped
public class ReactiveMessageLedgerEntryRepository {

    @Inject
    MessageReactivePanacheRepo repo;

    /**
     * Returns the most recent COMMAND or HANDOFF entry in {@code tenancyId} with the given
     * correlationId. Used at write time to resolve {@code causedByEntryId}.
     */
    public Uni<Optional<MessageLedgerEntry>> findLatestByCorrelationId(final UUID channelId,
            final String correlationId, final String tenancyId) {
        return repo.find(
                "subjectId = ?1 AND correlationId = ?2 AND tenancyId = ?3 " +
                        "AND messageType IN ('COMMAND','HANDOFF') ORDER BY sequenceNumber DESC",
                channelId, correlationId, tenancyId(tenancyId))
                .firstResult()
                .map(Optional::ofNullable);
    }

    /**
     * Returns the earliest entry in a correlation thread in {@code tenancyId} with a non-null
     * {@code subjectId}. Cross-channel by design — scoped to correlationId and tenancyId.
     *
     * <p><strong>Known limitation:</strong> cross-tenant HANDOFF delegation loses subjectId
     * propagation at the tenant boundary. See qhorus#265 design spec.
     */
    public Uni<Optional<MessageLedgerEntry>> findEarliestWithSubjectByCorrelationId(
            final String correlationId, final String tenancyId) {
        return repo.find(
                "correlationId = ?1 AND subjectId IS NOT NULL AND tenancyId = ?2 " +
                        "ORDER BY occurredAt ASC, id ASC",
                correlationId, tenancyId(tenancyId))
                .firstResult()
                .map(Optional::ofNullable);
    }

    /**
     * Returns the ledger entry whose {@code messageId} matches the given surrogate PK.
     * No tenant parameter — PKs are unique within the datasource.
     */
    public Uni<Optional<MessageLedgerEntry>> findByMessageId(final Long messageId) {
        return repo.find("messageId = ?1", messageId)
                .firstResult()
                .map(Optional::ofNullable);
    }

    /** All entries for a channel in {@code tenancyId}, ordered by sequence number ascending. */
    public Uni<List<MessageLedgerEntry>> findByChannelId(final UUID channelId, final String tenancyId) {
        return repo.list("subjectId = ?1 AND tenancyId = ?2 ORDER BY sequenceNumber ASC",
                channelId, tenancyId(tenancyId));
    }

    private static String tenancyId(final String tenancyId) {
        return tenancyId != null ? tenancyId : TenancyConstants.DEFAULT_TENANT_ID;
    }
}
