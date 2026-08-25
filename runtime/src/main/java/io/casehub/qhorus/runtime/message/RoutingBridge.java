package io.casehub.qhorus.runtime.message;

import io.casehub.eidos.api.AgentMatch;
import io.casehub.eidos.api.AgentQuery;
import io.casehub.eidos.api.AgentRegistry;
import io.casehub.eidos.api.AgentSelection;
import io.casehub.eidos.api.AgentSelector;
import io.casehub.eidos.api.SelectionContext;
import io.casehub.qhorus.api.channel.Channel;
import io.casehub.qhorus.api.message.MessageDispatch;
import io.casehub.qhorus.api.message.RoutingRejectedException;
import io.casehub.ledger.runtime.service.TrustGateService;
import io.casehub.qhorus.runtime.config.QhorusConfig;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.util.List;

@ApplicationScoped
public class RoutingBridge {

    private static final Logger LOG = Logger.getLogger(RoutingBridge.class);

    private final Instance<AgentRegistry>    agentRegistryInstance;
    private final Instance<AgentSelector>    agentSelectorInstance;
    private final Instance<TrustGateService> trustGateServiceInstance;
    private final QhorusConfig               config;

    @Inject
    public RoutingBridge(Instance<AgentRegistry> agentRegistryInstance,
                         Instance<AgentSelector> agentSelectorInstance,
                         Instance<TrustGateService> trustGateServiceInstance,
                         QhorusConfig config) {
        this.agentRegistryInstance    = agentRegistryInstance;
        this.agentSelectorInstance    = agentSelectorInstance;
        this.trustGateServiceInstance = trustGateServiceInstance;
        this.config                   = config;
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

        if (!agentRegistryInstance.isResolvable() || !agentSelectorInstance.isResolvable()) {
            LOG.warnf("Routing skipped for target '%s' — AgentRegistry or AgentSelector not available", target);
            return null;
        }

        AgentRegistry agentRegistry = agentRegistryInstance.get();
        AgentSelector agentSelector = agentSelectorInstance.get();

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
        if (!agentRegistryInstance.isResolvable() || !agentSelectorInstance.isResolvable()) {
            return new RoutingDiagnostic(List.of(), null, 0.0, "unavailable",
                                         "AgentRegistry or AgentSelector not available", 0.0, false);
        }

        AgentRegistry agentRegistry = agentRegistryInstance.get();
        AgentSelector agentSelector = agentSelectorInstance.get();

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

    private double lookupTrustScore(String agentId) {
        if (!trustGateServiceInstance.isResolvable()) {
            return 0.0;
        }
        return trustGateServiceInstance.get().currentScore(agentId).orElse(0.0);
    }

}
