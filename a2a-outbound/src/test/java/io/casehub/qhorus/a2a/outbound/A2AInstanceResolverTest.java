package io.casehub.qhorus.a2a.outbound;

import io.casehub.qhorus.api.instance.ExternalAgentBinding;
import io.casehub.qhorus.api.store.ExternalAgentBindingStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

class A2AInstanceResolverTest {

    private ExternalAgentBindingStore store;
    private A2AInstanceResolver resolver;

    @BeforeEach
    void setUp() {
        store = Mockito.mock(ExternalAgentBindingStore.class);
        resolver = new A2AInstanceResolver(store);
    }

    @Test
    void resolve_withExistingBinding_returnsBinding() {
        final ExternalAgentBinding binding = new ExternalAgentBinding(
                UUID.randomUUID(), "ext-agent-1", "https://agent.example.com/", null, "1.0", Instant.now());
        when(store.findByInstanceId("ext-agent-1")).thenReturn(Optional.of(binding));

        Optional<ExternalAgentBinding> result = resolver.resolve("ext-agent-1");

        assertThat(result).isPresent().contains(binding);
    }

    @Test
    void resolve_withNoBinding_returnsEmpty() {
        when(store.findByInstanceId("internal-agent")).thenReturn(Optional.empty());

        Optional<ExternalAgentBinding> result = resolver.resolve("internal-agent");

        assertThat(result).isEmpty();
    }

    @Test
    void resolve_withNullTarget_returnsEmpty() {
        Optional<ExternalAgentBinding> result = resolver.resolve(null);

        assertThat(result).isEmpty();
    }

    @Test
    void resolve_withBlankTarget_returnsEmpty() {
        Optional<ExternalAgentBinding> result = resolver.resolve("  ");

        assertThat(result).isEmpty();
    }

    @Test
    void isExternalAgent_withBinding_returnsTrue() {
        when(store.findByInstanceId("ext-agent-1")).thenReturn(
                Optional.of(new ExternalAgentBinding(UUID.randomUUID(), "ext-agent-1",
                        "https://agent.example.com/", null, "1.0", Instant.now())));

        assertThat(resolver.isExternalAgent("ext-agent-1")).isTrue();
    }

    @Test
    void isExternalAgent_withNoBinding_returnsFalse() {
        when(store.findByInstanceId("internal-agent")).thenReturn(Optional.empty());

        assertThat(resolver.isExternalAgent("internal-agent")).isFalse();
    }

    @Test
    void isExternalAgent_withNull_returnsFalse() {
        assertThat(resolver.isExternalAgent(null)).isFalse();
    }
}
