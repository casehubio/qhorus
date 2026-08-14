package io.casehub.qhorus.runtime.ledger;

import io.casehub.ledger.api.spi.AttestorCredibilityPolicy;
import io.casehub.ledger.api.spi.LedgerEntryRepository;
import io.casehub.qhorus.runtime.config.QhorusConfig;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;

@ApplicationScoped
public class CollusionAwareCredibilityProducer {

    @Inject
    QhorusConfig config;

    @Inject
    AgreementCredibilityPolicy base;

    @Inject
    LedgerEntryRepository ledger;

    @Produces
    @ApplicationScoped
    AttestorCredibilityPolicy policy() {
        if (config.attestation().collusionDetectionEnabled()) {
            return new CollusionAwareCredibilityPolicy(
                    base, ledger, config.attestation().collusionThreshold());
        }
        return base;
    }
}
