package io.casehub.qhorus.api.store;

import io.casehub.qhorus.api.audit.AttestationSummary;
import io.casehub.qhorus.api.audit.LedgerEntryView;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface LedgerReader {

    List<LedgerEntryView> listEntries(UUID channelId, Set<String> typeFilter,
            Long afterId, String senderId, Instant since,
            String correlationId, boolean sortDesc, int limit, String tenancyId);

    List<LedgerEntryView> findAllByCorrelationId(UUID channelId,
            String correlationId, String tenancyId);

    List<LedgerEntryView> findAncestorChain(UUID channelId, UUID entryId,
            String tenancyId);

    List<LedgerEntryView> findAncestorChainCrossChannel(UUID entryId,
            String tenancyId);

    List<LedgerEntryView> findStalledCommands(UUID channelId, Instant cutoff,
            String tenancyId);

    Map<String, Long> countByOutcome(UUID channelId, String tenancyId);

    List<LedgerEntryView> findEventsSince(UUID channelId, Instant since,
            String tenancyId);

    List<LedgerEntryView> findByCorrelationIdAcrossChannels(String correlationId,
            int limit, String tenancyId);

    List<LedgerEntryView> findByMessageIds(Collection<Long> messageIds);

    Optional<LedgerEntryView> findEntryById(UUID entryId, String tenancyId);

    Optional<LedgerEntryView> findLatestByCorrelationId(UUID channelId,
            String correlationId, String tenancyId);

    List<AttestationSummary> findAttestationsByEntryId(UUID entryId,
            String tenancyId);
}
