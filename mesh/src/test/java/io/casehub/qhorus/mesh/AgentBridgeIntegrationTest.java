package io.casehub.qhorus.mesh;

import io.casehub.platform.agent.AgentBackend;
import io.casehub.platform.agent.AgentEvent;
import io.casehub.platform.agent.AgentSession;
import io.casehub.platform.agent.AgentSessionConfig;
import io.casehub.platform.agent.AgentSessionInit;
import io.casehub.platform.api.identity.ActorType;
import io.casehub.qhorus.agent.bridge.AgentBridgeService;
import io.casehub.qhorus.agent.bridge.AgentChannelBinding;
import io.casehub.qhorus.agent.bridge.AgentProviderBackend;
import io.casehub.qhorus.api.channel.Channel;
import io.casehub.qhorus.api.channel.ChannelCreateRequest;
import io.casehub.qhorus.api.gateway.BackendRegistry;
import io.casehub.qhorus.api.gateway.ChannelRef;
import io.casehub.qhorus.api.gateway.OutboundMessage;
import io.casehub.qhorus.api.message.Message;
import io.casehub.qhorus.api.message.MessageDispatch;
import io.casehub.qhorus.api.message.MessageDispatcher;
import io.casehub.qhorus.api.message.MessageType;
import io.casehub.qhorus.api.store.MessageStore;
import io.casehub.qhorus.api.store.query.MessageQuery;
import io.casehub.qhorus.runtime.channel.ChannelService;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.mutiny.Multi;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
class AgentBridgeIntegrationTest {

    @Inject ChannelService channelService;
    @Inject MessageDispatcher messageDispatcher;
    @Inject MessageStore messageStore;
    @Inject BackendRegistry backendRegistry;

    @Test
    void bindingRegistration_andDirectInvocation() throws Exception {
        Channel ch = channelService.create(
                ChannelCreateRequest.builder("bridge-test-" + UUID.randomUUID()).build());

        CopyOnWriteArrayList<MessageDispatch> dispatched = new CopyOnWriteArrayList<>();
        CountDownLatch latch = new CountDownLatch(1);
        MessageDispatcher capturingDispatcher = dispatch -> {
            dispatched.add(dispatch);
            latch.countDown();
            return null;
        };

        AgentBackend echoBackend = new AgentBackend() {
            @Override public String key() { return "echo-test"; }
            @Override public Multi<AgentEvent> invoke(AgentSessionConfig c) {
                return Multi.createFrom().<AgentEvent>items(
                        new AgentEvent.TextDelta("Echo: " + c.userPrompt()),
                        new AgentEvent.InvocationComplete(10, 5, 0, 0, 0, null, 100L, 90L, "s", 1, false));
            }
            @Override public AgentSession openSession(AgentSessionInit init) { return null; }
        };

        var bridgeService = new AgentBridgeService(
                List.of(echoBackend), backendRegistry, capturingDispatcher);

        var binding = AgentChannelBinding.builder(ch.id(), "echo-agent", "echo-test")
                .persistent(false).maxConcurrency(1).build();
        bridgeService.createBinding(binding);

        assertThat(bridgeService.getBinding(binding.id())).isPresent();
        assertThat(bridgeService.listBindings()).hasSize(1);

        var inbound = new OutboundMessage(UUID.randomUUID(), 1L, "human-user",
                MessageType.COMMAND, "Hello agent", null, UUID.randomUUID().toString(), null,
                ActorType.HUMAN, List.of(), "echo-agent", null);

        var backend = new AgentProviderBackend(binding, echoBackend, capturingDispatcher);
        backend.postTracked(new ChannelRef(ch.id(), ch.name()), inbound);

        latch.await(5, TimeUnit.SECONDS);

        assertThat(dispatched).anyMatch(d ->
                d.type() == MessageType.RESPONSE
                && d.content() != null
                && d.content().contains("Echo: Hello agent"));

        bridgeService.destroyBinding(binding.id());
        assertThat(bridgeService.getBinding(binding.id())).isEmpty();
    }
}
