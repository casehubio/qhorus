package io.casehub.qhorus.signing;

import io.casehub.ledger.api.spi.TrustScoreSource;
import io.casehub.qhorus.api.store.ExternalAgentBindingStore;
import io.casehub.qhorus.signing.core.IdentityVerificationTrustDecoratorCore;
import jakarta.annotation.Priority;
import jakarta.decorator.Decorator;
import jakarta.decorator.Delegate;
import jakarta.enterprise.inject.Any;
import jakarta.inject.Inject;

import java.util.Map;
import java.util.OptionalDouble;

@Decorator
@Priority(1000)
public class IdentityVerificationTrustDecorator implements TrustScoreSource {

    @Inject @Delegate @Any
    TrustScoreSource delegate;

    @Inject
    ExternalAgentBindingStore bindingStore;

    private double floorScore = 0.6;
    private double weight = 0.15;

    @Inject
    void configure(SigningConfig config) {
        this.floorScore = config.trust().verifiedFloorScore();
        this.weight = config.trust().dimensionWeight();
    }

    private IdentityVerificationTrustDecoratorCore core() {
        return new IdentityVerificationTrustDecoratorCore(delegate, bindingStore, floorScore, weight);
    }

    @Override
    public OptionalDouble globalScore(String actorId) {
        return core().globalScore(actorId);
    }

    @Override
    public OptionalDouble capabilityScore(String actorId, String capabilityTag) {
        return core().capabilityScore(actorId, capabilityTag);
    }

    @Override
    public OptionalDouble dimensionScore(String actorId, String dimensionKey) {
        return core().dimensionScore(actorId, dimensionKey);
    }

    @Override
    public OptionalDouble capabilityDimensionScore(String actorId, String capabilityTag, String dimensionKey) {
        return core().capabilityDimensionScore(actorId, capabilityTag, dimensionKey);
    }

    @Override
    public int decisionCount(String actorId, String capabilityTag) {
        return core().decisionCount(actorId, capabilityTag);
    }

    @Override
    public Map<String, Double> allCapabilityScores(String actorId) {
        return core().allCapabilityScores(actorId);
    }

    @Override
    public Map<String, Double> allDimensionScores(String actorId) {
        return core().allDimensionScores(actorId);
    }

    @Override
    public Map<String, Double> qualityScores(String actorId, String capabilityTag) {
        return core().qualityScores(actorId, capabilityTag);
    }
}
