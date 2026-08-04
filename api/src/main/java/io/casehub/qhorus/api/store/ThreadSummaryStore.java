package io.casehub.qhorus.api.store;

import io.casehub.qhorus.api.channel.ThreadSummary;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ThreadSummaryStore {

    ThreadSummary save(ThreadSummary summary);

    Optional<ThreadSummary> findByCorrelationId(UUID channelId, String correlationId);

    List<ThreadSummary> findByChannel(UUID channelId);

    void delete(UUID channelId, String correlationId);
}
