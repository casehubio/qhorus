package io.casehub.qhorus.runtime.identity;

import io.casehub.qhorus.api.spi.ApiKeyIdentityResolver;
import io.quarkus.arc.ClientProxy;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
class PlatformApiKeyDisabledTest {

    @Inject
    ApiKeyIdentityResolver resolver;

    @Test
    void configResolverIsDefaultWhenPlatformPropertyAbsent() {
        assertThat(ClientProxy.unwrap(resolver))
                .isInstanceOf(ConfigApiKeyIdentityResolver.class);
    }
}
