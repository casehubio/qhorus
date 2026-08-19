package io.casehub.qhorus.persistence.memory.contract;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import io.casehub.qhorus.api.instance.ExternalAgentBinding;
import io.casehub.qhorus.api.store.ExternalAgentBindingStore;

import static org.assertj.core.api.Assertions.assertThat;

abstract class ExternalAgentBindingStoreContractTest {

    protected abstract ExternalAgentBindingStore store();

    @Test
    void put_and_findByInstanceId() {
        ExternalAgentBinding binding = new ExternalAgentBinding(
            UUID.randomUUID(), "ext-agent-1", "https://example.com/a2a",
            "ext.agent.token", "1.0", Instant.now());
        store().put(binding);
        Optional<ExternalAgentBinding> found = store().findByInstanceId("ext-agent-1");
        assertThat(found).isPresent();
        assertThat(found.get().endpoint()).isEqualTo("https://example.com/a2a");
        assertThat(found.get().authConfigKey()).isEqualTo("ext.agent.token");
        assertThat(found.get().protocolVersion()).isEqualTo("1.0");
    }

    @Test
    void findByInstanceId_notFound_returnsEmpty() {
        assertThat(store().findByInstanceId("nonexistent")).isEmpty();
    }

    @Test
    void findAll_returnsAllBindings() {
        store().put(new ExternalAgentBinding(
            UUID.randomUUID(), "agent-a", "https://a.com/a2a", null, "1.0", Instant.now()));
        store().put(new ExternalAgentBinding(
            UUID.randomUUID(), "agent-b", "https://b.com/a2a", null, "1.0", Instant.now()));
        assertThat(store().findAll()).hasSize(2);
    }

    @Test
    void delete_removesById() {
        UUID id = UUID.randomUUID();
        store().put(new ExternalAgentBinding(
            id, "agent-del", "https://del.com/a2a", null, "1.0", Instant.now()));
        store().delete(id);
        assertThat(store().findByInstanceId("agent-del")).isEmpty();
    }

    @Test
    void deleteByInstanceId_removesBinding() {
        store().put(new ExternalAgentBinding(
            UUID.randomUUID(), "agent-del2", "https://del2.com/a2a",
            "token-key", "1.0", Instant.now()));
        store().deleteByInstanceId("agent-del2");
        assertThat(store().findByInstanceId("agent-del2")).isEmpty();
    }

    @Test
    void put_updatesExistingBinding() {
        String instanceId = "agent-update-" + UUID.randomUUID();
        UUID id = UUID.randomUUID();
        store().put(new ExternalAgentBinding(
            id, instanceId, "https://old.com/a2a", null, "1.0", Instant.now()));
        store().put(new ExternalAgentBinding(
            id, instanceId, "https://new.com/a2a", "new-key", "2.0", Instant.now()));
        Optional<ExternalAgentBinding> found = store().findByInstanceId(instanceId);
        assertThat(found).isPresent();
        assertThat(found.get().endpoint()).isEqualTo("https://new.com/a2a");
    }
}
