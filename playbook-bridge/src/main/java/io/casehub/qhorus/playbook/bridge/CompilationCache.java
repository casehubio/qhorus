package io.casehub.qhorus.playbook.bridge;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class CompilationCache {

    private final ConcurrentHashMap<String, String> cache = new ConcurrentHashMap<>();

    private static String key(UUID channelId, String protocolName) {
        return channelId + ":" + protocolName;
    }

    public Optional<String> get(UUID channelId, String protocolName) {
        return Optional.ofNullable(cache.get(key(channelId, protocolName)));
    }

    public void put(UUID channelId, String protocolName, String yamlContent) {
        cache.put(key(channelId, protocolName), yamlContent);
    }

    public void evict(UUID channelId, String protocolName) {
        cache.remove(key(channelId, protocolName));
    }

    public void evictAll(UUID channelId) {
        String prefix = channelId + ":";
        cache.keySet().removeIf(k -> k.startsWith(prefix));
    }
}
