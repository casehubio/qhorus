package io.casehub.qhorus.playbook.bridge;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ProtocolDefinitionStoreTest {

    private InMemoryProtocolDefinitionStore store;

    @BeforeEach
    void setUp() {
        store = new InMemoryProtocolDefinitionStore();
    }

    private ProtocolDefinition def(UUID channelId, String name, String yaml) {
        return new ProtocolDefinition(UUID.randomUUID(), channelId, name,
                yaml, "DEFAULT", Instant.now());
    }

    @Test
    void put_and_find() {
        UUID ch = UUID.randomUUID();
        store.put(def(ch, "approval-gate", "version: 1.0"));
        assertThat(store.findByChannelAndProtocol(ch, "approval-gate"))
                .isPresent()
                .get().extracting(ProtocolDefinition::yamlContent)
                .isEqualTo("version: 1.0");
    }

    @Test
    void findByChannel_returnsAll() {
        UUID ch = UUID.randomUUID();
        store.put(def(ch, "gate-a", "a"));
        store.put(def(ch, "gate-b", "b"));
        store.put(def(UUID.randomUUID(), "gate-c", "c"));
        assertThat(store.findByChannel(ch)).hasSize(2);
    }

    @Test
    void existsByProtocolName_true() {
        store.put(def(UUID.randomUUID(), "quorum", "q"));
        assertThat(store.existsByProtocolName("quorum")).isTrue();
    }

    @Test
    void existsByProtocolName_false() {
        assertThat(store.existsByProtocolName("missing")).isFalse();
    }

    @Test
    void delete_removesEntry() {
        UUID ch = UUID.randomUUID();
        store.put(def(ch, "gate", "g"));
        store.delete(ch, "gate");
        assertThat(store.findByChannelAndProtocol(ch, "gate")).isEmpty();
    }

    @Test
    void deleteByChannel_removesAll() {
        UUID ch = UUID.randomUUID();
        store.put(def(ch, "a", "1"));
        store.put(def(ch, "b", "2"));
        store.deleteByChannel(ch);
        assertThat(store.findByChannel(ch)).isEmpty();
    }

    @Test
    void put_overwrites() {
        UUID ch = UUID.randomUUID();
        store.put(def(ch, "gate", "v1"));
        store.put(def(ch, "gate", "v2"));
        assertThat(store.findByChannelAndProtocol(ch, "gate"))
                .get().extracting(ProtocolDefinition::yamlContent)
                .isEqualTo("v2");
    }
}
