package io.casehub.qhorus.runtime.message;

import io.casehub.eidos.api.AgentMatch;
import io.casehub.eidos.api.AgentQuery;
import io.casehub.eidos.api.AgentRegistry;
import io.casehub.eidos.api.AgentSelection;
import io.casehub.eidos.api.AgentSelector;
import io.casehub.eidos.api.SelectionContext;
import io.casehub.ledger.runtime.service.TrustGateService;
import io.casehub.qhorus.api.channel.Channel;
import io.casehub.qhorus.api.message.MessageDispatch;
import io.casehub.qhorus.api.message.RoutingRejectedException;
import io.casehub.qhorus.runtime.config.QhorusConfig;
import org.jboss.logging.Logger;

import java.util.List;

public class RoutingBridge {

    private static final Logger LOG = Logger.getLogger(RoutingBridge.class);

    private final AgentRegistry agentRegistry;
    private final AgentSelector agentSelector;
    private final TrustGateService trustGateService;
    private final io.casehub.platform.api.capacity.ActorCapacityView capacityView;
    private final QhorusConfig config;

    public RoutingBridge(AgentRegistry agentRegistry,
                         AgentSelector agentSelector,
                         TrustGateService trustGateService,
                         io.casehub.platform.api.capacity.ActorCapacityView capacityView,
                         QhorusConfig config) {
        this.agentRegistry = agentRegistry;
        this.agentSelector = agentSelector;
        this.trustGateService = trustGateService;
        this.capacityView = capacityView;
        this.config = config;
    }

    public record RoutingOutcome(
            String resolvedTarget,
            String originalTarget,
            String strategyName,
            int candidateCount,
            double trustScore) {}

    public record CandidateInfo(String agentId, String name, double trustScore, boolean passesThreshold) {}

    public record RoutingDiagnostic(
            List<CandidateInfo> candidates,
            String selectedAgentId,
            double selectedTrustScore,
            String selectionOutcome,
            String reason,
            double effectiveThreshold,
            boolean routingAvailable) {}

    public RoutingOutcome resolve(MessageDispatch dispatch, Channel channel, String tenancyId) {
        String target = dispatch.target();
        if (target == null || !target.startsWith("role:")) {
            return null;
        }

        if (agentRegistry == null || agentSelector == null) {
            LOG.warnf("Routing skipped for target '%s' — AgentRegistry or AgentSelector not available", target);
            return null;
        }

        String capability = target.substring("role:".length());
        List<AgentMatch> matches = agentRegistry.find(
                AgentQuery.byCapability(capability, tenancyId));

        SelectionContext ctx = SelectionContext.of(tenancyId, capability);
        AgentSelection selection = agentSelector.select(matches, ctx);

        double channelThreshold = effectiveThreshold(channel);

        return switch (selection) {
            case AgentSelection.Selected s -> {
                if (s.trustScore() < channelThreshold) {
                    throw new RoutingRejectedException(
                            "Best candidate '%s' (score %.2f) below channel threshold %.2f for capability '%s'"
                                    .formatted(s.agent().agentId(), s.trustScore(), channelThreshold, capability));
                }
                if (capacityView != null) {
                    double capThreshold = effectiveCapacityThreshold(channel);
                    var cap = capacityView.getCapacity(s.agent().agentId());
                    if (cap != null && cap.aggregatePressure() >= capThreshold) {
                        throw new RoutingRejectedException(
                                "Best candidate '%s' (pressure %.2f) exceeds channel capacity threshold %.2f for capability '%s'"
                                        .formatted(s.agent().agentId(), cap.aggregatePressure(), capThreshold, capability));
                    }
                }
                yield new RoutingOutcome(
                        s.agent().agentId(),
                        target,
                        "eidos-simple",
                        matches.size(),
                        s.trustScore());
            }
            case AgentSelection.NoneQualified nq ->
                    throw new RoutingRejectedException(
                            "No agent qualified for capability '%s': %s".formatted(capability, nq.reason()));
            case AgentSelection.Escalated e ->
                    throw new RoutingRejectedException(
                            "Routing escalation for capability '%s': %s — %s".formatted(
                                    capability, e.kind(), e.reason()));
        };
    }

    public RoutingDiagnostic diagnose(String capability, Channel channel, String tenancyId) {
        if (agentRegistry == null || agentSelector == null) {
            return new RoutingDiagnostic(List.of(), null, 0.0, "unavailable",
                                         "AgentRegistry or AgentSelector not available", 0.0, false);
        }

        double threshold = effectiveThreshold(channel);

        List<AgentMatch> matches = agentRegistry.find(
                AgentQuery.byCapability(capability, tenancyId));

        List<CandidateInfo> candidates = matches.stream()
                .map(m -> {
                    String agentId = m.descriptor().agentId();
                    double score   = lookupTrustScore(agentId);
                    return new CandidateInfo(agentId, m.descriptor().name(), score, score >= threshold);
                })
                .toList();

        SelectionContext ctx       = SelectionContext.of(tenancyId, capability);
        AgentSelection   selection = agentSelector.select(matches, ctx);

        return switch (selection) {
            case AgentSelection.Selected s -> new RoutingDiagnostic(
                    candidates, s.agent().agentId(), s.trustScore(),
                    s.trustScore() >= threshold ? "selected" : "below_threshold",
                    s.reason(), threshold, true);
            case AgentSelection.NoneQualified nq -> new RoutingDiagnostic(
                    candidates, null, 0.0, "none_qualified", nq.reason(), threshold, true);
            case AgentSelection.Escalated e -> new RoutingDiagnostic(
                    candidates, null, 0.0, "escalated", e.reason(), threshold, true);
        };
    }

    public double effectiveThreshold(Channel channel) {
        if (channel != null && channel.routingTrustThreshold() != null) {
            return channel.routingTrustThreshold();
        }
        return config.routing().defaultTrustThreshold();
    }

    public double effectiveCapacityThreshold(Channel channel) {
        if (channel != null && channel.routingCapacityThreshold() != null) {
            return channel.routingCapacityThreshold();
        }
        return config.routing().defaultCapacityThreshold().orElse(0.8);
    }

    private double lookupTrustScore(String agentId) {
        if (trustGateService == null) {
            return 0.0;
        }
        return trustGateService.currentScore(agentId).orElse(0.0);
    }
}
