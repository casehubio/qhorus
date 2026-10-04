package io.casehub.qhorus.agent.bridge;

import io.casehub.platform.agent.AgentBackend;
import io.casehub.platform.agent.AgentEvent;
import io.casehub.platform.agent.AgentSession;
import io.casehub.platform.agent.AgentSessionConfig;
import io.casehub.platform.agent.AgentSessionInit;
import io.casehub.qhorus.api.gateway.BackendRegistration;
import io.casehub.qhorus.api.gateway.BackendRegistry;
import io.casehub.qhorus.api.gateway.ChannelBackend;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AgentBridgeServiceTest {

    private static final UUID CHANNEL_ID = UUID.randomUUID();

    private final ConcurrentHashMap<String, ChannelBackend> registeredBackends = new ConcurrentHashMap<>();
    private final BackendRegistry backendRegistry = new BackendRegistry() {
        @Override public void registerBackend(UUID channelId, ChannelBackend backend, String backendType) {
            registeredBackends.put(backend.backendId(), backend);
        }
        @Override public void deregisterBackend(UUID channelId, String backendId) {
            registeredBackends.remove(backendId);
        }
        @Override public List<BackendRegistration> listBackends(UUID channelId) {
            return List.of();
        }
    };

    private AgentBackend stubBackend;
    private AgentBridgeService service;

    @BeforeEach
    void setUp() {
        registeredBackends.clear();
        AgentSession stubSession = new AgentSession() {
            @Override public Multi<AgentEvent> query(String prompt) {
                return Multi.createFrom().<AgentEvent>items(
                        new AgentEvent.TextDelta("ok"),
                        new AgentEvent.InvocationComplete(0, 0, 0, 0, 0, null, 0, 0, null, 0, false));
            }
            @Override public Uni<Void> interrupt() { return Uni.createFrom().voidItem(); }
            @Override public void close(Duration maxWait) {}
        };
        stubBackend = new AgentBackend() {
            @Override public String key() { return "claude"; }
            @Override public Multi<AgentEvent> invoke(AgentSessionConfig c) {
                return Multi.createFrom().empty();
            }
            @Override public AgentSession openSession(AgentSessionInit init) {
                return stubSession;
            }
        };
        service = new AgentBridgeService(List.of(stubBackend), backendRegistry, d -> null);
    }

    @Test
    void createBinding_registersBackend() {
        var binding = AgentChannelBinding.builder(CHANNEL_ID, "agent-1", "claude")
                .persistent(false).maxConcurrency(1).build();
        service.createBinding(binding);

        assertThat(registeredBackends).containsKey("agent-bridge-agent-1");
        assertThat(service.getBinding(binding.id())).isPresent();
    }

    @Test
    void createBinding_persistent_opensSession() {
        var binding = AgentChannelBinding.builder(CHANNEL_ID, "agent-2", "claude")
                .persistent(true).maxConcurrency(1).build();
        service.createBinding(binding);

        assertThat(registeredBackends).containsKey("agent-bridge-agent-2");
    }

    @Test
    void destroyBinding_deregistersBackend() {
        var binding = AgentChannelBinding.builder(CHANNEL_ID, "agent-3", "claude")
                .persistent(false).maxConcurrency(1).build();
        service.createBinding(binding);
        service.destroyBinding(binding.id());

        assertThat(registeredBackends).doesNotContainKey("agent-bridge-agent-3");
        assertThat(service.getBinding(binding.id())).isEmpty();
    }

    @Test
    void createBinding_unknownBackendKey_throws() {
        var binding = AgentChannelBinding.builder(CHANNEL_ID, "agent-4", "unknown-provider")
                .persistent(false).maxConcurrency(1).build();
        assertThatThrownBy(() -> service.createBinding(binding))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unknown-provider");
    }

    @Test
    void listBindings_returnsAll() {
        var b1 = AgentChannelBinding.builder(CHANNEL_ID, "a1", "claude")
                .persistent(false).maxConcurrency(1).build();
        var b2 = AgentChannelBinding.builder(CHANNEL_ID, "a2", "claude")
                .persistent(false).maxConcurrency(1).build();
        service.createBinding(b1);
        service.createBinding(b2);

        assertThat(service.listBindings()).hasSize(2);
    }
}
