package io.casehub.qhorus.runtime.ledger;

import io.casehub.ledger.api.spi.LedgerEntryRepository;
import io.casehub.qhorus.api.audit.AttestationSummary;
import io.casehub.qhorus.api.audit.LedgerEntryView;
import io.casehub.qhorus.api.store.ChannelStore;
import io.casehub.qhorus.api.store.LedgerReader;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@ApplicationScoped
public class LedgerReaderAdapter implements LedgerReader {

    @Inject
    MessageLedgerEntryRepository ledgerRepo;

    @Inject
    LedgerEntryRepository ledgerEntryRepository;

    @Inject
    ChannelStore channelStore;

    @Override
    public List<LedgerEntryView> listEntries(UUID channelId, Set<String> typeFilter,
            Long afterId, String senderId, Instant since,
            String correlationId, boolean sortDesc, int limit, String tenancyId) {
        return ledgerRepo.listEntries(channelId, typeFilter, afterId, senderId,
                since, correlationId, sortDesc, limit, tenancyId)
                .stream().map(e -> toView(e, null)).toList();
    }

    @Override
    public List<LedgerEntryView> findAllByCorrelationId(UUID channelId,
            String correlationId, String tenancyId) {
        return ledgerRepo.findAllByCorrelationId(channelId, correlationId, tenancyId)
                .stream().map(e -> toView(e, null)).toList();
    }

    @Override
    public List<LedgerEntryView> findAncestorChain(UUID channelId, UUID entryId,
            String tenancyId) {
        var chain = ledgerRepo.findAncestorChain(channelId, entryId, tenancyId);
        var ch = channelStore.find(channelId);
        String channelName = ch.map(c -> c.name()).orElse(null);
        return chain.stream().map(e -> toView(e, channelName)).toList();
    }

    @Override
    public List<LedgerEntryView> findAncestorChainCrossChannel(UUID entryId,
            String tenancyId) {
        var chain = ledgerRepo.findAncestorChainCrossChannel(entryId, tenancyId);
        Set<UUID> channelIds = chain.stream().map(e -> e.channelId)
                .collect(Collectors.toSet());
        Map<UUID, String> channelNames = channelStore.findByIds(channelIds).stream()
                .collect(Collectors.toMap(c -> c.id(), c -> c.name(), (a, b) -> a));
        return chain.stream()
                .map(e -> toView(e, channelNames.getOrDefault(e.channelId, "unknown")))
                .toList();
    }

    @Override
    public List<LedgerEntryView> findStalledCommands(UUID channelId, Instant cutoff,
            String tenancyId) {
        return ledgerRepo.findStalledCommands(channelId, cutoff, tenancyId)
                .stream().map(e -> toView(e, null)).toList();
    }

    @Override
    public Map<String, Long> countByOutcome(UUID channelId, String tenancyId) {
        return ledgerRepo.countByOutcome(channelId, tenancyId);
    }

    @Override
    public List<LedgerEntryView> findEventsSince(UUID channelId, Instant since,
            String tenancyId) {
        return ledgerRepo.findEventsSince(channelId, since, tenancyId)
                .stream().map(e -> toView(e, null)).toList();
    }

    @Override
    public List<LedgerEntryView> findByCorrelationIdAcrossChannels(String correlationId,
            int limit, String tenancyId) {
        var entries = ledgerRepo.findByCorrelationIdAcrossChannels(correlationId,
                limit, tenancyId);
        if (entries.isEmpty()) return List.of();
        Set<UUID> channelIds = entries.stream().map(e -> e.channelId)
                .collect(Collectors.toSet());
        Map<UUID, String> channelNames = channelStore.findByIds(channelIds).stream()
                .collect(Collectors.toMap(c -> c.id(), c -> c.name(), (a, b) -> a));
        return entries.stream()
                .map(e -> toView(e, channelNames.getOrDefault(e.channelId, "unknown")))
                .toList();
    }

    @Override
    public List<LedgerEntryView> findByMessageIds(Collection<Long> messageIds) {
        return ledgerRepo.findByMessageIds(messageIds)
                .stream().map(e -> toView(e, null)).toList();
    }

    @Override
    public Optional<LedgerEntryView> findEntryById(UUID entryId, String tenancyId) {
        return ledgerEntryRepository.findEntryById(entryId, tenancyId)
                .filter(e -> e instanceof MessageLedgerEntry)
                .map(e -> toView((MessageLedgerEntry) e, null));
    }

    @Override
    public Optional<LedgerEntryView> findLatestByCorrelationId(UUID channelId,
            String correlationId, String tenancyId) {
        return ledgerRepo.findLatestByCorrelationId(channelId, correlationId, tenancyId)
                .map(e -> toView(e, null));
    }

    @Override
    public List<AttestationSummary> findAttestationsByEntryId(UUID entryId,
            String tenancyId) {
        return ledgerEntryRepository.findAttestationsByEntryId(entryId, tenancyId)
                .stream()
                .map(a -> new AttestationSummary(
                        a.id,
                        a.verdict != null ? a.verdict.name() : null,
                        a.attestorId,
                        a.attestorRole != null ? a.attestorRole : "policy",
                        a.evidence,
                        a.confidence,
                        a.occurredAt))
                .toList();
    }

    static LedgerEntryView toView(MessageLedgerEntry e, String channelName) {
        return new LedgerEntryView(
                e.id,
                e.sequenceNumber,
                e.messageType,
                e.entryType != null ? e.entryType.name() : null,
                e.actorId,
                e.target,
                e.content,
                e.correlationId,
                e.commitmentId,
                e.causedByEntryId,
                e.occurredAt,
                e.messageId,
                e.toolName,
                e.durationMs,
                e.tokenCount,
                e.contextRefs,
                e.sourceEntity,
                e.channelId,
                channelName);
    }
}
