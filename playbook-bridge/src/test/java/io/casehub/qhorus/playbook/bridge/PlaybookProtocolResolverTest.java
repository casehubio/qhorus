package io.casehub.qhorus.playbook.bridge;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.casehub.qhorus.api.spi.ChannelProtocol;

class PlaybookProtocolResolverTest {

    private InMemoryProtocolDefinitionStore store;
    private PlaybookProtocolResolver resolver;
    private PlaybookExecutor executor;

    @BeforeEach
    void setUp() {
        store = new InMemoryProtocolDefinitionStore();
        executor = (yaml, vars) -> Map.of("advisories", List.of());
        resolver = new PlaybookProtocolResolver();
        resolver.definitionStore = store;
        resolver.executor = executor;
        resolver.cache = new CompilationCache();
    }

    private ProtocolDefinition def(UUID channelId, String name, String yaml) {
        return new ProtocolDefinition(UUID.randomUUID(), channelId, name,
                yaml, "DEFAULT", Instant.now());
    }

    @Test
    void resolve_returnsProtocol_forKnownDefinition() {
        UUID ch = UUID.randomUUID();
        store.put(def(ch, "approval-gate", "version: 1.0"));

        List<ChannelProtocol> result = resolver.resolve(List.of("approval-gate"), ch);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).protocolName()).isEqualTo("approval-gate");
    }

    @Test
    void resolve_returnsEmpty_forUnknownDefinition() {
        UUID ch = UUID.randomUUID();

        List<ChannelProtocol> result = resolver.resolve(List.of("nonexistent"), ch);

        assertThat(result).isEmpty();
    }

    @Test
    void resolve_resolvesMultipleProtocols() {
        UUID ch = UUID.randomUUID();
        store.put(def(ch, "gate-a", "a"));
        store.put(def(ch, "gate-b", "b"));

        List<ChannelProtocol> result = resolver.resolve(
                List.of("gate-a", "gate-b"), ch);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).protocolName()).isEqualTo("gate-a");
        assertThat(result.get(1).protocolName()).isEqualTo("gate-b");
    }

    @Test
    void resolve_cachesYamlContent() {
        UUID ch = UUID.randomUUID();
        store.put(def(ch, "cached", "original-yaml"));

        resolver.resolve(List.of("cached"), ch);
        assertThat(resolver.cache.get(ch, "cached")).contains("original-yaml");

        store.put(def(ch, "cached", "updated-yaml"));
        resolver.resolve(List.of("cached"), ch);
        assertThat(resolver.cache.get(ch, "cached"))
                .contains("original-yaml");
    }

    @Test
    void resolve_usesEvictedCacheEntry() {
        UUID ch = UUID.randomUUID();
        store.put(def(ch, "evictable", "v1"));
        resolver.resolve(List.of("evictable"), ch);

        resolver.cache.evict(ch, "evictable");
        store.put(def(ch, "evictable", "v2"));
        resolver.resolve(List.of("evictable"), ch);

        assertThat(resolver.cache.get(ch, "evictable")).contains("v2");
    }

    @Test
    void resolve_partialMatch_onlyReturnsKnown() {
        UUID ch = UUID.randomUUID();
        store.put(def(ch, "known", "yaml"));

        List<ChannelProtocol> result = resolver.resolve(
                List.of("known", "unknown"), ch);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).protocolName()).isEqualTo("known");
    }
}
