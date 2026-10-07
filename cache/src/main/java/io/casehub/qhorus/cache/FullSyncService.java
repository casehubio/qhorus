package io.casehub.qhorus.cache;

import io.casehub.qhorus.api.channel.Channel;
import io.casehub.qhorus.api.message.Message;
import io.casehub.qhorus.api.store.ChannelStore;
import io.casehub.qhorus.api.store.MessageStore;
import io.casehub.qhorus.api.store.query.ChannelQuery;
import io.casehub.qhorus.api.store.query.MessageQuery;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class FullSyncService {

    private static final org.jboss.logging.Logger LOG =
            org.jboss.logging.Logger.getLogger(FullSyncService.class);

    public enum SyncStatus { SYNCING, READY, DISABLED }

    private final MessageStore jpaStore;
    private final ChannelStore channelStore;
    private final CachingMessageStore cachingStore;
    private final int batchSize;

    private volatile SyncStatus status;
    private final Map<UUID, Long> cursors = new ConcurrentHashMap<>();
    private volatile int channelsTotal;

    public FullSyncService(MessageStore jpaStore, ChannelStore channelStore,
                           CachingMessageStore cachingStore, int batchSize,
                           boolean fullMode) {
        this.jpaStore = jpaStore;
        this.channelStore = channelStore;
        this.cachingStore = cachingStore;
        this.batchSize = batchSize;
        this.status = fullMode ? SyncStatus.SYNCING : SyncStatus.DISABLED;
    }

    public void syncBatch() {
        if (status != SyncStatus.SYNCING) return;

        List<Channel> channels = channelStore.scan(ChannelQuery.all());
        channelsTotal = channels.size();

        boolean allDone = true;
        for (Channel ch : channels) {
            Long cursor = cursors.getOrDefault(ch.id(), 0L);
            MessageQuery q = MessageQuery.poll(ch.id(), cursor, batchSize);
            List<Message> batch = jpaStore.scan(q);

            if (!batch.isEmpty()) {
                for (Message msg : batch) {
                    cachingStore.addToBuffer(ch.id(), msg);
                }
                cursors.put(ch.id(), batch.getLast().id());
                allDone = false;
                return;
            }
        }

        if (allDone) {
            status = SyncStatus.READY;
            LOG.infof("Full sync complete — %d channels cached", channels.size());
        }
    }

    public SyncStatus status() {
        return status;
    }

    public int channelsSynced() {
        return cursors.size();
    }

    public int channelsTotal() {
        return channelsTotal;
    }
}
