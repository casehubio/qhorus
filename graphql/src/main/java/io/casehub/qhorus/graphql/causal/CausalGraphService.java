package io.casehub.qhorus.graphql.causal;

import io.casehub.platform.api.identity.CurrentPrincipal;
import io.casehub.qhorus.api.channel.Channel;
import io.casehub.qhorus.api.spi.causal.CausalGraphApi;
import io.casehub.qhorus.api.store.ChannelStore;
import io.casehub.qhorus.runtime.ledger.MessageLedgerEntry;
import io.casehub.qhorus.runtime.ledger.MessageLedgerEntryRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@ApplicationScoped
public class CausalGraphService implements CausalGraphApi {

    @Inject
    io.casehub.qhorus.runtime.ledger.CausalGraphService causalGraphService;

    @Inject
    MessageLedgerEntryRepository ledgerRepo;

    @Inject
    ChannelStore channelStore;

    @Inject
    CurrentPrincipal currentPrincipal;

    @Override
    @Transactional
    public Map<String, Object> getGraph(String correlationId, Integer limit) {
        int effectiveLimit = Math.min(Math.max(limit != null ? limit : 100, 1), 500);
        return causalGraphService.buildGraph(
                correlationId, effectiveLimit, currentPrincipal.tenancyId());
    }

    @Override
    @Transactional
    public List<Map<String, Object>> getAttribution(String entryId) {
        UUID entryUuid = UUID.fromString(entryId);

        List<MessageLedgerEntry> chain = ledgerRepo.findAncestorChainCrossChannel(
                entryUuid, currentPrincipal.tenancyId());

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
