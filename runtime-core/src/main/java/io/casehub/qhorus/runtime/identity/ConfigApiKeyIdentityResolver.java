package io.casehub.qhorus.runtime.identity;

import io.casehub.platform.api.identity.TenancyConstants;
import io.casehub.qhorus.api.spi.ApiKeyIdentityResolver;
import io.casehub.qhorus.api.spi.ResolvedIdentity;
import io.casehub.qhorus.runtime.config.QhorusConfig;
import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.security.MessageDigest;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@DefaultBean
@ApplicationScoped
public class ConfigApiKeyIdentityResolver implements ApiKeyIdentityResolver {

    private final Map<String, ResolvedIdentity> keyToIdentity;

    public record KeyEntry(String key, Optional<Set<String>> roles) {}

    @Inject
    public ConfigApiKeyIdentityResolver(QhorusConfig config) {
        this(buildReverseLookup(config));
    }

    ConfigApiKeyIdentityResolver(Map<String, ResolvedIdentity> keyToIdentity) {
        this.keyToIdentity = keyToIdentity;
    }

    @Override
    public Optional<ResolvedIdentity> resolve(String apiKey) {
        if (apiKey == null) return Optional.empty();
        for (var entry : keyToIdentity.entrySet()) {
            if (constantTimeEquals(entry.getKey(), apiKey)) {
                return Optional.of(entry.getValue());
            }
        }
        return Optional.empty();
    }

    private static boolean constantTimeEquals(String a, String b) {
        return MessageDigest.isEqual(a.getBytes(), b.getBytes());
    }

    private static Map<String, ResolvedIdentity> buildReverseLookup(QhorusConfig config) {
        var configKeys = config.apiKeys();
        if (configKeys == null || configKeys.isEmpty()) return Map.of();
        String tenancyId = config.defaultTenancyId().orElse(TenancyConstants.DEFAULT_TENANT_ID);
        var builder = new java.util.HashMap<String, ResolvedIdentity>();
        configKeys.forEach((name, entry) ->
            builder.put(entry.key(), new ResolvedIdentity(name, tenancyId, entry.roles().orElse(Set.of()))));
        return Map.copyOf(builder);
    }
}
