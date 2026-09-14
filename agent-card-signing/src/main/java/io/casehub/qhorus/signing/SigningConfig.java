package io.casehub.qhorus.signing;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import java.util.Optional;

@ConfigMapping(prefix = "casehub.qhorus.signing")
public interface SigningConfig {

    @WithDefault("true")
    boolean enabled();

    @WithDefault("system:agent-card-signer")
    String actorId();

    @WithDefault("default")
    String keyId();

    Optional<String> jwksUrl();

    JwksCache jwksCache();

    Trust trust();

    interface JwksCache {
        @WithDefault("3600")
        int ttl();

        @WithDefault("65536")
        int maxResponseBytes();

        @WithDefault("5000")
        int connectTimeoutMs();

        @WithDefault("5000")
        int readTimeoutMs();
    }

    interface Trust {
        @WithDefault("0.6")
        double verifiedFloorScore();

        @WithDefault("0.15")
        double dimensionWeight();
    }
}
