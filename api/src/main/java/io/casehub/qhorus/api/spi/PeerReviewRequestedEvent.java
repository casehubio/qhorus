package io.casehub.qhorus.api.spi;

import java.util.UUID;

public record PeerReviewRequestedEvent(
        UUID ledgerEntryId,
        UUID channelId,
        String tenancyId) {}
