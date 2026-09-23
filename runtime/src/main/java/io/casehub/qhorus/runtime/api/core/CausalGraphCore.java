package io.casehub.qhorus.runtime.api.core;

import io.casehub.platform.api.identity.CurrentPrincipal;
import io.casehub.qhorus.api.channel.Channel;
import io.casehub.qhorus.api.store.ChannelStore;
import io.casehub.qhorus.runtime.ledger.CausalGraphService;
import io.casehub.qhorus.runtime.ledger.MessageLedgerEntry;
import io.casehub.qhorus.runtime.ledger.MessageLedgerEntryRepository;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import jakarta.transaction.Transactional;

public class CausalGraphCore {

    private final CausalGraphService causalGraphService;
    private final MessageLedgerEntryRepository ledgerRepo;
    private final ChannelStore channelStore;
    private final CurrentPrincipal currentPrincipal;

    public CausalGraphCore(CausalGraphService causalGraphService,
                           MessageLedgerEntryRepository ledgerRepo,
                           ChannelStore channelStore,
                           CurrentPrincipal currentPrincipal) {
        this.causalGraphService = causalGraphService;
        this.ledgerRepo = ledgerRepo;
        this.channelStore = channelStore;
        this.currentPrincipal = currentPrincipal;
    }

    @Transactional
    public Object getGraph(String correlationId, int limit) {
        int effectiveLimit = Math.min(Math.max(limit, 1), 500);
        return causalGraphService.buildGraph(
                correlationId, effectiveLimit, currentPrincipal.tenancyId());
    }

    @Transactional
    public List<Map<String, Object>> getAttribution(String entryId) {
        List<MessageLedgerEntry> chain = ledgerRepo.findAncestorChainCrossChannel(
                UUID.fromString(entryId), currentPrincipal.tenancyId());

        Set<UUID> channelIds = chain.stream()
                .map(e -> e.channelId).collect(Collectors.toSet());
        Map<UUID, String> names = channelStore.findByIds(channelIds).stream()
                .collect(Collectors.toMap(Channel::id, Channel::name, (a, b) -> a));

        return chain.stream().map(e -> {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("entryId", e.id.toString());
            map.put("channelId", e.channelId.toString());
            map.put("channelName", names.getOrDefault(e.channelId, "unknown"));
            map.put("messageType", e.messageType);
            map.put("actorId", e.actorId != null ? e.actorId : "");
            map.put("correlationId", e.correlationId != null ? e.correlationId : "");
            map.put("occurredAt", e.occurredAt != null ? e.occurredAt.toString() : "");
            map.put("content", e.content != null ? e.content : "");
            map.put("causedByEntryId", e.causedByEntryId != null ? e.causedByEntryId.toString() : "");
            return map;
        }).toList();
    }
}
