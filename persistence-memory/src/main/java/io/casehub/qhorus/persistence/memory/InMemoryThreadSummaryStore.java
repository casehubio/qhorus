package io.casehub.qhorus.persistence.memory;

import io.casehub.qhorus.api.channel.ThreadSummary;
import io.casehub.qhorus.api.store.ThreadSummaryStore;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Alternative
@Priority(1)
@ApplicationScoped
public class InMemoryThreadSummaryStore implements ThreadSummaryStore {

    private final Map<String, ThreadSummary> store = new ConcurrentHashMap<>();

    private static String key(UUID channelId, String correlationId) {
        return channelId + ":" + correlationId;
    }

    @Override
    public ThreadSummary save(ThreadSummary summary) {
        ThreadSummary.Builder b = summary.toBuilder();
        if (summary.id() == null) {
            b.id(UUID.randomUUID());
        }
        ThreadSummary saved = b.build();
        store.put(key(saved.channelId(), saved.correlationId()), saved);
        return saved;
    }

    @Override
    public Optional<ThreadSummary> findByCorrelationId(UUID channelId, String correlationId) {
        return Optional.ofNullable(store.get(key(channelId, correlationId)));
    }

    @Override
    public List<ThreadSummary> findByChannel(UUID channelId) {
        return store.values().stream()
                .filter(s -> s.channelId().equals(channelId))
                .toList();
    }

    @Override
    public void delete(UUID channelId, String correlationId) {
        store.remove(key(channelId, correlationId));
    }

    public void clear() {
        store.clear();
    }
}
