package io.casehub.qhorus.cluster;

import io.quarkus.arc.properties.IfBuildProperty;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.PreMatching;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;

@Provider
@PreMatching
@Priority(50)
@ApplicationScoped
@IfBuildProperty(name = "casehub.qhorus.relay.enabled", stringValue = "true",
                 enableIfMissing = false)
public class InternalSecretFilter implements ContainerRequestFilter {

    @Inject
    RelayConfig config;

    @Override
    public void filter(ContainerRequestContext ctx) {
        String path = ctx.getUriInfo().getPath();
        if (!path.startsWith("internal/") && !path.startsWith("/internal/")) {
            return;
        }
        var secret = config.internalSecret();
        if (secret.isEmpty()) {
            return;
        }
        String header = ctx.getHeaderString("X-Internal-Secret");
        if (!secret.get().equals(header)) {
            ctx.abortWith(Response.status(Response.Status.UNAUTHORIZED)
                    .entity("Invalid or missing X-Internal-Secret header")
                    .build());
        }
    }
}
