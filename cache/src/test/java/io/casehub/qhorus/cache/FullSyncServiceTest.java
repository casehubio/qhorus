package io.casehub.qhorus.cache;

import io.casehub.qhorus.api.channel.Channel;
import io.casehub.qhorus.api.message.Message;
import io.casehub.qhorus.api.message.MessageType;
import io.casehub.qhorus.api.store.ChannelStore;
import io.casehub.qhorus.api.store.MessageStore;
import io.casehub.qhorus.api.store.query.ChannelQuery;
import io.casehub.qhorus.api.store.query.MessageQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class FullSyncServiceTest {

    private MessageStore jpaStore;
    private ChannelStore channelStore;
    private CachingMessageStore cachingStore;
    private FullSyncService syncService;

    private static final UUID CH1 = UUID.randomUUID();
    private static final UUID CH2 = UUID.randomUUID();

    private static Message msg(long id, UUID channelId) {
        return new Message(id, channelId, "agent", MessageType.STATUS, null, null,
                "content", null, null, null, 0, null, null, null, null, null, null, 0, Instant.now());
    }

    private static Channel channel(UUID id) {
        return new Channel(id, "ch-" + id.toString().substring(0, 8), null, null,
                List.of(), List.of(), List.of(), null, null, null, null,
                false, false, null, "default", Instant.now(), Instant.now());
    }

    @BeforeEach
    void setUp() {
        jpaStore = mock(MessageStore.class);
        channelStore = mock(ChannelStore.class);
        MessageStore mockDelegate = mock(MessageStore.class);
        cachingStore = new CachingMessageStore(mockDelegate, 100, 0, true);
        syncService = new FullSyncService(jpaStore, channelStore, cachingStore, 2, true);
    }

    @Test
    void syncBatchLoadsOneChannelPerTick() {
        when(channelStore.scan(any(ChannelQuery.class)))
                .thenReturn(List.of(channel(CH1), channel(CH2)));
        when(jpaStore.scan(any(MessageQuery.class)))
                .thenReturn(List.of(msg(1, CH1), msg(2, CH1)))
                .thenReturn(List.of());

        syncService.syncBatch();

        assertThat(syncService.status()).isEqualTo(FullSyncService.SyncStatus.SYNCING);
        assertThat(syncService.channelsSynced()).isEqualTo(1);
        assertThat(cachingStore.messagesCached()).isEqualTo(2);
    }

    @Test
    void syncCompletesWhenAllChannelsDrained() {
        when(channelStore.scan(any(ChannelQuery.class)))
                .thenReturn(List.of(channel(CH1)));
        when(jpaStore.scan(any(MessageQuery.class)))
                .thenReturn(List.of(msg(1, CH1)))
                .thenReturn(List.of());

        syncService.syncBatch();
        syncService.syncBatch();

        assertThat(syncService.status()).isEqualTo(FullSyncService.SyncStatus.READY);
    }

    @Test
    void disabledModeSkipsSyncBatch() {
        syncService = new FullSyncService(jpaStore, channelStore, cachingStore, 2, false);

        syncService.syncBatch();

        assertThat(syncService.status()).isEqualTo(FullSyncService.SyncStatus.DISABLED);
        verifyNoInteractions(channelStore);
    }

    @Test
    void emptyDatabaseCompletesImmediately() {
        when(channelStore.scan(any(ChannelQuery.class))).thenReturn(List.of());

        syncService.syncBatch();

        assertThat(syncService.status()).isEqualTo(FullSyncService.SyncStatus.READY);
    }

    @Test
    void channelsTotalReflectsScannedChannels() {
        when(channelStore.scan(any(ChannelQuery.class)))
                .thenReturn(List.of(channel(CH1), channel(CH2)));
        when(jpaStore.scan(any(MessageQuery.class))).thenReturn(List.of());

        syncService.syncBatch();

        assertThat(syncService.channelsTotal()).isEqualTo(2);
    }
}
