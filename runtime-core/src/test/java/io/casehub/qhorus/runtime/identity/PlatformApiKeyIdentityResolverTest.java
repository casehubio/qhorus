package io.casehub.qhorus.runtime.identity;

import io.casehub.platform.api.authn.CredentialState;
import io.casehub.platform.api.authn.CredentialType;
import io.casehub.platform.api.authn.StaticCredentialRecord;
import io.casehub.platform.api.authn.StaticCredentialStore;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class PlatformApiKeyIdentityResolverTest {

    @Test
    void resolvesActiveCredential() {
        var store = stubStore(record("agent-1", "tenant-a", CredentialState.ACTIVE, null));
        var resolver = new PlatformApiKeyIdentityResolver(store, false);

        var result = resolver.resolve("secret-key");

        assertThat(result).isPresent();
        assertThat(result.get().actorId()).isEqualTo("agent-1");
        assertThat(result.get().tenancyId()).isEqualTo("tenant-a");
        assertThat(result.get().roles()).isEmpty();
    }

    @Test
    void resolvesGracePeriodCredential() {
        var store = stubStore(record("agent-1", "tenant-a", CredentialState.GRACE_PERIOD, null));
        var resolver = new PlatformApiKeyIdentityResolver(store, false);

        var result = resolver.resolve("old-key");

        assertThat(result).isPresent();
        assertThat(result.get().actorId()).isEqualTo("agent-1");
    }

    @Test
    void rejectsRevokedCredential() {
        var store = stubStore(record("agent-1", "tenant-a", CredentialState.REVOKED, null));
        var resolver = new PlatformApiKeyIdentityResolver(store, false);

        assertThat(resolver.resolve("revoked-key")).isEmpty();
    }

    @Test
    void rejectsExpiredCredential() {
        var store = stubStore(record("agent-1", "tenant-a", CredentialState.ACTIVE,
                Instant.now().minus(1, ChronoUnit.HOURS)));
        var resolver = new PlatformApiKeyIdentityResolver(store, false);

        assertThat(resolver.resolve("expired-key")).isEmpty();
    }

    @Test
    void returnsEmptyForUnknownKey() {
        var store = emptyStore();
        var resolver = new PlatformApiKeyIdentityResolver(store, false);

        assertThat(resolver.resolve("unknown")).isEmpty();
    }

    @Test
    void returnsEmptyForNullKey() {
        var store = emptyStore();
        var resolver = new PlatformApiKeyIdentityResolver(store, false);

        assertThat(resolver.resolve(null)).isEmpty();
    }

    private static StaticCredentialRecord record(String actorId, String tenancyId,
                                                  CredentialState state, Instant expiresAt) {
        return new StaticCredentialRecord(actorId, tenancyId, "api-key", "secret",
                CredentialType.API_KEY, Instant.now(), Instant.now(), state, expiresAt);
    }

    private static StaticCredentialStore stubStore(StaticCredentialRecord match) {
        return new StaticCredentialStore() {
            @Override public void store(StaticCredentialRecord r) {}
            @Override public Optional<StaticCredentialRecord> find(String a, String p, String t) { return Optional.empty(); }
            @Override public List<StaticCredentialRecord> findAll(String a, String t) { return List.of(); }
            @Override public void delete(String a, String p, String t) {}
            @Override public Optional<StaticCredentialRecord> lookup(String credential) { return Optional.of(match); }
        };
    }

    private static StaticCredentialStore emptyStore() {
        return new StaticCredentialStore() {
            @Override public void store(StaticCredentialRecord r) {}
            @Override public Optional<StaticCredentialRecord> find(String a, String p, String t) { return Optional.empty(); }
            @Override public List<StaticCredentialRecord> findAll(String a, String t) { return List.of(); }
            @Override public void delete(String a, String p, String t) {}
            @Override public Optional<StaticCredentialRecord> lookup(String credential) { return Optional.empty(); }
        };
    }
}
