package io.casehub.qhorus.a2a.outbound;

import io.casehub.a2a.client.A2AClient;
import io.casehub.a2a.client.A2AClientRegistry;
import io.casehub.a2a.client.AuthConfig;
import io.casehub.a2a.model.A2AMessage;
import io.casehub.a2a.model.A2APart;
import io.casehub.a2a.model.A2ATask;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.casehub.platform.api.credentials.CredentialResolver;
import io.casehub.platform.api.identity.ActorType;
import io.casehub.qhorus.api.gateway.BackendRegistry;
import io.casehub.qhorus.api.gateway.ChannelBackend;
import io.casehub.qhorus.api.gateway.ChannelInitialisedEvent;
import io.casehub.qhorus.api.gateway.ChannelRef;
import io.casehub.qhorus.api.gateway.DeliveryGuarantee;
import io.casehub.qhorus.api.gateway.OutboundMessage;
import io.casehub.qhorus.api.instance.ExternalAgentBinding;
import io.casehub.qhorus.api.message.MessageDispatch;
import io.casehub.qhorus.api.message.MessageDispatcher;
import io.casehub.qhorus.api.store.ExternalAgentBindingStore;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@ApplicationScoped
public class A2AOutboundBackend implements ChannelBackend {
    private static final Logger LOG = Logger.getLogger(A2AOutboundBackend.class);

    private final ExternalAgentBindingStore bindingStore;
    private final A2AClientRegistry         clientRegistry;
    private final CredentialResolver        credentialResolver;
    private final MessageDispatcher         messageDispatcher;
    private final A2AInstanceResolver       resolver;
    private final A2AResponseHandler        responseHandler;
    private final ObjectMapper              objectMapper;

    @Inject
    BackendRegistry backendRegistry;

    @Inject
    public A2AOutboundBackend(ExternalAgentBindingStore bindingStore,
                              A2AClientRegistry clientRegistry,
                              CredentialResolver credentialResolver,
                              MessageDispatcher messageDispatcher,
                              A2AInstanceResolver resolver,
                              A2AResponseHandler responseHandler,
                              ObjectMapper objectMapper) {
        this.bindingStore       = bindingStore;
        this.clientRegistry     = clientRegistry;
        this.credentialResolver = credentialResolver;
        this.messageDispatcher  = messageDispatcher;
        this.resolver           = resolver;
        this.responseHandler    = responseHandler;
        this.objectMapper       = objectMapper;
    }

    @Override
    public String backendId() {
        return "a2a-outbound";
    }

    @Override
    public ActorType actorType() {
        return ActorType.AGENT;
    }

    @Override
    public DeliveryGuarantee deliveryGuarantee() {
        return DeliveryGuarantee.AT_LEAST_ONCE;
    }

    @Override
    public void open(ChannelRef channel, Map<String, String> metadata) {
    }

    @Override
    public void post(ChannelRef channel, OutboundMessage message) {
        String target = message.target();
        if (target == null || target.isBlank()) {
            return;
        }

        if (resolver.isExternalAgent(message.sender())) {
            return;
        }

        var bindingOpt = resolver.resolve(target);
        if (bindingOpt.isEmpty()) {
            return;
        }
        ExternalAgentBinding binding = bindingOpt.get();

        AuthConfig auth   = resolveAuth(binding);
        A2AClient  client = clientRegistry.getOrCreate(binding.endpoint(), auth);

        A2AMessage a2aMessage = buildOutboundMessage(message);
        String     contextId  = message.correlationId() != null ? message.correlationId() : UUID.randomUUID().toString();

        try {
            A2ATask responseTask = client.send(a2aMessage, contextId);
            if (responseTask != null && responseTask.status() != null) {
                ResponseContext ctx = new ResponseContext(channel.id(), binding.instanceId(),
                                                          message.correlationId(), message.sequenceId());
                MessageDispatch dispatch = responseHandler.mapResponse(responseTask, ctx);
                messageDispatcher.dispatch(dispatch);
            }
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException("A2A outbound call to " + binding.endpoint() + " failed", e);
        }
    }

    @Override
    public void close(ChannelRef channel) {
    }

    void onChannelRecovery(@Observes ChannelInitialisedEvent event) {
        if (backendRegistry == null) {
            return;
        }
        if (!bindingStore.findAll().isEmpty()) {
            backendRegistry.registerBackend(event.channelId(), this, "agent");
        }
    }

    private AuthConfig resolveAuth(ExternalAgentBinding binding) {
        if (binding.authConfigKey() == null) {
            return AuthConfig.NONE;
        }
        Map<String, String> credentials = credentialResolver.resolve(binding.authConfigKey());
        if (credentials == null || credentials.isEmpty()) {
            return AuthConfig.NONE;
        }
        String token   = credentials.get("token");
        String typeStr = credentials.getOrDefault("type", "bearer");
        AuthConfig.AuthType authType = switch (typeStr.toLowerCase()) {
            case "api_key", "apikey" -> AuthConfig.AuthType.API_KEY;
            default -> AuthConfig.AuthType.BEARER;
        };
        return new AuthConfig(authType, null, token);
    }

    private A2AMessage buildOutboundMessage(OutboundMessage message) {
        List<A2APart> parts = new ArrayList<>();
        if (message.content() != null) {
            parts.add(new A2APart.TextPart(message.content()));
        }
        if (message.payload() != null) {
            try {
                JsonNode payloadNode = objectMapper.readTree(message.payload());
                parts.add(new A2APart.DataPart("application/json", payloadNode));
            } catch (Exception e) {
                parts.add(new A2APart.TextPart(message.payload()));
            }
        }
        return new A2AMessage("user", parts, null, null, null, Map.of());
    }
}
