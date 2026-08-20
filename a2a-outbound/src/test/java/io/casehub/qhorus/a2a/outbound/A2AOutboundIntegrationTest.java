package io.casehub.qhorus.a2a.outbound;

import io.casehub.a2a.client.A2AClient;
import io.casehub.a2a.client.A2AClientRegistry;
import io.casehub.a2a.client.AuthConfig;
import io.casehub.a2a.model.A2AMessage;
import io.casehub.a2a.model.A2APart;
import io.casehub.a2a.model.A2ATask;
import io.casehub.a2a.model.A2ATaskState;
import io.casehub.a2a.model.A2ATaskStatus;
import io.casehub.platform.api.credentials.CredentialResolver;
import io.casehub.platform.api.identity.ActorType;
import io.casehub.platform.api.identity.CurrentPrincipal;
import io.casehub.platform.api.identity.TenancyConstants;
import io.casehub.qhorus.api.channel.Channel;
import io.casehub.qhorus.api.channel.ChannelCreateRequest;
import io.casehub.qhorus.api.gateway.ChannelRef;
import io.casehub.qhorus.api.gateway.DeliveryGuarantee;
import io.casehub.qhorus.api.gateway.OutboundMessage;
import io.casehub.qhorus.api.message.Message;
import io.casehub.qhorus.api.instance.ExternalAgentBinding;
import io.casehub.qhorus.api.message.MessageType;
import io.casehub.qhorus.api.store.query.MessageQuery;
import io.casehub.qhorus.persistence.memory.InMemoryChannelStore;
import io.casehub.qhorus.persistence.memory.InMemoryExternalAgentBindingStore;
import io.casehub.qhorus.persistence.memory.InMemoryMessageStore;
import io.casehub.qhorus.runtime.channel.ChannelService;
import io.casehub.qhorus.runtime.gateway.ChannelGateway;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@QuarkusTest
class A2AOutboundIntegrationTest {

    @Inject
    A2AOutboundBackend                backend;
    @Inject
    A2AInstanceResolver               resolver;
    @Inject
    ChannelService                    channelService;
    @Inject
    ChannelGateway                    gateway;
    @Inject
    InMemoryChannelStore              channelStore;
    @Inject
    InMemoryMessageStore              messageStore;
    @Inject
    InMemoryExternalAgentBindingStore bindingStore;

    @InjectMock
    A2AClientRegistry  clientRegistry;
    @InjectMock
    CredentialResolver credentialResolver;
    @InjectMock
    CurrentPrincipal   currentPrincipal;

    @BeforeEach
    void setUp() {
        Mockito.when(currentPrincipal.tenancyId()).thenReturn(TenancyConstants.DEFAULT_TENANT_ID);
        channelStore.clear();
        messageStore.clear();
    }

    @Test
    void cdiWiring_backendsInjected() {
        assertThat(backend).isNotNull();
        assertThat(resolver).isNotNull();
        assertThat(backend.backendId()).isEqualTo("a2a-outbound");
        assertThat(backend.deliveryGuarantee()).isEqualTo(DeliveryGuarantee.AT_LEAST_ONCE);
        assertThat(backend.actorType()).isEqualTo(ActorType.AGENT);
    }

    @Test
    void post_externalTarget_forwardsAndDispatchesResponse() throws Exception {
        Channel ch        = channelService.create(ChannelCreateRequest.builder("a2a-fwd-ch").build());
        UUID    channelId = ch.id();
        gateway.initChannel(channelId, new ChannelRef(channelId, ch.name()));
        gateway.registerBackend(channelId, backend, "agent");

        String extInstanceId = "ext-analyst-fwd";
        bindingStore.put(new ExternalAgentBinding(UUID.randomUUID(), extInstanceId,
                                                  "https://analyst.example.com/a2a", null, "1.0", Instant.now()));

        A2AClient mockClient = Mockito.mock(A2AClient.class);
        when(clientRegistry.getOrCreate(anyString(), any())).thenReturn(mockClient);

        A2AMessage responseMessage = new A2AMessage("agent",
                                                    List.of(new A2APart.TextPart("Analysis complete")), null, null, null, Map.of());
        A2ATask responseTask = new A2ATask("t1", null,
                                           new A2ATaskStatus(A2ATaskState.COMPLETED, responseMessage), List.of(), List.of());
        when(mockClient.send(any(A2AMessage.class), anyString())).thenReturn(responseTask);

        String corrId = UUID.randomUUID().toString();
        OutboundMessage outbound = new OutboundMessage(UUID.randomUUID(), 1L, "internal-agent",
                                                       MessageType.COMMAND, "Analyze this data", null, corrId, null,
                                                       ActorType.AGENT, List.of(), extInstanceId, null);

        backend.post(new ChannelRef(channelId, ch.name()), outbound);

        verify(mockClient).send(any(A2AMessage.class), anyString());

        List<Message> responses = messageStore.scan(MessageQuery.builder()
                                                                .channelId(channelId)
                                                                .sender(extInstanceId)
                                                                .messageType(MessageType.DONE)
                                                                .build());
        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).content()).isEqualTo("Analysis complete");
        assertThat(responses.get(0).correlationId()).isEqualTo(corrId);

        bindingStore.deleteByInstanceId(extInstanceId);
        gateway.closeChannel(channelId, new ChannelRef(channelId, ch.name()));
    }

    @Test
    void post_internalTarget_noOutboundCall() {
        Channel ch        = channelService.create(ChannelCreateRequest.builder("a2a-int-ch").build());
        UUID    channelId = ch.id();
        gateway.initChannel(channelId, new ChannelRef(channelId, ch.name()));
        gateway.registerBackend(channelId, backend, "agent");

        OutboundMessage outbound = new OutboundMessage(UUID.randomUUID(), 2L, "sender-1",
                                                       MessageType.COMMAND, "Do something", null, UUID.randomUUID().toString(), null,
                                                       ActorType.AGENT, List.of(), "internal-agent", null);

        backend.post(new ChannelRef(channelId, ch.name()), outbound);

        Mockito.verify(clientRegistry, Mockito.never()).getOrCreate(anyString(), any());

        gateway.closeChannel(channelId, new ChannelRef(channelId, ch.name()));
    }

    @Test
    void post_senderIsExternalAgent_loopGuardSkips() {
        Channel ch        = channelService.create(ChannelCreateRequest.builder("a2a-loop-ch").build());
        UUID    channelId = ch.id();
        gateway.initChannel(channelId, new ChannelRef(channelId, ch.name()));
        gateway.registerBackend(channelId, backend, "agent");

        String extInstanceId = "ext-loop-agent";
        bindingStore.put(new ExternalAgentBinding(UUID.randomUUID(), extInstanceId,
                                                  "https://loop.example.com/a2a", null, "1.0", Instant.now()));

        OutboundMessage outbound = new OutboundMessage(UUID.randomUUID(), 3L, extInstanceId,
                                                       MessageType.DONE, "Result", null, UUID.randomUUID().toString(), 1L,
                                                       ActorType.AGENT, List.of(), "some-target", null);

        backend.post(new ChannelRef(channelId, ch.name()), outbound);

        Mockito.verify(clientRegistry, Mockito.never()).getOrCreate(anyString(), any());

        bindingStore.deleteByInstanceId(extInstanceId);
        gateway.closeChannel(channelId, new ChannelRef(channelId, ch.name()));
    }

    @Test
    void post_withCredentials_resolvesAuth() throws Exception {
        Channel ch        = channelService.create(ChannelCreateRequest.builder("a2a-auth-ch").build());
        UUID    channelId = ch.id();
        gateway.initChannel(channelId, new ChannelRef(channelId, ch.name()));
        gateway.registerBackend(channelId, backend, "agent");

        String extInstanceId = "ext-secure-agent";
        bindingStore.put(new ExternalAgentBinding(UUID.randomUUID(), extInstanceId,
                                                  "https://secure.example.com/a2a", "my-secret-key", "1.0", Instant.now()));

        when(credentialResolver.resolve("my-secret-key"))
                .thenReturn(Map.of("token", "bearer-token-123", "type", "bearer"));

        A2AClient                  mockClient = Mockito.mock(A2AClient.class);
        ArgumentCaptor<AuthConfig> authCaptor = ArgumentCaptor.forClass(AuthConfig.class);
        when(clientRegistry.getOrCreate(anyString(), authCaptor.capture())).thenReturn(mockClient);

        A2ATask responseTask = new A2ATask("t2", null,
                                           new A2ATaskStatus(A2ATaskState.COMPLETED, null), List.of(), List.of());
        when(mockClient.send(any(A2AMessage.class), anyString())).thenReturn(responseTask);

        String corrId = UUID.randomUUID().toString();
        OutboundMessage outbound = new OutboundMessage(UUID.randomUUID(), 4L, "internal-agent",
                                                       MessageType.COMMAND, "Secure work", null, corrId, null,
                                                       ActorType.AGENT, List.of(), extInstanceId, null);

        backend.post(new ChannelRef(channelId, ch.name()), outbound);

        AuthConfig auth = authCaptor.getValue();
        assertThat(auth.type()).isEqualTo(AuthConfig.AuthType.BEARER);
        assertThat(auth.resolvedToken()).isEqualTo("bearer-token-123");

        bindingStore.deleteByInstanceId(extInstanceId);
        gateway.closeChannel(channelId, new ChannelRef(channelId, ch.name()));
    }
}
