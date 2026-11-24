package io.casehub.qhorus.runtime.identity;

import io.casehub.qhorus.api.spi.ApiKeyIdentityResolver;
import io.quarkus.arc.ClientProxy;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
@TestProfile(PlatformApiKeyProfile.class)
class PlatformApiKeyIdentityResolverCdiTest {

    @Inject
    ApiKeyIdentityResolver resolver;

    @Test
    void platformResolverDisplacesConfigResolver() {
        assertThat(ClientProxy.unwrap(resolver))
                .isInstanceOf(PlatformApiKeyIdentityResolver.class);
    }

    @Test
    void resolveReturnsEmptyForUnknownKey() {
        assertThat(resolver.resolve("nonexistent-key")).isEmpty();
    }
}
