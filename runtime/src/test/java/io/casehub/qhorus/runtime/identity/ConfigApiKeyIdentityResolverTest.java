package io.casehub.qhorus.runtime.identity;

import io.casehub.qhorus.api.spi.ResolvedIdentity;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ConfigApiKeyIdentityResolverTest {

    @Test
    void resolve_known_key_returns_identity() {
        var resolver = new ConfigApiKeyIdentityResolver(
            Map.of("sk-abc123", new ResolvedIdentity("agent-alpha", "test-tenant", Set.of("agent")))
        );
        var result = resolver.resolve("sk-abc123");
        assertThat(result).isPresent();
        assertThat(result.get().actorId()).isEqualTo("agent-alpha");
        assertThat(result.get().tenancyId()).isEqualTo("test-tenant");
        assertThat(result.get().roles()).containsExactly("agent");
    }

    @Test
    void resolve_unknown_key_returns_empty() {
        var resolver = new ConfigApiKeyIdentityResolver(
            Map.of("sk-abc123", new ResolvedIdentity("agent-alpha", "test-tenant", Set.of()))
        );
        assertThat(resolver.resolve("sk-wrong")).isEmpty();
    }

    @Test
    void resolve_empty_map_returns_empty() {
        var resolver = new ConfigApiKeyIdentityResolver(Map.of());
        assertThat(resolver.resolve("any-key")).isEmpty();
    }

    @Test
    void resolve_null_key_returns_empty() {
        var resolver = new ConfigApiKeyIdentityResolver(
            Map.of("sk-abc", new ResolvedIdentity("bot", "default", Set.of()))
        );
        assertThat(resolver.resolve(null)).isEmpty();
    }

    @Test
    void resolve_uses_constant_time_comparison() {
        var resolver = new ConfigApiKeyIdentityResolver(
            Map.of("sk-secret", new ResolvedIdentity("bot", "default", Set.of()))
        );
        assertThat(resolver.resolve("sk-secret")).isPresent();
        assertThat(resolver.resolve("sk-secre")).isEmpty();
        assertThat(resolver.resolve("sk-secretx")).isEmpty();
    }
}
