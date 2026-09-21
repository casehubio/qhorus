package io.casehub.qhorus.api.channel;

import java.util.UUID;

public interface RoutingDiagnostics {

    RoutingDiagnostic diagnose(String capability, UUID channelId, String tenancyId);

    double effectiveThreshold(Channel channel);
}
