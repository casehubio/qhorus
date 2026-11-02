package io.casehub.qhorus.mesh.auth;

import io.casehub.qhorus.api.spi.ApiKeyIdentityResolver;
import io.casehub.qhorus.api.spi.ResolvedIdentity;
import io.quarkus.security.identity.SecurityIdentity;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.ext.web.RoutingContext;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ApiKeyAuthenticationMechanismTest {

    private final ApiKeyIdentityResolver resolver = mock(ApiKeyIdentityResolver.class);
    private final ApiKeyAuthenticationMechanism mechanism = new ApiKeyAuthenticationMechanism(resolver);

    @Test
    void authenticate_with_valid_key_returns_identity() {
        var ctx = mockContext("sk-valid");
        when(resolver.resolve("sk-valid")).thenReturn(
            Optional.of(new ResolvedIdentity("agent-alpha", "tenant-1", Set.of("agent"))));

        var identity = mechanism.authenticate(ctx, null).await().indefinitely();
        assertThat(identity).isNotNull();
        assertThat(identity.getPrincipal().getName()).isEqualTo("agent-alpha");
        assertThat(identity.getRoles()).containsExactly("agent");
        assertThat((String) identity.getAttribute("tenancyId")).isEqualTo("tenant-1");
    }

    @Test
    void authenticate_without_header_returns_null() {
        var ctx = mockContext(null);
        var identity = mechanism.authenticate(ctx, null).await().indefinitely();
        assertThat(identity).isNull();
    }

    @Test
    void authenticate_with_blank_header_returns_null() {
        var ctx = mockContext("  ");
        var identity = mechanism.authenticate(ctx, null).await().indefinitely();
        assertThat(identity).isNull();
    }

    @Test
    void authenticate_with_unknown_key_returns_null() {
        var ctx = mockContext("sk-bad");
        when(resolver.resolve("sk-bad")).thenReturn(Optional.empty());
        var identity = mechanism.authenticate(ctx, null).await().indefinitely();
        assertThat(identity).isNull();
    }

    @Test
    void priority_is_lower_than_oidc_default() {
        assertThat(mechanism.getPriority()).isGreaterThan(1000);
    }

    private RoutingContext mockContext(String apiKeyValue) {
        var ctx = mock(RoutingContext.class);
        var request = mock(HttpServerRequest.class);
        when(ctx.request()).thenReturn(request);
        when(request.getHeader("X-Api-Key")).thenReturn(apiKeyValue);
        return ctx;
    }
}
