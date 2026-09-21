package io.casehub.qhorus.runtime.message;

import io.casehub.qhorus.api.channel.Channel;
import io.casehub.qhorus.api.channel.ChannelReader;
import io.casehub.qhorus.api.channel.RoutingCandidate;
import io.casehub.qhorus.api.channel.RoutingDiagnostic;
import io.casehub.qhorus.api.channel.RoutingDiagnostics;

import java.util.UUID;

public class RoutingDiagnosticsAdapter implements RoutingDiagnostics {

    private final RoutingBridge routingBridge;
    private final ChannelReader channelReader;

    public RoutingDiagnosticsAdapter(RoutingBridge routingBridge, ChannelReader channelReader) {
        this.routingBridge = routingBridge;
        this.channelReader = channelReader;
    }

    @Override
    public RoutingDiagnostic diagnose(String capability, UUID channelId, String tenancyId) {
        Channel channel = channelId != null
                ? channelReader.findById(channelId).orElse(null)
                : null;
        RoutingBridge.RoutingDiagnostic rd = routingBridge.diagnose(capability, channel, tenancyId);
        return new RoutingDiagnostic(
                rd.candidates().stream()
                  .map(c -> new RoutingCandidate(c.agentId(), c.name(), c.trustScore(), c.passesThreshold()))
                  .toList(),
                rd.selectedAgentId(),
                rd.selectedTrustScore(),
                rd.selectionOutcome(),
                rd.reason(),
                rd.effectiveThreshold(),
                rd.routingAvailable());
    }

    @Override
    public double effectiveThreshold(Channel channel) {
        return routingBridge.effectiveThreshold(channel);
    }
}
