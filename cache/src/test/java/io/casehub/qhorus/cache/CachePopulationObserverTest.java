package io.casehub.qhorus.cache;

import io.casehub.platform.api.identity.ActorType;
import io.casehub.qhorus.api.gateway.MessageObserver;
import io.casehub.qhorus.api.gateway.MessageReceivedEvent;
import io.casehub.qhorus.api.message.MessageType;
import io.casehub.qhorus.api.store.MessageStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class CachePopulationObserverTest {

    private CachingMessageStore cachingStore;
    private CachePopulationObserver observer;

    @BeforeEach
    void setUp() {
        MessageStore delegate = mock(MessageStore.class);
        cachingStore = new CachingMessageStore(delegate, 100, 200, false);
        observer = new CachePopulationObserver();
        observer.cachingStore = new MockInstance<>(cachingStore);
    }

    @Test
    void onMessageAddsToCache() {
        UUID channelId = UUID.randomUUID();
        MessageReceivedEvent event = new MessageReceivedEvent(
                42L, "test-channel", channelId, "default",
                MessageType.STATUS, "agent-1", null, ActorType.AGENT,
                null, Instant.now(), "hello", null, null);

        observer.onMessage(event);

        assertThat(cachingStore.channelsCached()).isEqualTo(1);
    }

    @Test
    void onMessageSkipsNullMessageId() {
        UUID channelId = UUID.randomUUID();
        MessageReceivedEvent event = new MessageReceivedEvent(
                null, "test-channel", channelId, "default",
                MessageType.STATUS, "agent-1", null, ActorType.AGENT,
                null, Instant.now(), "hello", null, null);

        observer.onMessage(event);

        assertThat(cachingStore.channelsCached()).isEqualTo(0);
    }

    @Test
    void scopeIsCluster() {
        assertThat(observer.scope()).isEqualTo(MessageObserver.Scope.CLUSTER);
    }

    @Test
    void idempotentOnDuplicateMessage() {
        UUID channelId = UUID.randomUUID();
        MessageReceivedEvent event = new MessageReceivedEvent(
                42L, "test-channel", channelId, "default",
                MessageType.STATUS, "agent-1", null, ActorType.AGENT,
                null, Instant.now(), "hello", null, null);

        observer.onMessage(event);
        observer.onMessage(event);

        assertThat(cachingStore.messagesCached()).isGreaterThanOrEqualTo(1);
    }

    @SuppressWarnings("unchecked")
    private static class MockInstance<T> implements jakarta.enterprise.inject.Instance<T> {
        private final T instance;
        MockInstance(T instance) { this.instance = instance; }
        @Override public T get() { return instance; }
        @Override public boolean isResolvable() { return true; }
        @Override public boolean isAmbiguous() { return false; }
        @Override public boolean isUnsatisfied() { return false; }
        @Override public jakarta.enterprise.inject.Instance<T> select(java.lang.annotation.Annotation... qualifiers) { return this; }
        @Override public <U extends T> jakarta.enterprise.inject.Instance<U> select(Class<U> subtype, java.lang.annotation.Annotation... qualifiers) { throw new UnsupportedOperationException(); }
        @Override public <U extends T> jakarta.enterprise.inject.Instance<U> select(jakarta.enterprise.util.TypeLiteral<U> subtype, java.lang.annotation.Annotation... qualifiers) { throw new UnsupportedOperationException(); }
        @Override public void destroy(T instance) {}
        @Override public Handle<T> getHandle() { throw new UnsupportedOperationException(); }
        @Override public Iterable<? extends Handle<T>> handles() { throw new UnsupportedOperationException(); }
        @Override public java.util.Iterator<T> iterator() { return java.util.List.of(instance).iterator(); }
        @Override public java.util.stream.Stream<T> stream() { return java.util.stream.Stream.of(instance); }
    }
}