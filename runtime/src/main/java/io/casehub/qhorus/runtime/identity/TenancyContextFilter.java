package io.casehub.qhorus.runtime.identity;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.PreMatching;
import jakarta.ws.rs.ext.Provider;

/**
 * JAX-RS pre-matching filter that reads the {@code X-Tenancy-ID} request header
 * and stores it in {@link InboundTenancyContext} for the duration of the request.
 *
 * <p>{@code @PreMatching} ensures the tenant is populated before resource-method dispatch,
 * so all code that runs during request processing (including JAX-RS resource methods,
 * CDI beans injected into them, and any service layer called from them) sees the
 * correct tenant.
 *
 * <p>Falls back to {@link io.casehub.platform.api.identity.TenancyConstants#DEFAULT_TENANT_ID}
 * when the header is absent or blank.
 *
 * <p>Refs qhorus#265.
 */
@Provider
@PreMatching
@Priority(100)
@ApplicationScoped
public class TenancyContextFilter implements ContainerRequestFilter {

    @Inject
    InboundTenancyContext ctx;

    @Override
    public void filter(final ContainerRequestContext requestContext) {
        ctx.set(requestContext.getHeaderString("X-Tenancy-ID"));
    }
}
