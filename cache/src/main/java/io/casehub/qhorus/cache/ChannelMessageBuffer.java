package io.casehub.qhorus.cache;

import io.casehub.qhorus.api.message.Message;
import io.casehub.qhorus.api.store.query.MessageQuery;

import java.util.List;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.concurrent.ConcurrentSkipListMap;

public class ChannelMessageBuffer {

    private final ConcurrentSkipListMap<Long, Message> messages = new ConcurrentSkipListMap<>();
    private final int maxSize;

    public ChannelMessageBuffer(int maxSize) {
        this.maxSize = maxSize;
    }

    public void add(Message msg) {
        if (msg.id() == null) return;
        messages.put(msg.id(), msg);
        if (maxSize > 0) {
            while (messages.size() > maxSize) {
                messages.pollFirstEntry();
            }
        }
    }

    public List<Message> query(MessageQuery q) {
        Long afterId = q.afterId();
        NavigableMap<Long, Message> range;
        if (afterId != null) {
            range = messages.tailMap(afterId, false);
        } else if (q.descending()) {
            range = messages.descendingMap();
        } else {
            range = messages;
        }

        Long beforeId = q.beforeId();
        var stream = range.values().stream();
        if (beforeId != null) {
            stream = stream.filter(m -> m.id() <= beforeId);
        }

        stream = stream.filter(q::matches);

        int limit = q.limit() != null ? q.limit() : 50;
        return stream.limit(limit).toList();
    }

    public Optional<Message> findById(Long id) {
        return Optional.ofNullable(messages.get(id));
    }

    public boolean covers(Long afterId) {
        if (messages.isEmpty()) return false;
        if (afterId == null) return true;
        return afterId >= messages.firstKey() - 1;
    }

    public int size() {
        return messages.size();
    }

    public Long lastId() {
        return messages.isEmpty() ? null : messages.lastKey();
    }
}
