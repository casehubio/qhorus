package io.casehub.qhorus.compliance.posture;

import io.casehub.qhorus.api.spi.compliance.CompliancePosture;
import io.casehub.qhorus.api.spi.compliance.CompliancePostureProvider;
import io.quarkus.arc.DefaultBean;

import jakarta.enterprise.context.ApplicationScoped;
import java.time.Instant;

@DefaultBean
@ApplicationScoped
public class NoOpCompliancePostureProvider implements CompliancePostureProvider {

    @Override
    public CompliancePosture getPosture(String tenancyId, Instant from, Instant to) {
        return CompliancePosture.EMPTY;
    }
}
