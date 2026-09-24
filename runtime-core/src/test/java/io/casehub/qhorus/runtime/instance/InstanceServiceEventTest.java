package io.casehub.qhorus.runtime.instance;

import io.casehub.qhorus.api.instance.Instance;
import io.casehub.qhorus.api.instance.InstanceDeregisteredEvent;
import io.casehub.qhorus.api.instance.InstanceRegisteredEvent;
import io.casehub.qhorus.api.store.InstanceStore;
import jakarta.enterprise.event.Event;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class InstanceServiceEventTest {

    private InstanceService service;
    private InstanceStore store;
    @SuppressWarnings("unchecked")
    private final Event<InstanceRegisteredEvent> registeredEvent = mock(Event.class);
    @SuppressWarnings("unchecked")
    private final Event<InstanceDeregisteredEvent> deregisteredEvent = mock(Event.class);

    private final Map<String, Instance> instances = new HashMap<>();
    private final Map<UUID, List<String>> caps = new HashMap<>();

    @BeforeEach
    void setUp() {
        instances.clear();
        caps.clear();
        store = mock(InstanceStore.class);
        when(store.findByInstanceId(any())).thenAnswer(inv ->
                Optional.ofNullable(instances.get((String) inv.getArgument(0))));
        when(store.put(any(Instance.class))).thenAnswer(inv -> {
            Instance i = inv.getArgument(0);
            Instance saved = i.id() != null ? i : i.toBuilder().id(UUID.randomUUID()).build();
            instances.put(saved.instanceId(), saved);
            return saved;
        });
        doAnswer(inv -> {
            caps.put(inv.getArgument(0), new ArrayList<>(inv.getArgument(1)));
            return null;
        }).when(store).putCapabilities(any(UUID.class), any());
        when(store.findCapabilities(any(UUID.class))).thenAnswer(inv ->
                caps.getOrDefault((UUID) inv.getArgument(0), List.of()));
        doAnswer(inv -> {
            UUID id = inv.getArgument(0);
            instances.values().removeIf(i -> id.equals(i.id()));
            caps.remove(id);
            return null;
        }).when(store).delete(any(UUID.class));

        service = new InstanceService(store, registeredEvent, deregisteredEvent);
        reset(registeredEvent, deregisteredEvent);
    }

    @Test
    void register_firesEventWithCapabilities() {
        service.register("agent-1", "Test agent", List.of("analyzer", "monitor"));

        ArgumentCaptor<InstanceRegisteredEvent> captor = ArgumentCaptor.forClass(InstanceRegisteredEvent.class);
        verify(registeredEvent).fireAsync(captor.capture());

        InstanceRegisteredEvent event = captor.getValue();
        assertThat(event.instanceId()).isEqualTo("agent-1");
        assertThat(event.previousCapabilities()).isEmpty();
        assertThat(event.currentCapabilities()).containsExactlyInAnyOrder("analyzer", "monitor");
    }

    @Test
    void register_existingAgent_firesDiffCapabilities() {
        service.register("agent-1", "Test agent", List.of("analyzer"));
        reset(registeredEvent);

        service.register("agent-1", "Updated agent", List.of("analyzer", "monitor"));

        ArgumentCaptor<InstanceRegisteredEvent> captor = ArgumentCaptor.forClass(InstanceRegisteredEvent.class);
        verify(registeredEvent).fireAsync(captor.capture());

        InstanceRegisteredEvent event = captor.getValue();
        assertThat(event.previousCapabilities()).containsExactly("analyzer");
        assertThat(event.currentCapabilities()).containsExactlyInAnyOrder("analyzer", "monitor");
    }

    @Test
    void deregister_firesEventWithCapabilities() {
        service.register("agent-1", "Test agent", List.of("analyzer"));
        reset(registeredEvent);

        service.deregister("agent-1");

        ArgumentCaptor<InstanceDeregisteredEvent> captor = ArgumentCaptor.forClass(InstanceDeregisteredEvent.class);
        verify(deregisteredEvent).fireAsync(captor.capture());

        InstanceDeregisteredEvent event = captor.getValue();
        assertThat(event.instanceId()).isEqualTo("agent-1");
        assertThat(event.capabilities()).containsExactly("analyzer");
    }

    @Test
    void deregister_nonExistent_doesNotFire() {
        service.deregister("ghost");
        verifyNoInteractions(deregisteredEvent);
    }

    @Test
    void register_nullEvents_doesNotThrow() {
        InstanceService noEvents = new InstanceService(store);
        noEvents.register("agent-1", "Test", List.of("cap"));
        noEvents.deregister("agent-1");
    }
}
