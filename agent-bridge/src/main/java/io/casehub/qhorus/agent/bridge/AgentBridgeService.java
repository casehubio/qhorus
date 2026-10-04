package io.casehub.qhorus.agent.bridge;

import io.casehub.platform.agent.AgentBackend;
import io.casehub.platform.agent.AgentSession;
import io.casehub.platform.agent.AgentSessionInit;
import io.casehub.qhorus.api.gateway.BackendRegistry;
import io.casehub.qhorus.api.message.MessageDispatcher;
import org.jboss.logging.Logger;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class AgentBridgeService {

    private static final Logger LOG = Logger.getLogger(AgentBridgeService.class);

    private final Map<String, AgentBackend> backends;
    private final BackendRegistry backendRegistry;
    private final MessageDispatcher dispatcher;
    private final ConcurrentHashMap<UUID, ManagedBinding> managedBindings = new ConcurrentHashMap<>();

    record ManagedBinding(AgentChannelBinding binding, AgentProviderBackend backend,
                          AgentSession session) {}

    public AgentBridgeService(List<AgentBackend> backends,
                               BackendRegistry backendRegistry,
                               MessageDispatcher dispatcher) {
        this.backends = new ConcurrentHashMap<>();
        for (AgentBackend b : backends) {
            this.backends.put(b.key(), b);
        }
        this.backendRegistry = backendRegistry;
        this.dispatcher = dispatcher;
    }

    public void createBinding(AgentChannelBinding binding) {
        AgentBackend agentBackend = backends.get(binding.backendKey());
        if (agentBackend == null) {
            throw new IllegalArgumentException(
                    "No AgentBackend found for key: " + binding.backendKey()
                    + ". Available: " + backends.keySet());
        }

        var providerBackend = new AgentProviderBackend(binding, agentBackend, dispatcher);

        AgentSession session = null;
        if (binding.persistent()) {
            AgentSessionInit init = new AgentSessionInit(
                    binding.agentBriefing(), List.of(), null, null, null, null);
            session = agentBackend.openSession(init);
            providerBackend.setSession(session);
        }

        backendRegistry.registerBackend(binding.channelId(), providerBackend, "agent");
        managedBindings.put(binding.id(), new ManagedBinding(binding, providerBackend, session));

        LOG.infof("Agent binding created: %s on channel %s (backend=%s, persistent=%s)",
                binding.agentInstanceId(), binding.channelId(), binding.backendKey(),
                binding.persistent());
    }

    public void destroyBinding(UUID bindingId) {
        ManagedBinding managed = managedBindings.remove(bindingId);
        if (managed == null) {
            LOG.warnf("Binding not found for destroy: %s", bindingId);
            return;
        }
        backendRegistry.deregisterBackend(managed.binding.channelId(),
                managed.backend.backendId());
        if (managed.session != null) {
            managed.session.close();
        }
        LOG.infof("Agent binding destroyed: %s", managed.binding.agentInstanceId());
    }

    public Optional<AgentChannelBinding> getBinding(UUID bindingId) {
        ManagedBinding managed = managedBindings.get(bindingId);
        return managed != null ? Optional.of(managed.binding) : Optional.empty();
    }

    public List<AgentChannelBinding> listBindings() {
        return managedBindings.values().stream()
                .map(ManagedBinding::binding)
                .toList();
    }
}
