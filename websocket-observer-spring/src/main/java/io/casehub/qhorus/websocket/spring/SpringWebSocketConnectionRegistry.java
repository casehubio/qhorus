package io.casehub.qhorus.websocket.spring;

import org.springframework.web.socket.WebSocketSession;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class SpringWebSocketConnectionRegistry {

    public record BufferedMessage(Long messageId, String json) {}

    private final ConcurrentHashMap<UUID, Set<WebSocketSession>> channels = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<WebSocketSession, List<BufferedMessage>> catchUpBuffers = new ConcurrentHashMap<>();

    public void subscribe(UUID channelId, WebSocketSession session) {
        channels.computeIfAbsent(channelId, k -> ConcurrentHashMap.newKeySet()).add(session);
    }

    public void subscribeCatchingUp(UUID channelId, WebSocketSession session) {
        channels.computeIfAbsent(channelId, k -> ConcurrentHashMap.newKeySet()).add(session);
        catchUpBuffers.put(session, new ArrayList<>());
    }

    public List<BufferedMessage> completeCatchUp(UUID channelId, WebSocketSession session) {
        List<BufferedMessage> buffer = catchUpBuffers.remove(session);
        return buffer != null ? List.copyOf(buffer) : List.of();
    }

    public boolean tryBufferForCatchUp(WebSocketSession session, Long messageId, String json) {
        List<BufferedMessage> buffer = catchUpBuffers.get(session);
        if (buffer == null) {
            return false;
        }
        synchronized (buffer) {
            buffer.add(new BufferedMessage(messageId, json));
        }
        return true;
    }

    public void cancelCatchUp(UUID channelId, WebSocketSession session) {
        catchUpBuffers.remove(session);
    }

    public void unsubscribe(UUID channelId, WebSocketSession session) {
        channels.computeIfPresent(channelId, (k, conns) -> {
            conns.remove(session);
            return conns.isEmpty() ? null : conns;
        });
        catchUpBuffers.remove(session);
    }

    public Set<WebSocketSession> connections(UUID channelId) {
        return channels.getOrDefault(channelId, Set.of());
    }
}
