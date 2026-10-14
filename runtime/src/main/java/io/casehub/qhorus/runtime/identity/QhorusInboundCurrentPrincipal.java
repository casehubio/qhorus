package io.casehub.qhorus.runtime.identity;

import io.casehub.platform.api.identity.CurrentPrincipal;
import io.casehub.platform.api.identity.TenancyConstants;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.ContextNotActiveException;
import jakarta.inject.Inject;

import java.util.Set;

/**
 * Qhorus default {@link CurrentPrincipal} for HTTP requests without OIDC authentication.
 *
 * <p>Reads {@code tenancyId} from {@link InboundTenancyContext}, which is populated by
 * {@link TenancyContextFilter} from the {@code X-Tenancy-ID} request header.
 *
 * <p><strong>CDI resolution:</strong> plain {@code @ApplicationScoped}. Displaces
 * {@code MockCurrentPrincipal @DefaultBean} from {@code casehub-platform} automatically
 * (a normal bean beats a {@code @DefaultBean}). Displaced by any {@code @Alternative}
 * bean with {@code @Priority}: {@code FixedCurrentPrincipal @Priority(1)} in test
 * fixtures, or {@code OidcCurrentPrincipal @Priority(100)} in OIDC deployments.
 * Refs qhorus#265, qhorus#490.
 *
 * <p><strong>Scope:</strong> {@code @ApplicationScoped} (not {@code @RequestScoped}).
 * The bean itself is stateless — per-request isolation comes from the
 * {@code @RequestScoped InboundTenancyContext} it delegates to. Using
 * {@code @ApplicationScoped} here ensures that when background threads (Scheduled,
 * ObservesAsync) call per-tenant stores, the CDI proxy resolves and the
 * {@link ContextNotActiveException} from {@link InboundTenancyContext#tenancyId()}
 * is caught at the call site, returning {@link TenancyConstants#DEFAULT_TENANT_ID}
 * safely. With {@code @RequestScoped}, the proxy itself would throw before entering
 * the method body and the catch would be unreachable.
 *
 * <p><strong>Security boundary:</strong> {@code X-Tenancy-ID} is not authenticated.
 * Any caller can claim any tenant. For isolated multi-tenant deployments,
 * use {@code casehub-platform-oidc}.
 *
 * <p><strong>Behavioural delta from MockCurrentPrincipal:</strong>
 * {@code actorId()} returns {@code "anonymous"}, making {@code isAuthenticated()}
 * return {@code false}. The mock defaults to {@code "system"} (configurable).
 * No qhorus-internal code currently gates on {@code isAuthenticated()}.
 *
 * <p>Refs qhorus#265, qhorus#276, qhorus#490.
 */
@ApplicationScoped
public class QhorusInboundCurrentPrincipal implements CurrentPrincipal {

    @Inject
    InboundTenancyContext ctx;

    @Override
    public String actorId() {
        return "anonymous";
    }

    @Override
    public Set<String> groups() {
        return Set.of();
    }

    @Override
    public String tenancyId() {
        try {
            return ctx.tenancyId();
        } catch (final ContextNotActiveException e) {
            return TenancyConstants.DEFAULT_TENANT_ID;
        }
    }

    @Override
    public boolean isCrossTenantAdmin() {
        return false;
    }
}
