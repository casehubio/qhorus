package io.casehub.qhorus.compliance.report;

import io.casehub.ledger.api.model.LedgerAttestation;
import io.casehub.ledger.api.spi.LedgerEntryRepository;
import io.casehub.ledger.runtime.service.ComplianceReport;
import io.casehub.ledger.runtime.service.DecisionRecord;
import io.casehub.ledger.runtime.service.LedgerComplianceReportService;
import io.casehub.ledger.runtime.service.LedgerVerificationService;
import io.casehub.ledger.runtime.service.TrustGateService;
import io.casehub.qhorus.compliance.model.AttributionEdge;
import io.casehub.qhorus.compliance.model.AttributionNode;
import io.casehub.qhorus.compliance.model.AttributionReport;
import io.casehub.qhorus.runtime.ledger.CausalGraphService;
import io.casehub.qhorus.runtime.ledger.CausalGraphService.CausalGraph;
import io.casehub.qhorus.runtime.ledger.CausalGraphService.GraphNode;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;

@ApplicationScoped
public class AttributionReportService {

    @Inject CausalGraphService causalGraphService;
    @Inject Instance<TrustGateService> trustGateServiceInstance;
    @Inject LedgerEntryRepository ledgerEntryRepository;
    @Inject Instance<LedgerVerificationService> verificationServiceInstance;
    @Inject Instance<LedgerComplianceReportService> complianceReportServiceInstance;

    public AttributionReport generate(String correlationId, int limit, String tenancyId) {
        CausalGraph graph = causalGraphService.buildGraph(correlationId, limit, tenancyId);

        if (graph.nodes().isEmpty()) {
            return new AttributionReport(
                    correlationId, null, 0, List.of(), null, graph.outcome(),
                    List.of(), List.of(), null, Instant.now(), 1);
        }

        Map<UUID, DecisionRecord> decisionMap = buildDecisionMap(graph, tenancyId);

        List<AttributionNode> enrichedNodes = graph.nodes().stream()
                .map(node -> enrichNode(node, decisionMap, tenancyId))
                .toList();

        List<AttributionEdge> edges = graph.edges().stream()
                .map(e -> new AttributionEdge(e.from(), e.to(), e.type(), e.elapsedMs()))
                .toList();

        String merkleRoot = buildCompositeMerkleRoot(graph, tenancyId);

        return new AttributionReport(
                graph.correlationId(), graph.rootEntryId(), graph.channelCount(),
                graph.channels(), graph.totalDurationMs(), graph.outcome(),
                enrichedNodes, edges, merkleRoot, Instant.now(), 1);
    }

    private Map<UUID, DecisionRecord> buildDecisionMap(CausalGraph graph, String tenancyId) {
        if (!complianceReportServiceInstance.isResolvable()) {
            return Map.of();
        }
        LedgerComplianceReportService service = complianceReportServiceInstance.get();

        Instant from = null;
        Instant to = null;
        for (GraphNode node : graph.nodes()) {
            if (node.occurredAt() != null) {
                Instant t = Instant.parse(node.occurredAt());
                if (from == null || t.isBefore(from)) from = t;
                if (to == null || t.isAfter(to)) to = t;
            }
        }
        if (from == null) {
            return Map.of();
        }

        Set<UUID> channelIds = graph.nodes().stream()
                .map(n -> UUID.fromString(n.channelId()))
                .collect(Collectors.toSet());

        Map<UUID, DecisionRecord> result = new HashMap<>();
        for (UUID channelId : channelIds) {
            try {
                ComplianceReport report = service.reportForSubject(channelId, from, to, tenancyId);
                for (DecisionRecord dr : report.decisions()) {
                    if (dr.entryId() != null) {
                        result.put(dr.entryId(), dr);
                    }
                }
            } catch (Exception e) {
                // Channel may have no compliance entries — continue
            }
        }
        return result;
    }

    private AttributionNode enrichNode(GraphNode node, Map<UUID, DecisionRecord> decisionMap, String tenancyId) {
        UUID entryId = UUID.fromString(node.entryId());

        Double trustScore = null;
        if (trustGateServiceInstance.isResolvable()) {
            OptionalDouble score = trustGateServiceInstance.get().currentScore(node.actorId());
            if (score.isPresent()) {
                trustScore = score.getAsDouble();
            }
        }

        String                  attestationVerdict = null;
        List<LedgerAttestation> attestations       = ledgerEntryRepository.findAttestationsByEntryId(entryId, tenancyId);
        if (!attestations.isEmpty()) {
            attestationVerdict = attestations.getLast().verdict.name();
        }

        DecisionRecord dr              = decisionMap.get(entryId);
        String         algorithmRef    = dr != null ? dr.algorithmRef() : null;
        Double         confidenceScore = dr != null ? dr.confidenceScore() : null;

        return new AttributionNode(
                node.entryId(), node.channelId(), node.channelName(),
                node.messageType(), node.actorId(), node.occurredAt(),
                node.content(), node.causedByEntryId(), node.depth(),
                trustScore, attestationVerdict, algorithmRef, confidenceScore, null);
    }

    private String buildCompositeMerkleRoot(CausalGraph graph, String tenancyId) {
        if (!verificationServiceInstance.isResolvable()) {
            return null;
        }
        LedgerVerificationService service = verificationServiceInstance.get();

        Set<UUID> channelIds = graph.nodes().stream()
                .map(n -> UUID.fromString(n.channelId()))
                .collect(Collectors.toCollection(TreeSet::new));

        List<String> parts = new ArrayList<>();
        for (UUID channelId : channelIds) {
            try {
                String root = service.treeRoot(channelId, tenancyId);
                parts.add(channelId + "=" + root);
            } catch (IllegalStateException e) {
                // No Merkle frontier for this subject — skip
            }
        }
        return parts.isEmpty() ? null : String.join(";", parts);
    }
}
