package io.casehub.qhorus.runtime.cdi;

import io.casehub.qhorus.api.channel.Channel;
import io.casehub.qhorus.api.channel.ChannelReader;
import io.casehub.qhorus.api.channel.RoutingDiagnostic;
import io.casehub.qhorus.api.channel.RoutingDiagnostics;
import io.casehub.qhorus.runtime.message.RoutingBridge;
import io.casehub.qhorus.runtime.message.RoutingDiagnosticsAdapter;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class CdiRoutingDiagnostics implements RoutingDiagnostics {

    @Inject
    Instance<RoutingBridge> routingBridge;

    @Inject
    ChannelReader channelReader;

    @Override
    public RoutingDiagnostic diagnose(String capability, UUID channelId, String tenancyId) {
        if (!routingBridge.isResolvable()) {
            return new RoutingDiagnostic(List.of(), null, 0.0, "unavailable",
                    "RoutingBridge not available", 0.0, false);
        }
        return new RoutingDiagnosticsAdapter(routingBridge.get(), channelReader)
                .diagnose(capability, channelId, tenancyId);
    }

    @Override
    public double effectiveThreshold(Channel channel) {
        if (!routingBridge.isResolvable()) {
            return 0.0;
        }
        return routingBridge.get().effectiveThreshold(channel);
    }
}
