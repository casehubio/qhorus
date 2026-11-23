package io.casehub.qhorus.playbook.bridge;

import io.casehub.platform.api.identity.CurrentPrincipal;
import io.casehub.qhorus.api.channel.Channel;
import io.casehub.qhorus.api.store.ChannelStore;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProtocolDefinitionResourceTest {

    private ProtocolDefinitionResource resource;
    private InMemoryProtocolDefinitionStore store;
    private CompilationCache cache;
    private ChannelStore     channelStore;
    private RecordingChannelManager channelManager;


    @BeforeEach
    void setUp() {
        store                     = new InMemoryProtocolDefinitionStore();
        cache                     = new CompilationCache();
        channelStore              = mock(ChannelStore.class);
        channelManager            = new RecordingChannelManager();
        resource                  = new ProtocolDefinitionResource();
        resource.definitionStore  = store;
        resource.compilationCache = cache;
        resource.channelStore     = channelStore;
        resource.channelManager   = channelManager;
        resource.currentPrincipal = mock(CurrentPrincipal.class);
        when(resource.currentPrincipal.tenancyId()).thenReturn("DEFAULT");
    }

    private ProtocolDefinition def(UUID channelId, String name, String yaml) {
        return new ProtocolDefinition(UUID.randomUUID(), channelId, name,
                yaml, "DEFAULT", Instant.now());
    }

    private void stubChannel(UUID id, List<String> protocols) {
        Channel ch = Channel.builder("test-channel").id(id).protocols(protocols).build();
        when(channelStore.find(id)).thenReturn(java.util.Optional.of(ch));
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

    @Test
    void put_addsProtocolToChannelList() {
        UUID ch = UUID.randomUUID();
        stubChannel(ch, List.of("existing-protocol"));
        var request = new ProtocolDefinitionResource.ProtocolDefinitionRequest("version: 1.0\n", null);

        resource.put(ch, "new-gate", request);

        assertThat(channelManager.lastSetProtocols(ch))
                .containsExactlyInAnyOrder("existing-protocol", "new-gate");
    }

    @Test
    void put_doesNotDuplicateProtocol() {
        UUID ch = UUID.randomUUID();
        stubChannel(ch, List.of("gate"));
        var request = new ProtocolDefinitionResource.ProtocolDefinitionRequest("version: 1.0\n", null);

        resource.put(ch, "gate", request);

        assertThat(channelManager.lastSetProtocols(ch)).isNull();
    }

    @Test
    void put_handlesChannelNotFound() {
        UUID ch      = UUID.randomUUID();
        var  request = new ProtocolDefinitionResource.ProtocolDefinitionRequest("version: 1.0\n", null);

        Response resp = resource.put(ch, "gate", request);

        assertThat(resp.getStatus()).isEqualTo(200);
        assertThat(channelManager.lastSetProtocols(ch)).isNull();
    }

    @Test
    void delete_removesProtocolFromChannelList() {
        UUID ch = UUID.randomUUID();
        stubChannel(ch, List.of("gate-a", "gate-b"));
        store.put(def(ch, "gate-a", "yaml"));

        resource.delete(ch, "gate-a");

        assertThat(channelManager.lastSetProtocols(ch)).containsExactly("gate-b");
    }

    @Test
    void delete_handlesProtocolNotInChannelList() {
        UUID ch = UUID.randomUUID();
        stubChannel(ch, List.of("other-protocol"));
        store.put(def(ch, "gate", "yaml"));

        resource.delete(ch, "gate");

        assertThat(channelManager.lastSetProtocols(ch)).isNull();
    }

    @Test
    void delete_handlesChannelNotFound() {
        UUID ch = UUID.randomUUID();
        store.put(def(ch, "gate", "yaml"));

        Response resp = resource.delete(ch, "gate");

        assertThat(resp.getStatus()).isEqualTo(204);
        assertThat(channelManager.lastSetProtocols(ch)).isNull();
    }
}
