package io.casehub.qhorus.slack.core;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SlackThreadCacheStore {

    Optional<String> findThreadTs(UUID channelId, String correlationId);

    Optional<String> findCorrelationId(UUID channelId, String threadTs);

    List<SlackThreadCache> findByChannelId(UUID channelId);

    void save(UUID channelId, String correlationId, String threadTs);

    void delete(UUID channelId, String correlationId);

    void deleteAllByChannelId(UUID channelId);

    int deleteOlderThan(Instant threshold);
}
