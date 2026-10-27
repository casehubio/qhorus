package io.casehub.qhorus.runtime.message.protocol;

import io.casehub.qhorus.api.channel.Channel;
import io.casehub.qhorus.api.gateway.ChannelClosedEvent;
import io.casehub.qhorus.api.gateway.ChannelInitialisedEvent;
import io.casehub.qhorus.api.gateway.ChannelProtocolsChangedEvent;
import io.casehub.qhorus.api.spi.ProtocolLifecycleContext;
import io.casehub.qhorus.api.spi.ProtocolLifecycleHandler;
import io.casehub.qhorus.api.store.CrossTenantChannelStore;
import io.casehub.qhorus.api.store.CrossTenantMessageStore;
import io.casehub.qhorus.api.store.query.MessageQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProtocolLifecycleManagerTest {

    private StubHandler handler;
    private ProtocolLifecycleManager manager;
    private CrossTenantChannelStore channelStore;
    private CrossTenantMessageStore messageStore;

    @BeforeEach
    void setUp() {
        handler = new StubHandler("PLAYBOOK_PROTO");
        channelStore = mock(CrossTenantChannelStore.class);
        messageStore = mock(CrossTenantMessageStore.class);
        when(messageStore.scan(any(MessageQuery.class))).thenReturn(List.of());

        manager = new ProtocolLifecycleManager(
                List.of(handler), channelStore, messageStore,
                msg -> null);
    }

    @Test
    void initDispatchesToSupportingHandler() {
        UUID channelId = UUID.randomUUID();
        Channel ch = channelWithProtocols(channelId, "test-ch",
                List.of("PLAYBOOK_PROTO"), List.of("agent-a"));
        when(channelStore.findById(channelId)).thenReturn(Optional.of(ch));

        manager.onChannelInitialised(
                new ChannelInitialisedEvent(channelId, "test-ch", false));

        assertThat(handler.initCalls).hasSize(1);
        var ctx = handler.initCalls.get(0);
        assertThat(ctx.channelId()).isEqualTo(channelId);
        assertThat(ctx.protocolName()).isEqualTo("PLAYBOOK_PROTO");
        assertThat(ctx.protocolParticipants()).containsExactly("agent-a");
        assertThat(ctx.recovered()).isFalse();
    }

    @Test
    void initSkipsProtocolsWithNoHandler() {
        UUID channelId = UUID.randomUUID();
        Channel ch = channelWithProtocols(channelId, "test-ch",
                List.of("REQUEST_RESPONSE"), List.of());
        when(channelStore.findById(channelId)).thenReturn(Optional.of(ch));

        manager.onChannelInitialised(
                new ChannelInitialisedEvent(channelId, "test-ch", false));

        assertThat(handler.initCalls).isEmpty();
    }

    @Test
    void destroyUsesInternalMapNotStore() {
        UUID channelId = UUID.randomUUID();
        Channel ch = channelWithProtocols(channelId, "test-ch",
                List.of("PLAYBOOK_PROTO"), List.of());
        when(channelStore.findById(channelId)).thenReturn(Optional.of(ch));

        manager.onChannelInitialised(
                new ChannelInitialisedEvent(channelId, "test-ch", false));
        handler.reset();

        manager.onChannelClosed(new ChannelClosedEvent(channelId, "test-ch"));

        assertThat(handler.destroyCalls).hasSize(1);
        assertThat(handler.destroyCalls.get(0).channelId()).isEqualTo(channelId);
        assertThat(handler.destroyCalls.get(0).protocolName()).isEqualTo("PLAYBOOK_PROTO");
    }

    @Test
    void destroyNoOpsForChannelWithNoTrackedProtocols() {
        UUID channelId = UUID.randomUUID();
        manager.onChannelClosed(new ChannelClosedEvent(channelId, "unknown"));

        assertThat(handler.destroyCalls).isEmpty();
    }

    @Test
    void initErrorDoesNotPreventNextProtocol() {
        var handler2 = new StubHandler("SECOND_PROTO");
        manager = new ProtocolLifecycleManager(
                List.of(handler, handler2), channelStore, messageStore,
                msg -> null);

        UUID channelId = UUID.randomUUID();
        Channel ch = channelWithProtocols(channelId, "test-ch",
                List.of("PLAYBOOK_PROTO", "SECOND_PROTO"), List.of());
        when(channelStore.findById(channelId)).thenReturn(Optional.of(ch));

        handler.throwOnNextInit = new RuntimeException("boom");

        manager.onChannelInitialised(
                new ChannelInitialisedEvent(channelId, "test-ch", false));

        assertThat(handler.initCalls).isEmpty();
        assertThat(handler2.initCalls).hasSize(1);
    }

    @Test
    void runtimeChangeDestroysThenInits() {
        var handler2 = new StubHandler("NEW_PROTO");
        manager = new ProtocolLifecycleManager(
                List.of(handler, handler2), channelStore, messageStore,
                msg -> null);

        UUID channelId = UUID.randomUUID();
        Channel ch = channelWithProtocols(channelId, "test-ch",
                List.of("PLAYBOOK_PROTO"), List.of());
        when(channelStore.findById(channelId)).thenReturn(Optional.of(ch));

        manager.onChannelInitialised(
                new ChannelInitialisedEvent(channelId, "test-ch", false));
        handler.reset();

        Channel updated = channelWithProtocols(channelId, "test-ch",
                List.of("NEW_PROTO"), List.of());
        when(channelStore.findById(channelId)).thenReturn(Optional.of(updated));

        manager.onProtocolsChanged(new ChannelProtocolsChangedEvent(
                channelId, "test-ch",
                List.of("NEW_PROTO"), List.of("PLAYBOOK_PROTO")));

        assertThat(handler.destroyCalls).hasSize(1);
        assertThat(handler2.initCalls).hasSize(1);
    }

    @Test
    void channelNotFoundSkipsQuietly() {
        UUID channelId = UUID.randomUUID();
        when(channelStore.findById(channelId)).thenReturn(Optional.empty());

        manager.onChannelInitialised(
                new ChannelInitialisedEvent(channelId, "test-ch", false));

        assertThat(handler.initCalls).isEmpty();
    }

    @Test
    void emptyProtocolListNoLifecycleCalls() {
        UUID channelId = UUID.randomUUID();
        Channel ch = channelWithProtocols(channelId, "test-ch",
                List.of(), List.of());
        when(channelStore.findById(channelId)).thenReturn(Optional.of(ch));

        manager.onChannelInitialised(
                new ChannelInitialisedEvent(channelId, "test-ch", false));

        assertThat(handler.initCalls).isEmpty();
    }

    @Test
    void recoveryFlagPassedThrough() {
        UUID channelId = UUID.randomUUID();
        Channel ch = channelWithProtocols(channelId, "test-ch",
                List.of("PLAYBOOK_PROTO"), List.of());
        when(channelStore.findById(channelId)).thenReturn(Optional.of(ch));

        manager.onChannelInitialised(
                new ChannelInitialisedEvent(channelId, "test-ch", true));

        assertThat(handler.initCalls.get(0).recovered()).isTrue();
    }

    private Channel channelWithProtocols(UUID id, String name,
            List<String> protocols, List<String> participants) {
        return Channel.builder(name)
                .id(id)
                .protocols(protocols)
                .protocolParticipants(participants)
                .build();
    }

    static class StubHandler implements ProtocolLifecycleHandler {
        final Set<String> supported;
        final List<ProtocolLifecycleContext> initCalls = new CopyOnWriteArrayList<>();
        final List<DestroyCall> destroyCalls = new CopyOnWriteArrayList<>();
        RuntimeException throwOnNextInit;

        StubHandler(String... protocols) {
            this.supported = Set.of(protocols);
        }

        @Override
        public boolean supports(String protocolName) {
            return supported.contains(protocolName);
        }

        @Override
        public void onInit(ProtocolLifecycleContext context) {
            if (throwOnNextInit != null) {
                RuntimeException e = throwOnNextInit;
                throwOnNextInit = null;
                throw e;
            }
            initCalls.add(context);
        }

        @Override
        public void onDestroy(UUID channelId, String protocolName) {
            destroyCalls.add(new DestroyCall(channelId, protocolName));
        }

        void reset() {
            initCalls.clear();
            destroyCalls.clear();
            throwOnNextInit = null;
        }

        record DestroyCall(UUID channelId, String protocolName) {}
    }
}
