package io.casehub.qhorus.graphql.audit;

import io.casehub.ledger.api.model.AttestationVerdict;
import io.casehub.platform.api.identity.ActorType;
import io.casehub.qhorus.api.audit.AttestationSummary;
import io.casehub.qhorus.api.audit.CausalChainEntry;
import io.casehub.qhorus.api.audit.CausalGraph;
import io.casehub.qhorus.api.audit.LedgerEntryView;
import io.casehub.qhorus.api.audit.ObligationChainSummary;
import io.casehub.qhorus.api.audit.ObligationStats;
import io.casehub.qhorus.api.audit.PeerAttestor;
import io.casehub.qhorus.api.audit.PeerReviewResult;
import io.casehub.qhorus.api.audit.ReviewerProvider;
import io.casehub.qhorus.api.audit.StalledObligation;
import io.casehub.qhorus.api.audit.TelemetrySummary;
import io.casehub.qhorus.api.audit.TimelineEntry;
import io.casehub.qhorus.api.audit.ToolTelemetry;
import io.casehub.qhorus.api.message.Message;
import io.casehub.qhorus.api.message.MessageDispatch;
import io.casehub.qhorus.api.message.MessageDispatcher;
import io.casehub.qhorus.api.message.MessageType;
import io.casehub.qhorus.api.spi.audit.AuditApi;
import io.casehub.qhorus.api.store.CausalGraphReader;
import io.casehub.qhorus.api.store.CommitmentReader;
import io.casehub.qhorus.api.store.LedgerReader;
import io.casehub.qhorus.api.store.MessageReader;
import io.casehub.qhorus.api.store.query.MessageQuery;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@ApplicationScoped
public class AuditService implements AuditApi {

    private final LedgerReader ledgerReader;
    private final CausalGraphReader causalGraphReader;
    private final CommitmentReader commitmentReader;
    private final MessageReader messageReader;
    private final PeerAttestor peerAttestor;
    private final ReviewerProvider reviewerProvider;
    private final MessageDispatcher messageDispatcher;

    public AuditService(LedgerReader ledgerReader,
            CausalGraphReader causalGraphReader,
            CommitmentReader commitmentReader,
            MessageReader messageReader,
            PeerAttestor peerAttestor,
            ReviewerProvider reviewerProvider,
            MessageDispatcher messageDispatcher) {
        this.ledgerReader = ledgerReader;
        this.causalGraphReader = causalGraphReader;
        this.commitmentReader = commitmentReader;
        this.messageReader = messageReader;
        this.peerAttestor = peerAttestor;
        this.reviewerProvider = reviewerProvider;
        this.messageDispatcher = messageDispatcher;
    }

    @Override
    public List<LedgerEntryView> ledgerEntries(UUID channelId, String typeFilter,
            String sender, String since, Long afterId, String correlationId,
            String sort, Integer limit) {
        return ledgerEntries(channelId, typeFilter, sender, since, afterId,
                correlationId, sort, limit, null);
    }

    public List<LedgerEntryView> ledgerEntries(UUID channelId, String typeFilter,
            String sender, String since, Long afterId, String correlationId,
            String sort, Integer limit, String tenancyId) {

        Set<String> types = null;
        if (typeFilter != null && !typeFilter.isBlank()) {
            types = Arrays.stream(typeFilter.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toSet());
        }

        int effectiveLimit = (limit != null && limit > 0) ? Math.min(limit, 100) : 20;

        Instant sinceInstant = null;
        if (since != null && !since.isBlank()) {
            try {
                sinceInstant = Instant.parse(since);
            } catch (java.time.format.DateTimeParseException e) {
                throw new IllegalArgumentException(
                        "Invalid 'since' timestamp '" + since
                                + "' — use ISO-8601 format, e.g. 2026-04-15T10:00:00Z");
            }
        }

        boolean sortDesc;
        if (sort == null || sort.isBlank() || "asc".equalsIgnoreCase(sort)) {
            sortDesc = false;
        } else if ("desc".equalsIgnoreCase(sort)) {
            sortDesc = true;
        } else {
            throw new IllegalArgumentException(
                    "Invalid sort value '" + sort + "' — use 'asc' or 'desc'");
        }

        return ledgerReader.listEntries(channelId, types, afterId, sender,
                sinceInstant, correlationId, sortDesc, effectiveLimit, tenancyId);
    }

    @Override
    public ObligationChainSummary obligationChain(UUID channelId,
            String correlationId) {
        return obligationChain(channelId, correlationId, null);
    }

    public ObligationChainSummary obligationChain(UUID channelId,
            String correlationId, String tenancyId) {

        var chain = ledgerReader.findAllByCorrelationId(channelId,
                correlationId, tenancyId);

        if (chain.isEmpty()) {
            return new ObligationChainSummary(correlationId, null, null,
                    null, null, null, List.of(), 0, null);
        }

        var first = chain.get(0);
        String initiator = first.actorId();

        Set<String> terminal = Set.of("DONE", "FAILURE", "DECLINE");
        var terminalEntry = chain.stream()
                .filter(e -> terminal.contains(e.messageType()))
                .findFirst()
                .orElse(null);

        String resolution = terminalEntry != null ? terminalEntry.messageType() : null;
        Instant resolvedAt = terminalEntry != null ? terminalEntry.occurredAt() : null;
        Long elapsedSeconds = (terminalEntry != null && first.occurredAt() != null
                && terminalEntry.occurredAt() != null)
                        ? terminalEntry.occurredAt().getEpochSecond()
                                - first.occurredAt().getEpochSecond()
                        : null;

        List<String> participants = chain.stream()
                .map(LedgerEntryView::actorId)
                .distinct()
                .collect(Collectors.toList());

        int handoffCount = (int) chain.stream()
                .filter(e -> "HANDOFF".equals(e.messageType()))
                .count();

        var commitment = commitmentReader.findByCorrelationId(correlationId)
                .orElse(null);

        return new ObligationChainSummary(correlationId, initiator,
                first.occurredAt(), resolvedAt, elapsedSeconds, resolution,
                participants, handoffCount, commitment);
    }

    @Override
    public List<CausalChainEntry> causalChain(UUID channelId,
            UUID ledgerEntryId) {
        return causalChain(channelId, ledgerEntryId, null);
    }

    public List<CausalChainEntry> causalChain(UUID channelId,
            UUID ledgerEntryId, String tenancyId) {

        List<LedgerEntryView> chain;
        if (channelId != null) {
            chain = ledgerReader.findAncestorChain(channelId, ledgerEntryId,
                    tenancyId);
        } else {
            chain = ledgerReader.findAncestorChainCrossChannel(ledgerEntryId,
                    tenancyId);
        }

        return chain.stream()
                .map(e -> new CausalChainEntry(e.entryId(), e.channelId(),
                        e.channelName(), e.messageType(), e.actorId(),
                        e.correlationId(), e.occurredAt(), e.content(),
                        e.causedByEntryId()))
                .toList();
    }

    @Override
    public CausalGraph causalGraph(String correlationId, Integer limit) {
        return causalGraph(correlationId, limit, null);
    }

    public CausalGraph causalGraph(String correlationId, Integer limit,
            String tenancyId) {
        int effectiveLimit = (limit != null && limit > 0)
                ? Math.min(limit, 500) : 100;
        return causalGraphReader.buildGraph(correlationId, effectiveLimit,
                tenancyId);
    }

    @Override
    public String renderedCausalGraph(String correlationId, Integer limit) {
        return renderedCausalGraph(correlationId, limit, null);
    }

    public String renderedCausalGraph(String correlationId, Integer limit,
            String tenancyId) {
        int effectiveLimit = (limit != null && limit > 0)
                ? Math.min(limit, 500) : 200;
        return causalGraphReader.renderGraph(correlationId, effectiveLimit,
                tenancyId);
    }

    @Override
    public List<StalledObligation> stalledObligations(UUID channelId,
            Integer olderThanSeconds) {
        return stalledObligations(channelId, olderThanSeconds, null);
    }

    public List<StalledObligation> stalledObligations(UUID channelId,
            Integer olderThanSeconds, String tenancyId) {

        int threshold = olderThanSeconds != null ? olderThanSeconds : 30;
        Instant cutoff = Instant.now().minusSeconds(threshold);
        Instant now = Instant.now();

        return ledgerReader.findStalledCommands(channelId, cutoff, tenancyId)
                .stream()
                .map(e -> {
                    long stalledFor = e.occurredAt() != null
                            ? now.getEpochSecond() - e.occurredAt().getEpochSecond()
                            : 0L;
                    return new StalledObligation(e.correlationId(),
                            e.actorId(), e.content(), e.occurredAt(),
                            stalledFor);
                })
                .toList();
    }

    @Override
    public ObligationStats obligationStats(UUID channelId) {
        return obligationStats(channelId, null);
    }

    public ObligationStats obligationStats(UUID channelId, String tenancyId) {

        Map<String, Long> counts = ledgerReader.countByOutcome(channelId,
                tenancyId);
        long total = counts.getOrDefault("COMMAND", 0L);
        long fulfilled = counts.getOrDefault("DONE", 0L);
        long failed = counts.getOrDefault("FAILURE", 0L);
        long declined = counts.getOrDefault("DECLINE", 0L);
        long delegated = counts.getOrDefault("HANDOFF", 0L);
        long stillOpen = Math.max(0L, total - fulfilled - failed - declined
                - delegated);
        long stalled = ledgerReader
                .findStalledCommands(channelId,
                        Instant.now().minusSeconds(30), tenancyId)
                .size();
        double rate = total > 0 ? (double) fulfilled / total : 0.0;

        return new ObligationStats((int) total, (int) fulfilled, (int) failed,
                (int) declined, (int) delegated, (int) stillOpen, (int) stalled,
                rate);
    }

    @Override
    public TelemetrySummary telemetrySummary(UUID channelId, String since) {
        return telemetrySummary(channelId, since, null);
    }

    public TelemetrySummary telemetrySummary(UUID channelId, String since,
            String tenancyId) {

        Instant sinceInstant = null;
        if (since != null && !since.isBlank()) {
            try {
                sinceInstant = Instant.parse(since);
            } catch (java.time.format.DateTimeParseException e) {
                throw new IllegalArgumentException(
                        "Invalid 'since' timestamp '" + since
                                + "' — use ISO-8601 format");
            }
        }

        var events = ledgerReader.findEventsSince(channelId, sinceInstant,
                tenancyId);

        if (events.isEmpty()) {
            return new TelemetrySummary(0, Map.of(), 0L, 0L);
        }

        LinkedHashMap<String, long[]> agg = new LinkedHashMap<>();
        for (var e : events) {
            long[] acc = agg.computeIfAbsent(e.toolName(), k -> new long[3]);
            acc[0]++;
            acc[1] += e.durationMs() != null ? e.durationMs() : 0;
            acc[2] += e.tokenCount() != null ? e.tokenCount() : 0;
        }

        Map<String, ToolTelemetry> byTool = new LinkedHashMap<>();
        for (var entry : agg.entrySet()) {
            long[] acc = entry.getValue();
            byTool.put(entry.getKey(),
                    new ToolTelemetry((int) acc[0],
                            acc[0] > 0 ? acc[1] / acc[0] : 0L, acc[2]));
        }

        long totalTokens = events.stream()
                .mapToLong(e -> e.tokenCount() != null ? e.tokenCount() : 0L)
                .sum();
        long totalDuration = events.stream()
                .mapToLong(e -> e.durationMs() != null ? e.durationMs() : 0L)
                .sum();

        return new TelemetrySummary(events.size(), byTool, totalTokens,
                totalDuration);
    }

    @Override
    public List<TimelineEntry> channelTimeline(UUID channelId, Long afterId,
            Integer limit) {
        return channelTimeline(channelId, afterId, limit, null);
    }

    public List<TimelineEntry> channelTimeline(UUID channelId, Long afterId,
            Integer limit, String tenancyId) {

        int effectiveLimit = (limit != null && limit > 0)
                ? Math.min(limit, 200) : 50;

        var messages = messageReader.scan(
                MessageQuery.poll(channelId, afterId, effectiveLimit));

        var eventIds = messages.stream()
                .filter(m -> m.messageType() == MessageType.EVENT)
                .map(Message::id)
                .toList();

        Map<Long, LedgerEntryView> ledgerByMessageId = eventIds.isEmpty()
                ? Map.of()
                : ledgerReader.findByMessageIds(eventIds).stream()
                        .collect(Collectors.toMap(
                                LedgerEntryView::messageId, e -> e));

        return messages.stream()
                .map(m -> toTimelineEntry(m,
                        m.messageType() == MessageType.EVENT
                                ? ledgerByMessageId.get(m.id()) : null))
                .toList();
    }

    @Override
    public List<LedgerEntryView> obligationActivity(String correlationId,
            Integer limit) {
        return obligationActivity(correlationId, limit, null);
    }

    public List<LedgerEntryView> obligationActivity(String correlationId,
            Integer limit, String tenancyId) {

        int effectiveLimit = (limit != null && limit > 0)
                ? Math.min(limit, 500) : 100;

        return ledgerReader.findByCorrelationIdAcrossChannels(correlationId,
                effectiveLimit, tenancyId);
    }

    @Override
    public List<AttestationSummary> attestations(UUID entryId) {
        return attestations(entryId, null);
    }

    public List<AttestationSummary> attestations(UUID entryId,
            String tenancyId) {
        return ledgerReader.findAttestationsByEntryId(entryId, tenancyId);
    }

    @Override
    public AttestationSummary attest(UUID entryId, String verdict,
            String evidence) {
        return attest(entryId, verdict, evidence, null, null);
    }

    public AttestationSummary attest(UUID entryId, String verdict,
            String evidence, String attestorId, String tenancyId) {
        AttestationVerdict v = AttestationVerdict.valueOf(
                verdict.toUpperCase());
        return peerAttestor.write(entryId, v, evidence, attestorId, tenancyId);
    }

    @Override
    public PeerReviewResult requestPeerReview(UUID entryId,
            List<String> reviewerIds, UUID channelId) {
        return requestPeerReview(entryId, reviewerIds, channelId, null, null);
    }

    public PeerReviewResult requestPeerReview(UUID entryId,
            List<String> reviewerIds, UUID channelId, String actorId,
            String tenancyId) {

        var entry = ledgerReader.findEntryById(entryId, tenancyId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Ledger entry not found: " + entryId));

        if (!"COMMAND".equals(entry.messageType())
                && !"HANDOFF".equals(entry.messageType())) {
            throw new IllegalArgumentException(
                    "Entry must be COMMAND or HANDOFF, not "
                            + entry.messageType());
        }

        UUID resolvedChannelId = channelId != null ? channelId : entry.channelId();
        List<String> reviewers = reviewerProvider.resolve(resolvedChannelId,
                reviewerIds, entryId, tenancyId);
        if (reviewers.isEmpty()) {
            return new PeerReviewResult(0, List.of(),
                    "No reviewers resolved — configure channel reviewers or "
                            + "register instances with peer-reviewer capability.");
        }

        String completionContent = null;
        if (entry.correlationId() != null) {
            var terminalEntry = ledgerReader.findLatestByCorrelationId(
                    entry.channelId(), entry.correlationId(), tenancyId);
            if (terminalEntry.isPresent()) {
                completionContent = terminalEntry.get().content();
            }
        }

        var sentReviews = new java.util.ArrayList<PeerReviewResult.ReviewDispatch>();
        for (String reviewerId : reviewers) {
            try {
                String corrId = UUID.randomUUID().toString();
                String content = "{\"peer_review\":{\"ledger_entry_id\":\""
                        + entryId + "\",\"original_command\":\""
                        + escapeJson(entry.content())
                        + "\",\"completion_content\":\""
                        + escapeJson(completionContent) + "\"}}";

                messageDispatcher.dispatch(MessageDispatch.builder()
                        .channelId(resolvedChannelId)
                        .sender(actorId != null ? actorId : "system:audit")
                        .type(MessageType.QUERY)
                        .content(content)
                        .correlationId(corrId)
                        .target(reviewerId)
                        .actorType(ActorType.SYSTEM)
                        .tenancyId(tenancyId)
                        .build());
                sentReviews.add(new PeerReviewResult.ReviewDispatch(
                        reviewerId, corrId));
            } catch (Exception ignored) {
            }
        }

        return new PeerReviewResult(sentReviews.size(), sentReviews, null);
    }

    private TimelineEntry toTimelineEntry(Message m,
            LedgerEntryView ledgerEntry) {
        if (m.messageType() == MessageType.EVENT) {
            return new TimelineEntry(m.id(), "EVENT", m.createdAt(),
                    m.sender(), null, null, null, null, null, 0,
                    null, null,
                    ledgerEntry != null ? ledgerEntry.toolName() : null,
                    ledgerEntry != null ? ledgerEntry.durationMs() : null,
                    ledgerEntry != null ? ledgerEntry.tokenCount() : null);
        }
        return new TimelineEntry(m.id(), "MESSAGE", m.createdAt(),
                m.sender(),
                m.messageType() != null ? m.messageType().name().toLowerCase()
                        : null,
                m.content(), m.correlationId(), m.inReplyTo(), m.target(),
                m.replyCount(), m.topic(), m.deadline(),
                null, null, null);
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }
}
