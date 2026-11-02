package io.casehub.qhorus.api.spi;

import java.util.Optional;

public interface ApiKeyIdentityResolver {
    Optional<ResolvedIdentity> resolve(String apiKey);
}
