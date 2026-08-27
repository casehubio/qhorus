package io.casehub.qhorus.api.spi.compliance;

import java.time.Instant;

public interface CompliancePostureProvider {

    CompliancePosture getPosture(String tenancyId, Instant from, Instant to);
}
