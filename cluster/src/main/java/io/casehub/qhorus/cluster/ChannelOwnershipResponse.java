package io.casehub.qhorus.cluster;

public record ChannelOwnershipResponse(
        String owner,
        String source,
        long claimWriteCount) {
}
