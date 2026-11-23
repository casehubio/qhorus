package io.casehub.qhorus.playbook.bridge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.inject.Inject;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.casehub.platform.api.identity.CurrentPrincipal;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;

@QuarkusTest
@TestProfile(JpaProtocolDefinitionStoreTest.Profile.class)
class JpaProtocolDefinitionStoreTest {

    public static class Profile implements QuarkusTestProfile {
        @Override
        public java.util.Map<String, String> getConfigOverrides() {
            return java.util.Map.of(
                    "quarkus.datasource.qhorus.db-kind", "h2",
                    "quarkus.datasource.qhorus.username", "sa",
                    "quarkus.datasource.qhorus.password", "",
                    "quarkus.datasource.qhorus.jdbc.url", "jdbc:h2:mem:playbook_jpa_store;DB_CLOSE_DELAY=-1",
                    "quarkus.datasource.qhorus.reactive", "false",
                    "quarkus.hibernate-orm.qhorus.database.generation", "drop-and-create"
            );
        }
    }

    @Inject ProtocolDefinitionStore store;
    @InjectMock CurrentPrincipal currentPrincipal;

    @BeforeEach
    void setUp() {
        when(currentPrincipal.tenancyId()).thenReturn("DEFAULT");
    }

    private ProtocolDefinition def(UUID channelId, String name, String yaml) {
        return new ProtocolDefinition(UUID.randomUUID(), channelId, name,
                yaml, "DEFAULT", Instant.now());
    }

    @Test
    void put_andFindByChannelAndProtocol() {
        UUID ch = UUID.randomUUID();
        ProtocolDefinition d = def(ch, "approval-gate", "version: 1.0\nsteps:\n  - check: open_queries\n");
        store.put(d);

        Optional<ProtocolDefinition> found = store.findByChannelAndProtocol(ch, "approval-gate");

        assertThat(found).isPresent();
        assertThat(found.get().protocolName()).isEqualTo("approval-gate");
        assertThat(found.get().yamlContent()).isEqualTo("version: 1.0\nsteps:\n  - check: open_queries\n");
        assertThat(found.get().tenancyId()).isEqualTo("DEFAULT");
    }

    @Test
    void put_upsertsOnDuplicate() {
        UUID ch = UUID.randomUUID();
        store.put(def(ch, "gate", "v1"));
        store.put(new ProtocolDefinition(UUID.randomUUID(), ch, "gate",
                "v2", "DEFAULT", Instant.now()));

        Optional<ProtocolDefinition> found = store.findByChannelAndProtocol(ch, "gate");

        assertThat(found).isPresent();
        assertThat(found.get().yamlContent()).isEqualTo("v2");
    }

    @Test
    void findByChannel_returnsAllForChannel() {
        UUID ch = UUID.randomUUID();
        store.put(def(ch, "gate-a", "a"));
        store.put(def(ch, "gate-b", "b"));
        store.put(def(UUID.randomUUID(), "gate-c", "c"));

        List<ProtocolDefinition> result = store.findByChannel(ch);

        assertThat(result).hasSize(2)
                .extracting(ProtocolDefinition::protocolName)
                .containsExactlyInAnyOrder("gate-a", "gate-b");
    }

    @Test
    void existsByProtocolName_trueForExisting() {
        store.put(def(UUID.randomUUID(), "exists-gate", "yaml"));

        assertThat(store.existsByProtocolName("exists-gate")).isTrue();
    }

    @Test
    void existsByProtocolName_falseForMissing() {
        assertThat(store.existsByProtocolName("nonexistent-" + UUID.randomUUID())).isFalse();
    }

    @Test
    void delete_removesDefinition() {
        UUID ch = UUID.randomUUID();
        store.put(def(ch, "del-gate", "yaml"));
        assertThat(store.findByChannelAndProtocol(ch, "del-gate")).isPresent();

        store.delete(ch, "del-gate");

        assertThat(store.findByChannelAndProtocol(ch, "del-gate")).isEmpty();
    }

    @Test
    void deleteByChannel_removesAllForChannel() {
        UUID ch = UUID.randomUUID();
        store.put(def(ch, "gate-x", "x"));
        store.put(def(ch, "gate-y", "y"));

        store.deleteByChannel(ch);

        assertThat(store.findByChannel(ch)).isEmpty();
    }

    @Test
    void tenancyFiltering_isolatesTenants() {
        UUID ch = UUID.randomUUID();
        store.put(new ProtocolDefinition(UUID.randomUUID(), ch, "shared-name",
                "tenant-a-yaml", "TENANT_A", Instant.now()));

        when(currentPrincipal.tenancyId()).thenReturn("TENANT_B");
        store.put(new ProtocolDefinition(UUID.randomUUID(), ch, "shared-name",
                "tenant-b-yaml", "TENANT_B", Instant.now()));

        when(currentPrincipal.tenancyId()).thenReturn("TENANT_A");
        Optional<ProtocolDefinition> resultA = store.findByChannelAndProtocol(ch, "shared-name");
        assertThat(resultA).isPresent();
        assertThat(resultA.get().yamlContent()).isEqualTo("tenant-a-yaml");

        when(currentPrincipal.tenancyId()).thenReturn("TENANT_B");
        Optional<ProtocolDefinition> resultB = store.findByChannelAndProtocol(ch, "shared-name");
        assertThat(resultB).isPresent();
        assertThat(resultB.get().yamlContent()).isEqualTo("tenant-b-yaml");
    }

    @Test
    void tenancyFiltering_existsByProtocolName() {
        store.put(new ProtocolDefinition(UUID.randomUUID(), UUID.randomUUID(),
                "tenant-scoped-" + UUID.randomUUID(), "yaml", "TENANT_X", Instant.now()));

        when(currentPrincipal.tenancyId()).thenReturn("TENANT_Y");
        assertThat(store.existsByProtocolName("tenant-scoped-" + UUID.randomUUID())).isFalse();
    }

    @Test
    void tenancyFiltering_deleteOnlyAffectsOwnTenant() {
        UUID ch = UUID.randomUUID();
        String protocolName = "cross-tenant-del-" + UUID.randomUUID();

        store.put(new ProtocolDefinition(UUID.randomUUID(), ch, protocolName,
                "yaml-default", "DEFAULT", Instant.now()));

        when(currentPrincipal.tenancyId()).thenReturn("OTHER");
        store.put(new ProtocolDefinition(UUID.randomUUID(), ch, protocolName,
                "yaml-other", "OTHER", Instant.now()));

        when(currentPrincipal.tenancyId()).thenReturn("OTHER");
        store.delete(ch, protocolName);

        when(currentPrincipal.tenancyId()).thenReturn("DEFAULT");
        assertThat(store.findByChannelAndProtocol(ch, protocolName)).isPresent();
    }
}
