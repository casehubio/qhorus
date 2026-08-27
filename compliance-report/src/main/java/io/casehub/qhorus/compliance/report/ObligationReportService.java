package io.casehub.qhorus.compliance.report;

import io.casehub.ledger.runtime.service.LedgerVerificationService;
import io.casehub.ledger.runtime.service.TrustGateService;
import io.casehub.qhorus.api.channel.Channel;
import io.casehub.qhorus.api.message.Commitment;
import io.casehub.qhorus.api.message.CommitmentState;
import io.casehub.qhorus.api.spi.compliance.CompliancePosture;
import io.casehub.qhorus.api.spi.compliance.CompliancePostureProvider;
import io.casehub.qhorus.api.store.ChannelStore;
import io.casehub.qhorus.api.store.CommitmentReader;
import io.casehub.qhorus.api.store.query.ChannelQuery;
import io.casehub.qhorus.compliance.model.AgentObligationSummary;
import io.casehub.qhorus.compliance.model.ChannelObligationSummary;
import io.casehub.qhorus.compliance.model.ObligationReport;
import io.casehub.qhorus.runtime.ledger.MessageLedgerEntryRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.UUID;

@ApplicationScoped
public class ObligationReportService {

    @Inject MessageLedgerEntryRepository ledgerRepo;
    @Inject CommitmentReader commitmentReader;
    @Inject ChannelStore channelStore;
    @Inject Instance<TrustGateService> trustGateServiceInstance;
    @Inject CompliancePostureProvider postureProvider;
    @Inject Instance<LedgerVerificationService> verificationServiceInstance;

    public ObligationReport generate(UUID channelId, Instant from, Instant to,
                                     String actorId, String tenancyId) {
        List<Channel> channels;
        if (channelId != null) {
            channels = channelStore.find(channelId).map(List::of).orElse(List.of());
        } else {
            channels = channelStore.scan(ChannelQuery.all());
        }

        List<ChannelObligationSummary> channelSummaries = new ArrayList<>();
        Map<String, List<Commitment>> agentCommitments = new HashMap<>();

        for (Channel ch : channels) {
            Map<String, Long> outcomes = ledgerRepo.countByOutcome(ch.id(), from, to, tenancyId);
            int total = intVal(outcomes, "COMMAND");
            int fulfilled = intVal(outcomes, "DONE");
            int failed = intVal(outcomes, "FAILURE");
            int declined = intVal(outcomes, "DECLINE");
            int delegated = intVal(outcomes, "HANDOFF");

            int stillOpen = commitmentReader.findOpenByChannelId(ch.id()).size();
            int stalled = ledgerRepo.findStalledCommands(ch.id(), from, tenancyId).size();

            double rate = total > 0 ? (double) fulfilled / total : 0.0;
            channelSummaries.add(new ChannelObligationSummary(
                    ch.id(), ch.name(), total, fulfilled, failed, declined,
                    delegated, stillOpen, stalled, rate));

            for (Commitment c : commitmentReader.findByChannel(ch.id())) {
                if (c.obligor() != null) {
                    agentCommitments.computeIfAbsent(c.obligor(), k -> new ArrayList<>()).add(c);
                }
            }
        }

        List<AgentObligationSummary> agentSummaries = buildAgentSummaries(
                agentCommitments, actorId, from, to, tenancyId);

        int totalCommands = channelSummaries.stream().mapToInt(ChannelObligationSummary::total).sum();
        int totalFulfilled = channelSummaries.stream().mapToInt(ChannelObligationSummary::fulfilled).sum();
        int totalFailed = channelSummaries.stream().mapToInt(ChannelObligationSummary::failed).sum();
        int totalDeclined = channelSummaries.stream().mapToInt(ChannelObligationSummary::declined).sum();
        int totalDelegated = channelSummaries.stream().mapToInt(ChannelObligationSummary::delegated).sum();
        int totalStillOpen = channelSummaries.stream().mapToInt(ChannelObligationSummary::stillOpen).sum();
        int totalStalled = channelSummaries.stream().mapToInt(ChannelObligationSummary::stalled).sum();
        double overallRate = totalCommands > 0 ? (double) totalFulfilled / totalCommands : 0.0;

        CompliancePosture posture = postureProvider.getPosture(tenancyId, from, to);
        String merkleRoot = buildCompositeMerkleRoot(channels, tenancyId);

        return new ObligationReport(
                from, to, channelSummaries, agentSummaries,
                totalCommands, totalFulfilled, totalFailed, totalDeclined,
                totalDelegated, totalStillOpen, totalStalled, overallRate,
                posture, merkleRoot, Instant.now(), 1);
    }

    private List<AgentObligationSummary> buildAgentSummaries(
            Map<String, List<Commitment>> agentCommitments,
            String actorFilter, Instant from, Instant to, String tenancyId) {
        List<AgentObligationSummary> summaries = new ArrayList<>();

        for (var entry : agentCommitments.entrySet()) {
            String actor = entry.getKey();
            if (actorFilter != null && !actorFilter.equals(actor)) continue;

            List<Commitment> commitments = entry.getValue();

            List<Commitment> inWindow = commitments.stream()
                    .filter(c -> c.createdAt() != null
                            && !c.createdAt().isBefore(from)
                            && !c.createdAt().isAfter(to))
                    .toList();

            int total = inWindow.size();
            int fulfilled = (int) inWindow.stream().filter(c -> c.state() == CommitmentState.FULFILLED).count();
            int failed = (int) inWindow.stream().filter(c -> c.state() == CommitmentState.FAILED).count();
            int declined = (int) inWindow.stream().filter(c -> c.state() == CommitmentState.DECLINED).count();
            int delegated = (int) inWindow.stream().filter(c -> c.state() == CommitmentState.DELEGATED).count();

            int stillOpen = (int) commitments.stream().filter(c -> c.state().isActive()).count();
            int stalled = (int) commitments.stream()
                    .filter(c -> c.state().isActive()
                            && c.createdAt() != null
                            && c.createdAt().isBefore(from))
                    .count();

            double rate = total > 0 ? (double) fulfilled / total : 0.0;

            Double trustScore = null;
            if (trustGateServiceInstance.isResolvable()) {
                OptionalDouble score = trustGateServiceInstance.get().currentScore(actor);
                if (score.isPresent()) trustScore = score.getAsDouble();
            }

            summaries.add(new AgentObligationSummary(
                    actor, total, fulfilled, failed, declined,
                    delegated, stillOpen, stalled, rate, trustScore));
        }

        return summaries;
    }

    private String buildCompositeMerkleRoot(List<Channel> channels, String tenancyId) {
        if (!verificationServiceInstance.isResolvable()) return null;
        LedgerVerificationService service = verificationServiceInstance.get();

        List<String> parts = new ArrayList<>();
        for (Channel ch : channels) {
            try {
                String root = service.treeRoot(ch.id(), tenancyId);
                parts.add(ch.id() + "=" + root);
            } catch (IllegalStateException e) {
                // No Merkle frontier — skip
            }
        }
        return parts.isEmpty() ? null : String.join(";", parts);
    }

    private static int intVal(Map<String, Long> map, String key) {
        return map.getOrDefault(key, 0L).intValue();
    }
}
