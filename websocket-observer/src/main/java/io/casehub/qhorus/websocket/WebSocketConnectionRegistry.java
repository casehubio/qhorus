package io.casehub.qhorus.websocket;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.enterprise.context.ApplicationScoped;

import io.quarkus.websockets.next.WebSocketConnection;

@ApplicationScoped
public class WebSocketConnectionRegistry {

    private final ConcurrentHashMap<UUID, Set<WebSocketConnection>> channels = new ConcurrentHashMap<>();

    public void subscribe(UUID channelId, WebSocketConnection connection) {
        channels.computeIfAbsent(channelId, k -> ConcurrentHashMap.newKeySet()).add(connection);
    }

    public void unsubscribe(UUID channelId, WebSocketConnection connection) {
        channels.computeIfPresent(channelId, (k, conns) -> {
            conns.remove(connection);
            return conns.isEmpty() ? null : conns;
        });
    }

    public Set<WebSocketConnection> connections(UUID channelId) {
        return channels.getOrDefault(channelId, Set.of());
    }

    int channelCount() {
        return channels.size();
    }
}
