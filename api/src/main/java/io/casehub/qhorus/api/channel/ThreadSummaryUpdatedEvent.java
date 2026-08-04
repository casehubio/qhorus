package io.casehub.qhorus.api.channel;

import java.util.UUID;

public record ThreadSummaryUpdatedEvent(
        UUID channelId, String channelName,
        String correlationId, String updatedBy) {}
