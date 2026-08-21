package io.casehub.qhorus.runtime.ledger;

import io.casehub.qhorus.api.channel.Channel;
import io.casehub.qhorus.api.store.ChannelStore;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@ApplicationScoped
public class CausalGraphService {

    @Inject
    MessageLedgerEntryRepository ledgerRepo;

    @Inject
    ChannelStore channelStore;

    public record CausalGraph(
            String correlationId,
            String rootEntryId,
            int channelCount,
            List<String> channels,
            Long totalDurationMs,
            String outcome,
            boolean truncated,
            List<GraphNode> nodes,
            List<GraphEdge> edges) {
    }

    public record GraphNode(
            String entryId,
            String channelId,
            String channelName,
            String messageType,
            String actorId,
            String occurredAt,
            String content,
            String causedByEntryId,
            int depth) {
    }

    public record GraphEdge(
            String from,
            String to,
            String type,
            Long elapsedMs) {
    }

    private static final Set<String> TERMINAL_TYPES = Set.of("DONE", "FAILURE", "DECLINE");

    @Transactional
    public CausalGraph buildGraph(String correlationId, int limit, String tenancyId) {
        List<MessageLedgerEntry> entries =
                ledgerRepo.findByCorrelationIdAcrossChannels(correlationId, limit, tenancyId);

        if (entries.isEmpty()) {
            return new CausalGraph(correlationId, null, 0, List.of(), null, "OPEN",
                    false, List.of(), List.of());
        }

        boolean truncated = entries.size() >= limit;

        Map<UUID, MessageLedgerEntry> byId = new LinkedHashMap<>();
        for (MessageLedgerEntry e : entries) {
            byId.put(e.id, e);
        }

        List<GraphEdge> edges = new ArrayList<>();
        for (MessageLedgerEntry e : entries) {
            if (e.causedByEntryId != null && byId.containsKey(e.causedByEntryId)) {
                MessageLedgerEntry parent = byId.get(e.causedByEntryId);
                Long elapsed = (e.occurredAt != null && parent.occurredAt != null)
                        ? e.occurredAt.toEpochMilli() - parent.occurredAt.toEpochMilli()
                        : null;
                edges.add(new GraphEdge(
                        parent.id.toString(), e.id.toString(), "CAUSED_BY", elapsed));
            }
        }

        MessageLedgerEntry root = entries.stream()
                .filter(e -> e.causedByEntryId == null || !byId.containsKey(e.causedByEntryId))
                .min(Comparator.comparing(e -> e.occurredAt != null ? e.occurredAt : Instant.MAX))
                .orElse(null);

        Map<UUID, Integer> depthMap = new HashMap<>();
        if (root != null) {
            depthMap.put(root.id, 0);
            Map<UUID, List<UUID>> children = new HashMap<>();
            for (MessageLedgerEntry e : entries) {
                if (e.causedByEntryId != null && byId.containsKey(e.causedByEntryId)) {
                    children.computeIfAbsent(e.causedByEntryId, k -> new ArrayList<>()).add(e.id);
                }
            }
            Queue<UUID> queue = new ArrayDeque<>();
            queue.add(root.id);
            while (!queue.isEmpty()) {
                UUID current = queue.poll();
                int currentDepth = depthMap.get(current);
                for (UUID childId : children.getOrDefault(current, List.of())) {
                    if (!depthMap.containsKey(childId)) {
                        depthMap.put(childId, currentDepth + 1);
                        queue.add(childId);
                    }
                }
            }
        }

        Set<UUID> channelIds = entries.stream()
                .map(e -> e.channelId)
                .collect(Collectors.toSet());
        Map<UUID, String> channelNames = channelStore.findByIds(channelIds).stream()
                .collect(Collectors.toMap(Channel::id, Channel::name, (a, b) -> a));

        List<GraphNode> nodes = entries.stream()
                .map(e -> new GraphNode(
                        e.id.toString(),
                        e.channelId.toString(),
                        channelNames.getOrDefault(e.channelId, "unknown"),
                        e.messageType,
                        e.actorId,
                        e.occurredAt != null ? e.occurredAt.toString() : null,
                        e.content,
                        e.causedByEntryId != null ? e.causedByEntryId.toString() : null,
                        depthMap.getOrDefault(e.id, -1)))
                .toList();

        List<MessageLedgerEntry> terminals = entries.stream()
                .filter(e -> TERMINAL_TYPES.contains(e.messageType))
                .toList();
        String outcome;
        if (terminals.isEmpty()) {
            outcome = "OPEN";
        } else if (terminals.stream().anyMatch(e -> "FAILURE".equals(e.messageType))) {
            outcome = "FAILED";
        } else if (terminals.stream().anyMatch(e -> "DECLINE".equals(e.messageType))) {
            outcome = "DECLINED";
        } else {
            outcome = "FULFILLED";
        }

        Long totalDurationMs = null;
        if (root != null && root.occurredAt != null && !terminals.isEmpty()) {
            Instant latestTerminal = terminals.stream()
                    .map(e -> e.occurredAt)
                    .filter(Objects::nonNull)
                    .max(Instant::compareTo)
                    .orElse(null);
            if (latestTerminal != null) {
                totalDurationMs = latestTerminal.toEpochMilli() - root.occurredAt.toEpochMilli();
            }
        }

        List<String> channelNameList = channelIds.stream()
                .map(id -> channelNames.getOrDefault(id, "unknown"))
                .sorted()
                .toList();

        return new CausalGraph(
                correlationId,
                root != null ? root.id.toString() : null,
                channelIds.size(),
                channelNameList,
                totalDurationMs,
                outcome,
                truncated,
                nodes,
                edges);
    }
}
