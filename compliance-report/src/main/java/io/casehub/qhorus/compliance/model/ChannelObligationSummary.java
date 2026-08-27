package io.casehub.qhorus.compliance.model;

import java.util.UUID;

public record ChannelObligationSummary(
        UUID channelId,
        String channelName,
        int total,
        int fulfilled,
        int failed,
        int declined,
        int delegated,
        int stillOpen,
        int stalled,
        double fulfillmentRate) {
}
