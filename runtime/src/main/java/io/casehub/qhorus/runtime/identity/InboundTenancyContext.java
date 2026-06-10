package io.casehub.qhorus.runtime.identity;

import jakarta.enterprise.context.RequestScoped;

import io.casehub.platform.api.identity.TenancyConstants;

/**
 * Request-scoped holder for the tenancyId resolved from the inbound HTTP request.
 *
 * <p>Populated by {@link TenancyContextFilter} from the {@code X-Tenancy-ID} header.
 * Read by {@link QhorusInboundCurrentPrincipal} to answer {@code CurrentPrincipal.tenancyId()}.
 *
 * <p>Refs qhorus#265.
 */
@RequestScoped
public class InboundTenancyContext {

    private String tenancyId = TenancyConstants.DEFAULT_TENANT_ID;

    public String tenancyId() {
        return tenancyId;
    }

    /** Sets the tenant for this request. Null or blank falls back to {@link TenancyConstants#DEFAULT_TENANT_ID}. */
    public void set(final String t) {
        tenancyId = (t != null && !t.isBlank()) ? t : TenancyConstants.DEFAULT_TENANT_ID;
    }
}
