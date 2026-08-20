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
import io.casehub.qhorus.api.gateway.BackendRegistry;
import io.casehub.qhorus.api.gateway.ChannelBackend;
import io.casehub.qhorus.api.gateway.ChannelInitialisedEvent;
import io.casehub.qhorus.api.gateway.ChannelRef;
import io.casehub.qhorus.api.gateway.DeliveryGuarantee;
import io.casehub.qhorus.api.gateway.OutboundMessage;
import io.casehub.qhorus.api.instance.ExternalAgentBinding;
import io.casehub.qhorus.api.message.DispatchResult;
import io.casehub.qhorus.api.message.MessageDispatch;
import io.casehub.qhorus.api.message.MessageDispatcher;
import io.casehub.qhorus.api.message.MessageType;
import io.casehub.qhorus.api.store.ExternalAgentBindingStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class A2AOutboundBackendTest {

    private ExternalAgentBindingStore bindingStore;
    private A2AClientRegistry clientRegistry;
    private CredentialResolver credentialResolver;
    private MessageDispatcher messageDispatcher;
    private A2AOutboundBackend backend;

    @BeforeEach
    void setUp() {
        bindingStore = Mockito.mock(ExternalAgentBindingStore.class);
        clientRegistry = Mockito.mock(A2AClientRegistry.class);
        credentialResolver = Mockito.mock(CredentialResolver.class);
        messageDispatcher = Mockito.mock(MessageDispatcher.class);

        A2AInstanceResolver resolver = new A2AInstanceResolver(bindingStore);
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        A2AResponseHandler responseHandler = new A2AResponseHandler(mapper);

        backend = new A2AOutboundBackend(bindingStore, clientRegistry, credentialResolver,
                messageDispatcher, resolver, responseHandler, mapper);
    }

    @Test
    void backendId_returnsA2AOutbound() {
        assertThat(backend.backendId()).isEqualTo("a2a-outbound");
    }

    @Test
    void actorType_returnsAgent() {
        assertThat(backend.actorType()).isEqualTo(ActorType.AGENT);
    }

    @Test
    void deliveryGuarantee_returnsAtLeastOnce() {
        assertThat(backend.deliveryGuarantee()).isEqualTo(DeliveryGuarantee.AT_LEAST_ONCE);
    }

    @Test
    void post_withNoTarget_doesNothing() throws Exception {
        final OutboundMessage message = outboundMessage(null, "internal-sender", MessageType.COMMAND);

        backend.post(channelRef(), message);

        verify(clientRegistry, never()).getOrCreate(anyString(), any());
    }

    @Test
    void post_withInternalTarget_doesNothing() throws Exception {
        final OutboundMessage message = outboundMessage("internal-agent", "sender-1", MessageType.COMMAND);
        when(bindingStore.findByInstanceId("internal-agent")).thenReturn(Optional.empty());

        backend.post(channelRef(), message);

        verify(clientRegistry, never()).getOrCreate(anyString(), any());
    }

    @Test
    void post_senderIsExternalAgent_loopGuardSkips() throws Exception {
        final String extAgentId = "ext-agent-1";
        when(bindingStore.findByInstanceId(extAgentId)).thenReturn(Optional.of(binding(extAgentId)));

        final OutboundMessage message = outboundMessage("some-target", extAgentId, MessageType.COMMAND);

        backend.post(channelRef(), message);

        verify(clientRegistry, never()).getOrCreate(anyString(), any());
    }

    @Test
    void post_withExternalTarget_forwardsViaA2AClient() throws Exception {
        final String extAgentId = "ext-agent-1";
        final ExternalAgentBinding binding = binding(extAgentId);
        when(bindingStore.findByInstanceId(extAgentId)).thenReturn(Optional.of(binding));
        when(bindingStore.findByInstanceId("internal-sender")).thenReturn(Optional.empty());

        A2AClient client = Mockito.mock(A2AClient.class);
        when(clientRegistry.getOrCreate(anyString(), any())).thenReturn(client);
        when(client.send(any(), anyString())).thenReturn(
                new A2ATask("t1", null, new A2ATaskStatus(A2ATaskState.COMPLETED, null), List.of(), List.of()));
        when(messageDispatcher.dispatch(any())).thenReturn(dummyResult());

        final ChannelRef ref = channelRef();
        final OutboundMessage message = outboundMessage(extAgentId, "internal-sender", MessageType.COMMAND,
                "Do this work", UUID.randomUUID().toString(), 1L);

        backend.post(ref, message);

        verify(client).send(any(A2AMessage.class), anyString());
    }

    @Test
    void post_withExternalTarget_dispatchesResponseBack() throws Exception {
        final String extAgentId = "ext-agent-1";
        final ExternalAgentBinding binding = binding(extAgentId);
        when(bindingStore.findByInstanceId(extAgentId)).thenReturn(Optional.of(binding));
        when(bindingStore.findByInstanceId("internal-sender")).thenReturn(Optional.empty());

        A2AClient client = Mockito.mock(A2AClient.class);
        when(clientRegistry.getOrCreate(anyString(), any())).thenReturn(client);
        when(client.send(any(), anyString())).thenReturn(
                new A2ATask("t1", null, new A2ATaskStatus(A2ATaskState.COMPLETED, null), List.of(), List.of()));
        when(messageDispatcher.dispatch(any())).thenReturn(dummyResult());

        final String corrId = UUID.randomUUID().toString();
        final ChannelRef ref = channelRef();
        final OutboundMessage message = outboundMessage(extAgentId, "internal-sender", MessageType.COMMAND,
                "Do this work", corrId, 1L);

        backend.post(ref, message);

        ArgumentCaptor<MessageDispatch> captor = ArgumentCaptor.forClass(MessageDispatch.class);
        verify(messageDispatcher).dispatch(captor.capture());
        MessageDispatch dispatched = captor.getValue();
        assertThat(dispatched.type()).isEqualTo(MessageType.DONE);
        assertThat(dispatched.sender()).isEqualTo(extAgentId);
        assertThat(dispatched.channelId()).isEqualTo(ref.id());
        assertThat(dispatched.correlationId()).isEqualTo(corrId);
    }

    @Test
    void post_withCredentials_resolvesViaCredentialResolver() throws Exception {
        final String extAgentId = "ext-agent-1";
        final ExternalAgentBinding binding = new ExternalAgentBinding(UUID.randomUUID(), extAgentId,
                "https://agent.example.com/", "my-auth-key", "1.0", Instant.now());
        when(bindingStore.findByInstanceId(extAgentId)).thenReturn(Optional.of(binding));
        when(bindingStore.findByInstanceId("internal-sender")).thenReturn(Optional.empty());
        when(credentialResolver.resolve("my-auth-key")).thenReturn(Map.of("token", "secret-token", "type", "bearer"));

        A2AClient client = Mockito.mock(A2AClient.class);
        ArgumentCaptor<AuthConfig> authCaptor = ArgumentCaptor.forClass(AuthConfig.class);
        when(clientRegistry.getOrCreate(anyString(), authCaptor.capture())).thenReturn(client);
        when(client.send(any(), anyString())).thenReturn(
                new A2ATask("t1", null, new A2ATaskStatus(A2ATaskState.COMPLETED, null), List.of(), List.of()));
        when(messageDispatcher.dispatch(any())).thenReturn(dummyResult());

        final ChannelRef ref = channelRef();
        final OutboundMessage message = outboundMessage(extAgentId, "internal-sender", MessageType.COMMAND,
                "work", UUID.randomUUID().toString(), 1L);

        backend.post(ref, message);

        AuthConfig auth = authCaptor.getValue();
        assertThat(auth.type()).isEqualTo(AuthConfig.AuthType.BEARER);
        assertThat(auth.resolvedToken()).isEqualTo("secret-token");
    }

    @Test
    void post_nonTargetedMessageType_skips() throws Exception {
        final OutboundMessage message = outboundMessage(null, "sender", MessageType.EVENT);

        backend.post(channelRef(), message);

        verify(bindingStore, never()).findByInstanceId(anyString());
    }

    @Test
    void onChannelRecovery_withBindings_registersOnChannel() {
        BackendRegistry registry = Mockito.mock(BackendRegistry.class);
        backend.backendRegistry = registry;

        when(bindingStore.findAll()).thenReturn(List.of(binding("ext-agent-1")));

        backend.onChannelRecovery(new ChannelInitialisedEvent(UUID.randomUUID(), "test-ch", true));

        verify(registry).registerBackend(any(UUID.class), any(ChannelBackend.class), anyString());
    }

    @Test
    void onChannelRecovery_noBindings_doesNotRegister() {
        BackendRegistry registry = Mockito.mock(BackendRegistry.class);
        backend.backendRegistry = registry;

        when(bindingStore.findAll()).thenReturn(List.of());

        backend.onChannelRecovery(new ChannelInitialisedEvent(UUID.randomUUID(), "test-ch", true));

        verify(registry, never()).registerBackend(any(UUID.class), any(ChannelBackend.class), anyString());
    }


    @Test
    void post_contentMappedToTextPart() throws Exception {
        final String extAgentId = "ext-agent-1";
        when(bindingStore.findByInstanceId(extAgentId)).thenReturn(Optional.of(binding(extAgentId)));
        when(bindingStore.findByInstanceId("internal-sender")).thenReturn(Optional.empty());

        A2AClient client = Mockito.mock(A2AClient.class);
        ArgumentCaptor<A2AMessage> msgCaptor = ArgumentCaptor.forClass(A2AMessage.class);
        when(clientRegistry.getOrCreate(anyString(), any())).thenReturn(client);
        when(client.send(msgCaptor.capture(), anyString())).thenReturn(
                new A2ATask("t1", null, new A2ATaskStatus(A2ATaskState.COMPLETED, null), List.of(), List.of()));
        when(messageDispatcher.dispatch(any())).thenReturn(dummyResult());

        final ChannelRef ref = channelRef();
        final OutboundMessage message = outboundMessage(extAgentId, "internal-sender", MessageType.COMMAND,
                "Please analyze this", UUID.randomUUID().toString(), 1L);

        backend.post(ref, message);

        A2AMessage sent = msgCaptor.getValue();
        assertThat(sent.parts()).hasSize(1);
        assertThat(sent.parts().get(0)).isInstanceOf(A2APart.TextPart.class);
        assertThat(((A2APart.TextPart) sent.parts().get(0)).text()).isEqualTo("Please analyze this");
    }

    private static ChannelRef channelRef() {
        return new ChannelRef(UUID.randomUUID(), "test-channel");
    }

    private static OutboundMessage outboundMessage(String target, String sender, MessageType type) {
        return outboundMessage(target, sender, type, null, null, null);
    }

    private static OutboundMessage outboundMessage(String target, String sender, MessageType type,
                                                   String content, String correlationId, Long inReplyTo) {
        return new OutboundMessage(UUID.randomUUID(), 100L, sender, type, content, null,
                correlationId, inReplyTo, ActorType.AGENT, List.of(), target, null);
    }

    private static ExternalAgentBinding binding(String instanceId) {
        return new ExternalAgentBinding(UUID.randomUUID(), instanceId,
                "https://agent.example.com/", null, "1.0", Instant.now());
    }

    private static DispatchResult dummyResult() {
        return new DispatchResult(1L, UUID.randomUUID(), "sender", MessageType.DONE,
                null, null, List.of(), null, null, null, null, 0, List.of());
    }
}
