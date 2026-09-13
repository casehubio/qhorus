package io.casehub.qhorus.runtime.message;

import io.casehub.ledger.runtime.service.TrustGateService;
import io.casehub.qhorus.api.spi.ObligorTrustContext;
import io.casehub.qhorus.api.spi.ObligorTrustPolicy;

public class DefaultObligorTrustPolicy implements ObligorTrustPolicy {

    private final double minObligorTrust;
    private final TrustGateService trustGateService;

    public DefaultObligorTrustPolicy(double minObligorTrust, TrustGateService trustGateService) {
        this.minObligorTrust = minObligorTrust;
        this.trustGateService = trustGateService;
    }

    @Override
    public boolean permits(ObligorTrustContext ctx) {
        if (minObligorTrust <= 0.0) {
            return true;
        }
        return trustGateService.meetsThreshold(ctx.obligorId(), minObligorTrust);
    }
}
