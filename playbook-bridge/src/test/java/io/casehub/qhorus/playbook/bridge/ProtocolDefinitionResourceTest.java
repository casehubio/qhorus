package io.casehub.qhorus.playbook.bridge;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.ws.rs.core.Response;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ProtocolDefinitionResourceTest {

    private ProtocolDefinitionResource resource;
    private InMemoryProtocolDefinitionStore store;
    private CompilationCache cache;

    @BeforeEach
    void setUp() {
        store = new InMemoryProtocolDefinitionStore();
        cache = new CompilationCache();
        resource = new ProtocolDefinitionResource();
        resource.definitionStore = store;
        resource.compilationCache = cache;
    }

    private ProtocolDefinition def(UUID channelId, String name, String yaml) {
        return new ProtocolDefinition(UUID.randomUUID(), channelId, name,
                yaml, "DEFAULT", Instant.now());
    }

    @Test
    void put_storesDefinition() {
        UUID ch = UUID.randomUUID();
        var request = new ProtocolDefinitionResource.ProtocolDefinitionRequest(
                "version: 1.0\nsteps:\n  - check: open_queries\n", null);

        Response resp = resource.put(ch, "approval-gate", request);

        assertThat(resp.getStatus()).isEqualTo(200);
        assertThat(store.findByChannelAndProtocol(ch, "approval-gate")).isPresent();
    }

    @Test
    void put_evictsCache() {
        UUID ch = UUID.randomUUID();
        cache.put(ch, "gate", "old-yaml");

        var request = new ProtocolDefinitionResource.ProtocolDefinitionRequest(
                "version: 2.0\n", null);
        resource.put(ch, "gate", request);

        assertThat(cache.get(ch, "gate")).isEmpty();
    }

    @Test
    void put_rejectsBlankYaml() {
        UUID ch = UUID.randomUUID();
        var request = new ProtocolDefinitionResource.ProtocolDefinitionRequest("", null);

        Response resp = resource.put(ch, "gate", request);

        assertThat(resp.getStatus()).isEqualTo(400);
    }

    @Test
    void put_rejectsNullYaml() {
        UUID ch = UUID.randomUUID();
        var request = new ProtocolDefinitionResource.ProtocolDefinitionRequest(null, null);

        Response resp = resource.put(ch, "gate", request);

        assertThat(resp.getStatus()).isEqualTo(400);
    }

    @Test
    void put_rejectsInvalidDeadlineDuration() {
        UUID ch = UUID.randomUUID();
        var request = new ProtocolDefinitionResource.ProtocolDefinitionRequest(
                "deadlines:\n  timeout: not-a-duration\n", null);

        Response resp = resource.put(ch, "gate", request);

        assertThat(resp.getStatus()).isEqualTo(400);
    }

    @Test
    void put_acceptsValidDeadline() {
        UUID ch = UUID.randomUUID();
        var request = new ProtocolDefinitionResource.ProtocolDefinitionRequest(
                "deadlines:\n  timeout: PT5M\n", null);

        Response resp = resource.put(ch, "gate", request);

        assertThat(resp.getStatus()).isEqualTo(200);
    }

    @Test
    void get_returnsDefinition() {
        UUID ch = UUID.randomUUID();
        store.put(def(ch, "gate", "yaml-content"));

        Response resp = resource.get(ch, "gate");

        assertThat(resp.getStatus()).isEqualTo(200);
    }

    @Test
    void get_returns404_forMissing() {
        Response resp = resource.get(UUID.randomUUID(), "nonexistent");

        assertThat(resp.getStatus()).isEqualTo(404);
    }

    @Test
    void delete_removesDefinition() {
        UUID ch = UUID.randomUUID();
        store.put(def(ch, "gate", "yaml"));
        cache.put(ch, "gate", "cached-yaml");

        Response resp = resource.delete(ch, "gate");

        assertThat(resp.getStatus()).isEqualTo(204);
        assertThat(store.findByChannelAndProtocol(ch, "gate")).isEmpty();
        assertThat(cache.get(ch, "gate")).isEmpty();
    }

    @Test
    void list_returnsAllForChannel() {
        UUID ch = UUID.randomUUID();
        store.put(def(ch, "gate-a", "a"));
        store.put(def(ch, "gate-b", "b"));

        List<ProtocolDefinition> result = resource.list(ch);

        assertThat(result).hasSize(2);
    }

    @Test
    void put_defaultsTenancyId() {
        UUID ch = UUID.randomUUID();
        var request = new ProtocolDefinitionResource.ProtocolDefinitionRequest("version: 1.0\n", null);

        resource.put(ch, "gate", request);

        assertThat(store.findByChannelAndProtocol(ch, "gate"))
                .get().extracting(ProtocolDefinition::tenancyId)
                .isEqualTo("DEFAULT");
    }
}
