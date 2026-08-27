package io.casehub.qhorus.compliance.report;

import io.casehub.ledger.api.model.LedgerAttestation;
import io.casehub.ledger.api.spi.LedgerEntryRepository;
import io.casehub.ledger.runtime.service.LedgerVerificationService;
import io.casehub.ledger.runtime.service.TrustGateService;
import io.casehub.qhorus.api.judgment.JudgmentEventKinds;
import io.casehub.qhorus.compliance.model.AttributionEdge;
import io.casehub.qhorus.compliance.model.AttributionNode;
import io.casehub.qhorus.compliance.model.JudgmentAttributionReport;
import io.casehub.qhorus.compliance.model.JudgmentEvent;
import io.casehub.qhorus.runtime.ledger.CausalGraphService;
import io.casehub.qhorus.runtime.ledger.CausalGraphService.CausalGraph;
import io.casehub.qhorus.runtime.ledger.CausalGraphService.GraphNode;
import io.casehub.qhorus.runtime.ledger.MessageLedgerEntry;
import io.casehub.qhorus.runtime.ledger.MessageLedgerEntryRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

@ApplicationScoped
public class JudgmentAttributionReportService {

    @Inject MessageLedgerEntryRepository ledgerRepo;
    @Inject CausalGraphService causalGraphService;
    @Inject LedgerEntryRepository ledgerEntryRepository;
    @Inject Instance<TrustGateService> trustGateServiceInstance;
    @Inject Instance<LedgerVerificationService> verificationServiceInstance;

    public JudgmentAttributionReport generate(UUID judgmentId, int limit, String tenancyId) {
        List<MessageLedgerEntry> entries = ledgerRepo.findJudgmentEvents(
                null, judgmentId, null, null, tenancyId);

        if (entries.isEmpty()) {
            return new JudgmentAttributionReport(
                    judgmentId.toString(), null, 0, List.of(), null, null, null,
                    List.of(), List.of(), List.of(), null, Instant.now(), 1);
        }

        MessageLedgerEntry yielded = entries.stream()
                .filter(e -> JudgmentEventKinds.YIELDED.equals(e.toolName))
                .findFirst().orElse(entries.getFirst());

        String judgmentType = yielded.judgmentType;
        String correlationId = yielded.correlationId;

        String verificationOutcome = entries.stream()
                .filter(e -> JudgmentEventKinds.VERIFIED.equals(e.toolName))
                .map(e -> e.verificationOutcome)
                .findFirst().orElse(null);

        Long totalDurationMs = null;
        if (entries.size() >= 2) {
            Instant first = entries.getFirst().occurredAt;
            Instant last = entries.getLast().occurredAt;
            if (first != null && last != null) {
                totalDurationMs = Duration.between(first, last).toMillis();
            }
        }

        List<JudgmentEvent> events = entries.stream()
                .map(this::toJudgmentEvent)
                .toList();

        List<AttributionNode> causalNodes = List.of();
        List<AttributionEdge> causalEdges = List.of();
        Set<String> channels = new TreeSet<>();

        if (correlationId != null) {
            CausalGraph graph = causalGraphService.buildGraph(correlationId, limit, tenancyId);
            causalNodes = graph.nodes().stream()
                    .map(n -> enrichNode(n, tenancyId))
                    .toList();
            causalEdges = graph.edges().stream()
                    .map(e -> new AttributionEdge(e.from(), e.to(), e.type(), e.elapsedMs()))
                    .toList();
            graph.channels().forEach(channels::add);
        }

        entries.stream()
                .map(e -> e.channelId)
                .filter(Objects::nonNull)
                .map(UUID::toString)
                .forEach(channels::add);

        String merkleRoot = buildMerkleRoot(channels, tenancyId);

        return new JudgmentAttributionReport(
                judgmentId.toString(), judgmentType,
                channels.size(), new ArrayList<>(channels), correlationId,
                verificationOutcome, totalDurationMs,
                events, causalNodes, causalEdges, merkleRoot, Instant.now(), 1);
    }

    private JudgmentEvent toJudgmentEvent(MessageLedgerEntry e) {
        Double trustScore = null;
        if (trustGateServiceInstance.isResolvable()) {
            OptionalDouble score = trustGateServiceInstance.get().currentScore(e.actorId);
            if (score.isPresent()) {
                trustScore = score.getAsDouble();
            }
        }
        return new JudgmentEvent(
                e.toolName, e.actorId, e.occurredAt,
                e.evidenceQuality, e.verificationOutcome,
                null, trustScore, e.durationMs);
    }

    private AttributionNode enrichNode(GraphNode node, String tenancyId) {
        UUID entryId = UUID.fromString(node.entryId());
        Double trustScore = null;
        if (trustGateServiceInstance.isResolvable()) {
            OptionalDouble score = trustGateServiceInstance.get().currentScore(node.actorId());
            if (score.isPresent()) {
                trustScore = score.getAsDouble();
            }
        }
        String attestationVerdict = null;
        List<LedgerAttestation> attestations = ledgerEntryRepository.findAttestationsByEntryId(entryId, tenancyId);
        if (!attestations.isEmpty()) {
            attestationVerdict = attestations.getLast().verdict.name();
        }
        return new AttributionNode(
                node.entryId(), node.channelId(), node.channelName(),
                node.messageType(), node.actorId(), node.occurredAt(),
                node.content(), node.causedByEntryId(), node.depth(),
                trustScore, attestationVerdict, null, null, null);
    }

    private String buildMerkleRoot(Set<String> channels, String tenancyId) {
        if (!verificationServiceInstance.isResolvable()) {
            return null;
        }
        LedgerVerificationService service = verificationServiceInstance.get();
        List<String> parts = new ArrayList<>();
        for (String chId : channels) {
            try {
                String root = service.treeRoot(UUID.fromString(chId), tenancyId);
                parts.add(chId + "=" + root);
            } catch (Exception e) {
                // No frontier — skip
            }
        }
        return parts.isEmpty() ? null : String.join(";", parts);
    }
}
