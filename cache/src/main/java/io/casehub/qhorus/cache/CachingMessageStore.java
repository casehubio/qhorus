package io.casehub.qhorus.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.casehub.qhorus.api.message.Message;
import io.casehub.qhorus.api.message.MessageType;
import io.casehub.qhorus.api.message.MessageView;
import io.casehub.qhorus.api.store.MessageStore;
import io.casehub.qhorus.api.store.query.MessageQuery;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class CachingMessageStore implements MessageStore {

    private final MessageStore delegate;
    private final Cache<UUID, ChannelMessageBuffer> channelCache;
    private final int maxMessagesPerChannel;
    private final boolean fullMode;

    public CachingMessageStore(MessageStore delegate, int maxChannels,
                               int maxMessagesPerChannel, boolean fullMode) {
        this.delegate = delegate;
        this.maxMessagesPerChannel = maxMessagesPerChannel;
        this.fullMode = fullMode;
        this.channelCache = Caffeine.newBuilder()
                .maximumSize(fullMode ? Long.MAX_VALUE : maxChannels)
                .build();
    }

    @Override
    public Message put(Message message) {
        Message persisted = delegate.put(message);
        if (persisted.id() != null && persisted.channelId() != null) {
            addToBuffer(persisted.channelId(), persisted);
        }
        return persisted;
    }

    @Override
    public Optional<Message> find(Long id) {
        Optional<Message> result = delegate.find(id);
        result.ifPresent(msg -> {
            if (msg.channelId() != null) {
                addToBuffer(msg.channelId(), msg);
            }
        });
        return result;
    }

    @Override
    public List<Message> scan(MessageQuery query) {
        if (query.channelId() == null) {
            return delegate.scan(query);
        }
        ChannelMessageBuffer buffer = channelCache.getIfPresent(query.channelId());
        if (buffer != null && buffer.covers(query.afterId())) {
            List<Message> cached = buffer.query(query);
            if (!cached.isEmpty() || buffer.covers(query.afterId())) {
                return cached;
            }
        }
        return delegate.scan(query);
    }

    @Override
    public Optional<Message> findLastMessage(UUID channelId) {
        ChannelMessageBuffer buffer = channelCache.getIfPresent(channelId);
        if (buffer != null && buffer.size() > 0) {
            Long lastId = buffer.lastId();
            if (lastId != null) {
                return buffer.findById(lastId);
            }
        }
        return delegate.findLastMessage(channelId);
    }

    @Override
    public List<MessageView> findRecent(UUID channelId, int limit) {
        return delegate.findRecent(channelId, limit);
    }

    @Override
    public int countByChannel(UUID channelId) {
        return delegate.countByChannel(channelId);
    }

    @Override
    public long count(MessageQuery query) {
        return delegate.count(query);
    }

    @Override
    public Map<UUID, Long> countAllByChannel() {
        return delegate.countAllByChannel();
    }

    @Override
    public List<String> distinctSendersByChannel(UUID channelId, MessageType excludedType) {
        return delegate.distinctSendersByChannel(channelId, excludedType);
    }

    @Override
    public Optional<Message> findLastMessageForUpdate(UUID channelId) {
        return delegate.findLastMessageForUpdate(channelId);
    }

    @Override
    public int countByCorrectsMessageId(Long messageId) {
        return delegate.countByCorrectsMessageId(messageId);
    }

    @Override
    public void deleteAll(UUID channelId) {
        channelCache.invalidate(channelId);
        delegate.deleteAll(channelId);
    }

    @Override
    public void deleteNonEvent(UUID channelId) {
        channelCache.invalidate(channelId);
        delegate.deleteNonEvent(channelId);
    }

    @Override
    public void delete(Long id) {
        delegate.delete(id);
        channelCache.asMap().values().forEach(b -> b.remove(id));
    }

    @Override
    public int updateTopicName(UUID channelId, String oldTopic, String newTopic) {
        channelCache.invalidate(channelId);
        return delegate.updateTopicName(channelId, oldTopic, newTopic);
    }

    @Override
    public int updateChannelId(UUID sourceChannelId, String topic, UUID targetChannelId) {
        channelCache.invalidate(sourceChannelId);
        channelCache.invalidate(targetChannelId);
        return delegate.updateChannelId(sourceChannelId, topic, targetChannelId);
    }

    public void addToBuffer(UUID channelId, Message msg) {
        int bufSize = fullMode ? 0 : maxMessagesPerChannel;
        ChannelMessageBuffer buffer = channelCache.get(channelId,
                k -> new ChannelMessageBuffer(bufSize));
        buffer.add(msg);
    }

    public void invalidateAll() {
        channelCache.invalidateAll();
    }

    public int channelsCached() {
        channelCache.cleanUp();
        return (int) channelCache.estimatedSize();
    }

    public long messagesCached() {
        channelCache.cleanUp();
        long total = 0;
        for (ChannelMessageBuffer buf : channelCache.asMap().values()) {
            total += buf.size();
        }
        return total;
    }
}
