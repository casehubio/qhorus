package io.casehub.qhorus.cache;

public record CacheHealthResponse(
        String status,
        int channelsCached,
        long messagesCached,
        String syncStatus,
        int channelsSynced,
        int channelsTotal) {
}