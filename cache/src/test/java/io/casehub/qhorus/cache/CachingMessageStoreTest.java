package io.casehub.qhorus.cache;

import io.casehub.qhorus.api.message.Message;
import io.casehub.qhorus.api.message.MessageType;
import io.casehub.qhorus.api.store.MessageStore;
import io.casehub.qhorus.api.store.query.MessageQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CachingMessageStoreTest {

    private MessageStore delegate;
    private CachingMessageStore cache;
    private static final UUID CH = UUID.randomUUID();

    private static Message msg(long id, UUID channelId) {
        return new Message(id, channelId, "agent", MessageType.STATUS, null, null,
                "content", null, null, null, 0, null, null, null, null, null, null, 0, Instant.now());
    }

    @BeforeEach
    void setUp() {
        delegate = Mockito.mock(MessageStore.class);
        cache = new CachingMessageStore(delegate, 100, 200, false);
    }

    @Test
    void putDelegatesToStoreAndPopulatesCache() {
        Message input = msg(0, CH);
        Message persisted = msg(1, CH);
        when(delegate.put(input)).thenReturn(persisted);

        Message result = cache.put(input);

        assertThat(result.id()).isEqualTo(1L);
        verify(delegate).put(input);

        MessageQuery q = MessageQuery.forChannel(CH);
        List<Message> cached = cache.scan(q);
        assertThat(cached).hasSize(1);
        verify(delegate, never()).scan(any());
    }

    @Test
    void scanFallsThroughOnCacheMiss() {
        UUID otherCh = UUID.randomUUID();
        MessageQuery q = MessageQuery.forChannel(otherCh);
        when(delegate.scan(q)).thenReturn(List.of(msg(1, otherCh)));

        List<Message> result = cache.scan(q);

        assertThat(result).hasSize(1);
        verify(delegate).scan(q);
    }

    @Test
    void scanFallsThroughWhenAfterIdBeforeBufferRange() {
        Message persisted = msg(100, CH);
        when(delegate.put(any())).thenReturn(persisted);
        cache.put(msg(0, CH));

        MessageQuery q = MessageQuery.poll(CH, 5L, 50);
        when(delegate.scan(q)).thenReturn(List.of(msg(10, CH), msg(50, CH)));

        List<Message> result = cache.scan(q);
        verify(delegate).scan(q);
        assertThat(result).hasSize(2);
    }

    @Test
    void scanServesFromCacheWhenInRange() {
        cache.addToBuffer(CH, msg(10, CH));
        cache.addToBuffer(CH, msg(20, CH));
        cache.addToBuffer(CH, msg(30, CH));

        MessageQuery q = MessageQuery.poll(CH, 10L, 50);
        List<Message> result = cache.scan(q);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).id()).isEqualTo(20L);
        assertThat(result.get(1).id()).isEqualTo(30L);
        verify(delegate, never()).scan(any());
    }

    @Test
    void findPopulatesCacheOnDelegateHit() {
        Message m = msg(42, CH);
        when(delegate.find(42L)).thenReturn(Optional.of(m));

        Optional<Message> result = cache.find(42L);

        assertThat(result).isPresent();
        verify(delegate).find(42L);
        assertThat(cache.messagesCached()).isEqualTo(1);
    }

    @Test
    void deleteAllInvalidatesChannelCache() {
        cache.addToBuffer(CH, msg(1, CH));
        assertThat(cache.channelsCached()).isEqualTo(1);

        cache.deleteAll(CH);

        assertThat(cache.channelsCached()).isZero();
        verify(delegate).deleteAll(CH);
    }

    @Test
    void scanWithNullChannelIdFallsThrough() {
        MessageQuery q = MessageQuery.recent(10);
        when(delegate.scan(q)).thenReturn(List.of());

        cache.scan(q);
        verify(delegate).scan(q);
    }

    @Test
    void invalidateAllClearsEverything() {
        cache.addToBuffer(CH, msg(1, CH));
        cache.addToBuffer(UUID.randomUUID(), msg(2, UUID.randomUUID()));

        cache.invalidateAll();

        assertThat(cache.channelsCached()).isZero();
    }

    @Test
    void countMethodsPassThrough() {
        when(delegate.countByChannel(CH)).thenReturn(42);
        assertThat(cache.countByChannel(CH)).isEqualTo(42);
        verify(delegate).countByChannel(CH);
    }

    @Test
    void findLastMessageServesFromCache() {
        cache.addToBuffer(CH, msg(10, CH));
        cache.addToBuffer(CH, msg(20, CH));

        Optional<Message> result = cache.findLastMessage(CH);
        assertThat(result).isPresent();
        assertThat(result.get().id()).isEqualTo(20L);
        verify(delegate, never()).findLastMessage(any());
    }
}
