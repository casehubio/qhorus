package io.casehub.qhorus.runtime.identity;

import java.util.Set;

import jakarta.enterprise.context.ApplicationScoped;

import io.casehub.platform.api.identity.CurrentPrincipal;
import io.casehub.platform.api.identity.TenancyConstants;
import io.casehub.qhorus.runtime.qualifier.QhorusSystem;

/**
 * Qhorus-internal system-actor CurrentPrincipal. Always isCrossTenantAdmin().
 *
 * <p>Not @DefaultBean — never replaces MockCurrentPrincipal. Accessed only
 * via @QhorusSystem qualifier from CrossTenantProducer.
 *
 * <p>Interim: delete when casehub-platform ships a platform-level system-actor
 * principal with isCrossTenantAdmin()=true. Update CrossTenantProducer to inject
 * the platform implementation instead.
 */
@ApplicationScoped
@QhorusSystem
public class QhorusSystemCurrentPrincipal implements CurrentPrincipal {

    @Override
    public String actorId() {
        return "system:qhorus";
    }

    @Override
    public Set<String> groups() {
        return Set.of();
    }

    @Override
    public String tenancyId() {
        return TenancyConstants.DEFAULT_TENANT_ID;
    }

    @Override
    public boolean isCrossTenantAdmin() {
        return true;
    }
}
