package io.casehub.qhorus.api.channel;

public record MessageDeliveryStatus(
        String memberId,
        boolean delivered,
        Long lastDeliveredMessageId) {}
