package io.casehub.qhorus.runtime.identity;

import io.casehub.platform.api.authn.CredentialState;
import io.casehub.platform.api.authn.StaticCredentialStore;
import io.casehub.qhorus.api.spi.ApiKeyIdentityResolver;
import io.casehub.qhorus.api.spi.ResolvedIdentity;
import io.quarkus.arc.properties.IfBuildProperty;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.Optional;
import java.util.Set;

@ApplicationScoped
@IfBuildProperty(name = "casehub.qhorus.identity.platform-api-keys", stringValue = "true", enableIfMissing = false)
public class PlatformApiKeyIdentityResolver implements ApiKeyIdentityResolver {

    private final StaticCredentialStore credentialStore;

    @Inject
    public PlatformApiKeyIdentityResolver(StaticCredentialStore credentialStore) {
        this.credentialStore = credentialStore;
    }

    PlatformApiKeyIdentityResolver(StaticCredentialStore store, boolean unused) {
        this.credentialStore = store;
    }

    @Override
    public Optional<ResolvedIdentity> resolve(String apiKey) {
        if (apiKey == null) return Optional.empty();
        return credentialStore.lookup(apiKey)
                .filter(r -> r.state() != CredentialState.REVOKED)
                .filter(r -> !r.isExpired())
                .map(r -> new ResolvedIdentity(r.actorId(), r.tenancyId(), Set.of()));
    }
}
