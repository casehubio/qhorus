package io.casehub.qhorus.mesh.auth;

import io.casehub.platform.api.identity.SecurityIdentityAttributes;
import io.casehub.qhorus.api.spi.ApiKeyIdentityResolver;
import io.casehub.qhorus.api.spi.ResolvedIdentity;
import io.quarkus.security.identity.IdentityProviderManager;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.security.runtime.QuarkusSecurityIdentity;
import io.quarkus.vertx.http.runtime.security.ChallengeData;
import io.quarkus.vertx.http.runtime.security.HttpAuthenticationMechanism;
import io.smallrye.mutiny.Uni;
import io.vertx.ext.web.RoutingContext;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.security.Principal;

@ApplicationScoped
public class ApiKeyAuthenticationMechanism implements HttpAuthenticationMechanism {

    private final ApiKeyIdentityResolver resolver;

    @Inject
    public ApiKeyAuthenticationMechanism(ApiKeyIdentityResolver resolver) {
        this.resolver = resolver;
    }

    @Override
    public Uni<SecurityIdentity> authenticate(
            RoutingContext context,
            IdentityProviderManager identityProviderManager) {
        String key = context.request().getHeader("X-Api-Key");
        if (key == null || key.isBlank()) {
            return Uni.createFrom().nullItem();
        }
        return Uni.createFrom().item(() ->
            resolver.resolve(key).map(this::toSecurityIdentity).orElse(null));
    }

    @Override
    public int getPriority() {
        return 2000;
    }

    @Override
    public Uni<ChallengeData> getChallenge(RoutingContext context) {
        return Uni.createFrom().nullItem();
    }


    private SecurityIdentity toSecurityIdentity(ResolvedIdentity resolved) {
        return QuarkusSecurityIdentity.builder()
            .setPrincipal(new ApiKeyPrincipal(resolved.actorId()))
            .addRoles(resolved.roles())
            .addAttribute(SecurityIdentityAttributes.TENANCY_ID, resolved.tenancyId())
            .build();
    }

    private record ApiKeyPrincipal(String name) implements Principal {
        @Override public String getName() { return name; }
    }
}
