package io.casehub.qhorus.runtime.message.protocol;

import io.casehub.qhorus.api.channel.Channel;
import io.casehub.qhorus.api.channel.ChannelCreateRequest;
import io.casehub.qhorus.api.channel.ChannelManager;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
class ProtocolLifecycleIntegrationTest {

    @Inject
    ChannelManager channelManager;

    @Inject
    TestProtocolLifecycleHandler recorder;

    @BeforeEach
    void setUp() {
        recorder.reset();
    }

    @Test
    void channelCreationWithProtocolsTriggersInit() {
        var channelName = "lifecycle-init-" + System.nanoTime();

        var ch = QuarkusTransaction.requiringNew().call(() ->
                channelManager.create(ChannelCreateRequest.builder(channelName)
                        .protocols(List.of(TestProtocolLifecycleHandler.SUPPORTED_PROTOCOL))
                        .build()));

        assertThat(recorder.initCalls()).hasSize(1);
        var ctx = recorder.initCalls().get(0);
        assertThat(ctx.channelId()).isEqualTo(ch.id());
        assertThat(ctx.protocolName()).isEqualTo(TestProtocolLifecycleHandler.SUPPORTED_PROTOCOL);
        assertThat(ctx.recovered()).isFalse();
        assertThat(ctx.messageHistory()).isEmpty();
    }

    @Test
    void channelDeletionTriggersDestroy() {
        var channelName = "lifecycle-del-" + System.nanoTime();

        var chRef = new AtomicReference<Channel>();
        QuarkusTransaction.requiringNew().run(() ->
                chRef.set(channelManager.create(ChannelCreateRequest.builder(channelName)
                        .protocols(List.of(TestProtocolLifecycleHandler.SUPPORTED_PROTOCOL))
                        .build())));
        recorder.reset();

        QuarkusTransaction.requiringNew().run(() ->
                channelManager.delete(chRef.get().id(), true));

        assertThat(recorder.destroyCalls()).hasSize(1);
        assertThat(recorder.destroyCalls().get(0).channelId()).isEqualTo(chRef.get().id());
        assertThat(recorder.destroyCalls().get(0).protocolName())
                .isEqualTo(TestProtocolLifecycleHandler.SUPPORTED_PROTOCOL);
    }

    @Test
    void channelWithNoProtocolsDoesNotTriggerLifecycle() {
        var channelName = "lifecycle-none-" + System.nanoTime();

        QuarkusTransaction.requiringNew().run(() ->
                channelManager.create(ChannelCreateRequest.builder(channelName).build()));

        assertThat(recorder.initCalls()).isEmpty();
    }
}
